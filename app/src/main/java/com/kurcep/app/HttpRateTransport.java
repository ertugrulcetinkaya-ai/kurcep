package com.kurcep.app;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Production HTTPS transport with timeouts, cancellation checks, and bounded responses. */
final class HttpRateTransport implements RateTransport {
    @Override
    public RateTransport.Response get(String address) throws Exception {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("HTTP isteği iptal edildi");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "KurCep-Android/1.0");
        try {
            int status = connection.getResponseCode();
            try (InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream()) {
                if (stream == null) {
                    throw new PermanentRateException("Sunucu yanıt vermedi");
                }
                return new RateTransport.Response(status, RateJsonClient.readLimitedBody(stream));
            }
        } finally {
            connection.disconnect();
        }
    }
}
