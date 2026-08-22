package com.kurcep.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class RateRepository {
    private static final String TAG = "RateRepository";
    static final String FX_URL = "https://open.er-api.com/v6/latest/USD";
    static final String GOLD_URL = "https://api.gold-api.com/price/XAU";
    static final String GOLD_HISTORY_URL = "https://xaus.com/api/v1/history";
    static final double GRAMS_PER_TROY_OUNCE = 31.1034768;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // Package-private so unit tests can replace it with a fake implementation.
    static RateTransport transport = new HttpRateTransport();

    interface Callback {
        void onSuccess(RateData data);
        void onError(Exception error);
    }

    static void fetch(Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                RateData result = loadRates();
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                Log.e(TAG, "Güncel kurlar alınamadı", error);
                MAIN.post(() -> callback.onError(asException(error)));
            }
        });
    }

    // Package-private so unit tests can exercise the full computation with a fake transport.
    static RateData loadRates() throws Exception {
        // These requests are independent. Starting them together keeps a slow source
        // from serially extending the fetch time.
        Future<JSONObject> fxFuture = EXECUTOR.submit(() -> getJson(FX_URL));
        Future<JSONObject> goldFuture = EXECUTOR.submit(() -> getJson(GOLD_URL));

        JSONObject rates = fxFuture.get().getJSONObject("rates");
        double usdTry = rates.getDouble("TRY");
        double eurTry = usdTry / rates.getDouble("EUR");
        double goldUsdPerOunce = goldFuture.get().getDouble("price");
        double gramGoldTry = goldUsdPerOunce * usdTry / GRAMS_PER_TROY_OUNCE;

        // Historical sources are optional and independent. A failure in one does not
        // hide a valid daily change from another source.
        Future<double[]> currencyChange = EXECUTOR.submit(
                () -> fetchCurrencyDailyChange(usdTry, eurTry));
        Future<Double> goldChange = EXECUTOR.submit(
                () -> fetchGoldDailyChange(gramGoldTry));

        double[] currencyChanges = currencyChange.get();
        double dailyGoldChange = goldChange.get();
        return new RateData(usdTry, eurTry, gramGoldTry,
                currencyChanges[0], currencyChanges[1], dailyGoldChange,
                System.currentTimeMillis());
    }

    private static double[] fetchCurrencyDailyChange(double usdTry, double eurTry) {
        try {
            String date = yesterday();
            JSONObject historical = getJson("https://api.frankfurter.app/" + date
                    + "?from=USD&to=TRY,EUR");
            JSONObject rates = historical.getJSONObject("rates");
            double previousUsdTry = rates.getDouble("TRY");
            double previousEurTry = previousUsdTry / rates.getDouble("EUR");
            return new double[]{
                    RateMath.percentChange(usdTry, previousUsdTry),
                    RateMath.percentChange(eurTry, previousEurTry)
            };
        } catch (Exception error) {
            Log.w(TAG, "Döviz geçmiş verisi alınamadı", error);
            return new double[]{Double.NaN, Double.NaN};
        }
    }

    private static double fetchGoldDailyChange(double gramGoldTry) {
        try {
            JSONObject fx = getJson("https://api.frankfurter.app/" + yesterday()
                    + "?from=USD&to=TRY");
            double previousUsdTry = fx.getJSONObject("rates").getDouble("TRY");
            double previousGoldUsdPerOunce = fetchPreviousGoldClose();
            double previousGoldTry = previousGoldUsdPerOunce * previousUsdTry
                    / GRAMS_PER_TROY_OUNCE;
            return RateMath.percentChange(gramGoldTry, previousGoldTry);
        } catch (Exception error) {
            Log.w(TAG, "Altın geçmiş verisi alınamadı", error);
            return Double.NaN;
        }
    }

    private static double fetchPreviousGoldClose() throws Exception {
        JSONObject history = getJson(GOLD_HISTORY_URL);
        JSONArray points = history.getJSONArray("points");
        String yesterday = yesterday();
        for (int i = points.length() - 1; i >= 0; i--) {
            JSONObject point = points.getJSONObject(i);
            if (point.getString("d").compareTo(yesterday) <= 0) {
                return point.getDouble("c");
            }
        }
        throw new IllegalStateException("Altın geçmiş verisi yok");
    }

    private static String yesterday() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    static JSONObject getJson(String address) throws Exception {
        RateTransport.Response response = transport.get(address);
        if (response.status < 200 || response.status >= 300) {
            throw new IllegalStateException("Sunucu hatası: " + response.status);
        }
        return new JSONObject(response.body);
    }

    /**
     * Production transport: the same HttpURLConnection behavior the repository
     * used before the transport seam was introduced.
     */
    private static final class HttpRateTransport implements RateTransport {
        @Override
        public RateTransport.Response get(String address) throws Exception {
            HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "KurCep-Android/1.0");
            try {
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                if (stream == null) throw new IllegalStateException("Sunucu yanıt vermedi");
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                return new RateTransport.Response(status, body.toString());
            } finally {
                connection.disconnect();
            }
        }
    }

    private static Exception asException(Exception error) {
        if (error instanceof ExecutionException && error.getCause() instanceof Exception) {
            return (Exception) error.getCause();
        }
        return error;
    }

    private RateRepository() {}
}
