package com.kurcep.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Converts a transport response into bounded JSON and classifies failures. */
final class RateJsonClient {
    static final int MAX_RESPONSE_BYTES = 512 * 1024;

    private final RateTransport transport;

    RateJsonClient(RateTransport transport) {
        this.transport = transport;
    }

    JSONObject getJson(String address) throws Exception {
        RateTransport.Response response;
        try {
            response = transport.get(address);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        } catch (java.io.IOException error) {
            throw new TransientRateException("Ağ isteği başarısız: " + address, error);
        } catch (RateException error) {
            throw error;
        } catch (Exception error) {
            throw new PermanentRateException("HTTP isteği başarısız: " + address, error);
        }
        if (response == null || response.body == null) {
            throw new PermanentRateException("Sunucu boş yanıt verdi: " + address);
        }
        if (response.status < 200 || response.status >= 300) {
            throw new RateHttpException(response.status, address);
        }
        try {
            return new JSONObject(response.body);
        } catch (JSONException error) {
            throw new InvalidRateException("Geçersiz JSON yanıtı: " + address, error);
        }
    }

    static String readLimitedBody(InputStream stream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8 * 1024];
        int total = 0;
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("HTTP yanıtı okunurken iptal edildi");
            }
            total += count;
            if (total > MAX_RESPONSE_BYTES) {
                throw new ResponseTooLargeException(MAX_RESPONSE_BYTES);
            }
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
