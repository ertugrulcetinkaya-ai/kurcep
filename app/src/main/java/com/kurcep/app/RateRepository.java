package com.kurcep.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RateRepository {
    interface Callback {
        void onSuccess(RateData data);
        void onError(Exception error);
    }

    static void fetch(Callback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                JSONObject fx = getJson("https://open.er-api.com/v6/latest/USD");
                JSONObject rates = fx.getJSONObject("rates");
                double usdTry = rates.getDouble("TRY");
                double eurPerUsd = rates.getDouble("EUR");

                JSONObject gold = getJson("https://api.gold-api.com/price/XAU");
                double goldUsdPerOunce = gold.getDouble("price");
                double eurTry = usdTry / eurPerUsd;
                double gramGoldTry = goldUsdPerOunce * usdTry / 31.1034768;
                double[] dailyChanges = fetchDailyChanges(usdTry, eurTry, goldUsdPerOunce,
                        gramGoldTry);
                RateData result = new RateData(usdTry, eurTry, gramGoldTry,
                        dailyChanges[0], dailyChanges[1], dailyChanges[2], System.currentTimeMillis());
                main.post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                main.post(() -> callback.onError(error));
            } finally {
                executor.shutdown();
            }
        });
    }

    private static double[] fetchDailyChanges(double usdTry, double eurTry,
                                              double goldUsdPerOunce, double gramGoldTry) {
        try {
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(yesterday.getTime());
            JSONObject historical = getJson("https://api.frankfurter.app/" + date
                    + "?from=USD&to=TRY,EUR");
            JSONObject rates = historical.getJSONObject("rates");
            double previousUsdTry = rates.getDouble("TRY");
            double previousEurTry = previousUsdTry / rates.getDouble("EUR");
            double previousGoldUsdPerOunce = fetchPreviousGoldClose();
            double previousGoldTry = previousGoldUsdPerOunce * previousUsdTry / 31.1034768;
            return new double[]{
                    percentChange(usdTry, previousUsdTry),
                    percentChange(eurTry, previousEurTry),
                    percentChange(gramGoldTry, previousGoldTry)
            };
        } catch (Exception ignored) {
            return new double[]{Double.NaN, Double.NaN, Double.NaN};
        }
    }

    private static double fetchPreviousGoldClose() throws Exception {
        JSONObject history = getJson("https://xaus.com/api/v1/history");
        JSONArray points = history.getJSONArray("points");
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        String yesterdayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(yesterday.getTime());
        double fallback = Double.NaN;
        for (int i = points.length() - 1; i >= 0; i--) {
            JSONObject point = points.getJSONObject(i);
            String date = point.getString("d");
            if (date.compareTo(yesterdayDate) <= 0) {
                return point.getDouble("c");
            }
            fallback = point.getDouble("c");
        }
        if (!Double.isNaN(fallback)) return fallback;
        throw new IllegalStateException("Altın geçmiş verisi yok");
    }

    private static double percentChange(double current, double previous) {
        return previous == 0 ? Double.NaN : ((current - previous) / previous) * 100;
    }

    private static JSONObject getJson(String address) throws Exception {
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
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Sunucu hatası: " + status);
            }
            return new JSONObject(body.toString());
        } finally {
            connection.disconnect();
        }
    }

    private RateRepository() {}
}
