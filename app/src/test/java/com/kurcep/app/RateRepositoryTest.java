package com.kurcep.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class RateRepositoryTest {
    private final RateTransport originalTransport = RateRepository.transport;
    @After
    public void tearDown() {
        RateRepository.transport = originalTransport;
    }

    @Test
    public void getJsonParsesBodyOnSuccessStatus() throws Exception {
        useFakeTransport(address -> new RateTransport.Response(200,
                "{\"rates\":{\"TRY\":41.25,\"EUR\":1.10},\"base\":\"USD\"}"));

        JSONObject json = RateRepository.getJson(RateRepository.FX_URL);

        assertEquals("USD", json.getString("base"));
        assertEquals(41.25, json.getJSONObject("rates").getDouble("TRY"), 1e-9);
        assertEquals(1.10, json.getJSONObject("rates").getDouble("EUR"), 1e-9);
    }

    @Test
    public void getJsonThrowsForHttpErrorStatus() throws Exception {
        useFakeTransport(address -> new RateTransport.Response(503, "sunucu bakımda"));

        try {
            RateRepository.getJson(RateRepository.FX_URL);
            fail("Beklenen IllegalStateException fırlatılmadı");
        } catch (Exception error) {
            assertTrue("Beklenen IllegalStateException değildi: " + error,
                    error instanceof IllegalStateException);
            assertEquals("Sunucu hatası: 503", error.getMessage());
        }
    }

    @Test
    public void getJsonThrowsOnInvalidJsonBody() throws Exception {
        useFakeTransport(address -> new RateTransport.Response(200, "bu bir json degil"));

        try {
            RateRepository.getJson(RateRepository.FX_URL);
            fail("Beklenen JSONException fırlatılmadı");
        } catch (Exception error) {
            assertTrue("Beklenen JSONException değildi: " + error,
                    error instanceof JSONException);
        }
    }

    @Test
    public void loadRatesReturnsRatesAndDailyChangesOnSuccess() throws Exception {
        double usdTry = 41.25;
        double eurRate = 1.10;
        double eurTry = usdTry / eurRate;
        double goldUsdPerOunce = 2340.0;
        double gramGoldTry = goldUsdPerOunce * usdTry / RateRepository.GRAMS_PER_TROY_OUNCE;
        useFakeTransportForLoadRates(usdTry, eurRate, 2340.0);

        long before = System.currentTimeMillis();
        RateData data = RateRepository.loadRates();
        long after = System.currentTimeMillis();

        assertEquals(usdTry, data.usdTry, 1e-9);
        assertEquals(eurTry, data.eurTry, 1e-9);
        assertEquals(gramGoldTry, data.gramGoldTry, 1e-9);
        // Previous values are controlled by the fake: 40.80 USD/TRY, 2300 USD/oz gold.
        assertEquals(RateMath.percentChange(usdTry, 40.80), data.usdDailyChange, 1e-9);
        assertEquals(RateMath.percentChange(usdTry / eurRate, 40.80 / eurRate),
                data.eurDailyChange, 1e-9);
        assertEquals(RateMath.percentChange(
                gramGoldTry, 2300.0 * 40.80 / RateRepository.GRAMS_PER_TROY_OUNCE),
                data.goldDailyChange, 1e-9);
        assertTrue(data.updatedAt >= before && data.updatedAt <= after);
    }

    @Test
    public void loadRatesUsesNaNChangesWhenHistoricalSourcesFail() throws Exception {
        double usdTry = 41.25;
        double eurRate = 1.10;
        Map<String, RateTransport.Response> responses = new HashMap<>();
        responses.put(RateRepository.FX_URL, new RateTransport.Response(200,
                "{\"rates\":{\"TRY\":" + usdTry + ",\"EUR\":" + eurRate + "}}"));
        responses.put(RateRepository.GOLD_URL, new RateTransport.Response(200, "{\"price\":2340.0}"));
        // Historical endpoints all fail; they must not hide the valid rates above.
        useFakeTransport(address -> {
            RateTransport.Response response = responses.get(address);
            if (response != null) {
                return response;
            }
            if (address.startsWith("https://api.frankfurter.app/")) {
                return new RateTransport.Response(500, "dün veri alınamadı");
            }
            if (address.equals(RateRepository.GOLD_HISTORY_URL)) {
                return new RateTransport.Response(503, "geçmiş yanıt vermedi");
            }
            throw new IllegalStateException("Beklenmeyen adres: " + address);
        });

        RateData data = RateRepository.loadRates();

        assertEquals(usdTry, data.usdTry, 1e-9);
        assertTrue(Double.isNaN(data.usdDailyChange));
        assertTrue(Double.isNaN(data.eurDailyChange));
        assertTrue(Double.isNaN(data.goldDailyChange));
    }

    private interface FakeResponse {
        RateTransport.Response forAddress(String address) throws Exception;
    }

    private static void useFakeTransport(FakeResponse fake) {
        RateRepository.transport = new RateTransport() {
            @Override
            public RateTransport.Response get(String address) throws Exception {
                return fake.forAddress(address);
            }
        };
    }

    private static void useFakeTransportForLoadRates(double usdTry, double eurRate,
                                                     double goldUsdPerOunce) {
        String yesterday = yesterdayIso();
        Map<String, RateTransport.Response> responses = new HashMap<>();
        responses.put(RateRepository.FX_URL, new RateTransport.Response(200,
                "{\"rates\":{\"TRY\":" + usdTry + ",\"EUR\":" + eurRate + "}}"));
        responses.put(RateRepository.GOLD_URL, new RateTransport.Response(200,
                "{\"price\":" + goldUsdPerOunce + "}"));
        responses.put("https://api.frankfurter.app/" + yesterday + "?from=USD&to=TRY,EUR",
                new RateTransport.Response(200,
                        "{\"rates\":{\"TRY\":40.80,\"EUR\":" + eurRate + "}}"));
        responses.put("https://api.frankfurter.app/" + yesterday + "?from=USD&to=TRY",
                new RateTransport.Response(200, "{\"rates\":{\"TRY\":40.80}}"));
        responses.put(RateRepository.GOLD_HISTORY_URL, new RateTransport.Response(200,
                "{\"points\":["
                        + "{\"d\":\"" + yesterday + "\",\"c\":2300.0},"
                        + "{\"d\":\"" + todayIso() + "\",\"c\":2400.0}"
                        + "]}"));
        useFakeTransport(address -> {
            RateTransport.Response response = responses.get(address);
            if (response == null) {
                throw new IllegalStateException("Beklenmeyen adres: " + address);
            }
            return response;
        });
    }

    private static String yesterdayIso() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -1);
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(calendar.getTime());
    }

    private static String todayIso() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
    }

}
