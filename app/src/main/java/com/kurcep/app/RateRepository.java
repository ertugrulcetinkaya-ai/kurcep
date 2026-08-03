package com.kurcep.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
                RateData result = new RateData(usdTry, eurTry, gramGoldTry, System.currentTimeMillis());
                main.post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                main.post(() -> callback.onError(error));
            } finally {
                executor.shutdown();
            }
        });
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
