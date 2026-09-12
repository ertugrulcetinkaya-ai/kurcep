package com.kurcep.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class RateRepositoryTest {
    private RateRepository repository;

    @After
    public void tearDown() {
        if (repository != null) {
            repository.close();
        }
    }

    @Test
    public void getJsonParsesBodyOnSuccessStatus() throws Exception {
        useFakeTransport(address -> new RateTransport.Response(200,
                "{\"rates\":{\"TRY\":41.25,\"EUR\":1.10},\"base\":\"USD\"}"));

        JSONObject json = repository.getJson(RateRepository.FX_URL);

        assertEquals("USD", json.getString("base"));
        assertEquals(41.25, json.getJSONObject("rates").getDouble("TRY"), 1e-9);
        assertEquals(1.10, json.getJSONObject("rates").getDouble("EUR"), 1e-9);
    }

    @Test
    public void getJsonThrowsForHttpErrorStatus() throws Exception {
        useFakeTransport(address -> new RateTransport.Response(503, "sunucu bakımda"));

        try {
            repository.getJson(RateRepository.FX_URL);
            fail("Beklenen RateHttpException fırlatılmadı");
        } catch (Exception error) {
            assertTrue("Beklenen RateHttpException değildi: " + error,
                    error instanceof RateHttpException);
            assertEquals(503, ((RateHttpException) error).status);
            assertTrue(((RateHttpException) error).isRetryable());
        }
    }

    @Test
    public void classifiesClientHttpErrorsAsNonRetryable() throws Exception {
        useFakeTransport(address -> new RateTransport.Response(401, "yetkisiz"));

        try {
            repository.getJson(RateRepository.FX_URL);
            fail("Beklenen RateHttpException fırlatılmadı");
        } catch (RateHttpException error) {
            assertEquals(401, error.status);
            assertFalse(error.isRetryable());
        }
    }

    @Test
    public void classifiesNetworkIoAsTransient() throws Exception {
        useFakeTransport(address -> {
            throw new IOException("timeout");
        });

        try {
            repository.getJson(RateRepository.FX_URL);
            fail("Beklenen TransientRateException fırlatılmadı");
        } catch (TransientRateException error) {
            assertTrue(error.getCause() instanceof IOException);
        }
    }

    @Test
    public void getJsonThrowsOnInvalidJsonBody() throws Exception {
        useFakeTransport(address -> new RateTransport.Response(200, "bu bir json degil"));

        try {
            repository.getJson(RateRepository.FX_URL);
            fail("Beklenen InvalidRateException fırlatılmadı");
        } catch (Exception error) {
            assertTrue("Beklenen InvalidRateException değildi: " + error,
                    error instanceof InvalidRateException);
        }
    }

    @Test
    public void rejectsAnOversizedResponseBody() throws Exception {
        byte[] body = new byte[RateRepository.MAX_RESPONSE_BYTES + 1];
        Arrays.fill(body, (byte) 'x');

        try {
            RateRepository.readLimitedBody(new ByteArrayInputStream(body));
            fail("Beklenen ResponseTooLargeException fırlatılmadı");
        } catch (ResponseTooLargeException error) {
            assertTrue(error.getMessage().contains(
                    String.valueOf(RateRepository.MAX_RESPONSE_BYTES)));
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
        RateSnapshot data = repository.loadRates(42L);
        long after = System.currentTimeMillis();

        assertEquals(usdTry, data.usdTry.value, 1e-9);
        assertEquals(eurTry, data.eurTry.value, 1e-9);
        assertEquals(gramGoldTry, data.gramGoldTry.value, 1e-9);
        // Previous values are controlled by the fake: 40.80 USD/TRY, 2300 USD/oz gold.
        assertEquals(RateMath.percentChange(usdTry, 40.80), data.usdTry.dailyChange, 1e-9);
        assertEquals(RateMath.percentChange(usdTry / eurRate, 40.80 / eurRate),
                data.eurTry.dailyChange, 1e-9);
        assertEquals(RateMath.percentChange(
                gramGoldTry, 2300.0 * 40.80 / RateRepository.GRAMS_PER_TROY_OUNCE),
                data.gramGoldTry.dailyChange, 1e-9);
        // Brent: current price from meta, change against the last completed daily close.
        assertEquals(78.42, data.brentUsd.value, 1e-9);
        assertEquals(RateMath.percentChange(78.42, 78.12), data.brentUsd.dailyChange, 1e-9);
        assertTrue(data.updatedAt >= before && data.updatedAt <= after);
        assertEquals(42L, data.refreshId);
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

        RateSnapshot data = repository.loadRates(43L);

        assertEquals(usdTry, data.usdTry.value, 1e-9);
        assertTrue(Double.isNaN(data.usdTry.dailyChange));
        assertTrue(Double.isNaN(data.eurTry.dailyChange));
        assertTrue(Double.isNaN(data.gramGoldTry.dailyChange));
        // Brent source is not stubbed and fails; it degrades to NaN without failing the fetch.
        assertTrue(Double.isNaN(data.brentUsd.value));
        assertTrue(Double.isNaN(data.brentUsd.dailyChange));
    }

    @Test
    public void loadRatesMarksDependentChangesMissingWhenSharedFxHistoryFails() throws Exception {
        double usdTry = 41.25;
        double eurRate = 1.10;
        String yesterday = yesterdayIso();
        Map<String, RateTransport.Response> responses = new HashMap<>();
        responses.put(RateRepository.FX_URL, new RateTransport.Response(200,
                "{\"rates\":{\"TRY\":" + usdTry + ",\"EUR\":" + eurRate + "}}"));
        responses.put(RateRepository.GOLD_URL, new RateTransport.Response(200, "{\"price\":2340.0}"));
        // The shared previous-FX source fails...
        responses.put("https://api.frankfurter.app/" + yesterday + "?from=USD&to=TRY,EUR",
                new RateTransport.Response(500, "dün döviz verisi alınamadı"));
        // ...but the gold history source succeeds. It still cannot produce a
        // percentage without the shared previous FX baseline.
        responses.put(RateRepository.GOLD_HISTORY_URL, new RateTransport.Response(200,
                "{\"points\":[{\"d\":\"" + yesterday + "\",\"c\":2300.0}]}"));
        useFakeTransport(address -> {
            RateTransport.Response response = responses.get(address);
            if (response == null) {
                throw new IllegalStateException("Beklenmeyen adres: " + address);
            }
            return response;
        });

        RateSnapshot data = repository.loadRates(44L);

        assertEquals(usdTry, data.usdTry.value, 1e-9);
        // Both derived values depend on the shared previous FX baseline.
        assertTrue(Double.isNaN(data.usdTry.dailyChange));
        assertTrue(Double.isNaN(data.eurTry.dailyChange));
        assertTrue(Double.isNaN(data.gramGoldTry.dailyChange));
    }

    @Test
    public void loadRatesWalksBackwardToValidCloseWhenPreviousCloseIsNull() throws Exception {
        double usdTry = 41.25;
        double eurRate = 1.10;
        String yesterday = yesterdayIso();
        Map<String, RateTransport.Response> responses = new HashMap<>();
        responses.put(RateRepository.FX_URL, new RateTransport.Response(200,
                "{\"rates\":{\"TRY\":" + usdTry + ",\"EUR\":" + eurRate + "}}"));
        responses.put(RateRepository.GOLD_URL, new RateTransport.Response(200, "{\"price\":2340.0}"));
        responses.put("https://api.frankfurter.app/" + yesterday + "?from=USD&to=TRY,EUR",
                new RateTransport.Response(200,
                        "{\"rates\":{\"TRY\":40.80,\"EUR\":" + eurRate + "}}"));
        responses.put(RateRepository.GOLD_HISTORY_URL, new RateTransport.Response(200,
                "{\"points\":[{\"d\":\"" + yesterday + "\",\"c\":2300.0}]}"));
        // The last completed daily close (len-2) and the bars before it are null;
        // the nearest earlier valid close (77.99) must be used as the baseline.
        responses.put(RateRepository.BRENT_URL, new RateTransport.Response(200,
                "{\"chart\":{\"result\":[{\"meta\":{\"regularMarketPrice\":78.42},"
                        + "\"indicators\":{\"quote\":[{\"close\":["
                        + "77.99,null,null,null,78.42]}]}}],\"error\":null}}"));
        useFakeTransport(address -> {
            RateTransport.Response response = responses.get(address);
            if (response == null) {
                throw new IllegalStateException("Beklenmeyen adres: " + address);
            }
            return response;
        });

        RateSnapshot data = repository.loadRates(45L);

        assertEquals(78.42, data.brentUsd.value, 1e-9);
        assertEquals(RateMath.percentChange(78.42, 77.99), data.brentUsd.dailyChange, 1e-9);
    }

    @Test
    public void loadRatesCallsEachEndpointExactlyOnce() throws Exception {
        double usdTry = 41.25;
        double eurRate = 1.10;
        String yesterday = yesterdayIso();
        Map<String, RateTransport.Response> responses = new HashMap<>();
        responses.put(RateRepository.FX_URL, new RateTransport.Response(200,
                "{\"rates\":{\"TRY\":" + usdTry + ",\"EUR\":" + eurRate + "}}"));
        responses.put(RateRepository.GOLD_URL, new RateTransport.Response(200,
                "{\"price\":2340.0}"));
        responses.put("https://api.frankfurter.app/" + yesterday + "?from=USD&to=TRY,EUR",
                new RateTransport.Response(200,
                        "{\"rates\":{\"TRY\":40.80,\"EUR\":1.10}}"));
        responses.put(RateRepository.GOLD_HISTORY_URL, new RateTransport.Response(200,
                "{\"points\":[{\"d\":\"" + yesterday + "\",\"c\":2300.0}]}"));
        responses.put(RateRepository.BRENT_URL, new RateTransport.Response(200,
                "{\"chart\":{\"result\":[{\"meta\":{\"regularMarketPrice\":78.42},"
                        + "\"indicators\":{\"quote\":[{\"close\":[77.99,78.42]}]}}]}}"));
        Map<String, Integer> calls = new ConcurrentHashMap<>();
        useFakeTransport(address -> {
            calls.merge(address, 1, Integer::sum);
            RateTransport.Response response = responses.get(address);
            if (response == null) {
                throw new IllegalStateException("Beklenmeyen adres: " + address);
            }
            return response;
        });

        repository.loadRates(46L);

        assertEquals(5, calls.size());
        for (Integer count : calls.values()) {
            assertEquals(Integer.valueOf(1), count);
        }
    }

    @Test
    public void rejectsInvalidCurrentFxValue() throws Exception {
        useFakeTransport(address -> {
            if (address.equals(RateRepository.FX_URL)) {
                return new RateTransport.Response(200,
                        "{\"rates\":{\"TRY\":0,\"EUR\":1.10}}");
            }
            throw new IllegalStateException("Beklenmeyen adres: " + address);
        });

        try {
            repository.loadRates(47L);
            fail("Beklenen InvalidRateException fırlatılmadı");
        } catch (InvalidRateException error) {
            assertTrue(error.getMessage().contains("TRY"));
        }
    }

    @Test
    public void interruptsLoadAndCancelsChildRequests() throws Exception {
        CountDownLatch requestStarted = new CountDownLatch(1);
        AtomicBoolean callerInterrupted = new AtomicBoolean();
        useFakeTransport(address -> {
            requestStarted.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException error) {
                throw error;
            }
            throw new AssertionError("İptal edilen istek tamamlanmamalı");
        });

        Thread caller = new Thread(() -> {
            try {
                repository.loadRates(48L);
                fail("İptal edilen refresh başarılı olmamalı");
            } catch (InterruptedException error) {
                callerInterrupted.set(true);
            } catch (Exception error) {
                fail("Beklenmeyen hata: " + error);
            }
        });
        caller.start();

        assertTrue(requestStarted.await(2, java.util.concurrent.TimeUnit.SECONDS));
        caller.interrupt();
        caller.join(2_000);

        assertTrue("Refresh çağrısı interrupt'ı görmedi", callerInterrupted.get());
        assertTrue("Refresh thread'i sonlanmadı", !caller.isAlive());
    }

    private interface FakeResponse {
        RateTransport.Response forAddress(String address) throws Exception;
    }

    private void useFakeTransport(FakeResponse fake) {
        repository = new RateRepository(new RateTransport() {
            @Override
            public RateTransport.Response get(String address) throws Exception {
                return fake.forAddress(address);
            }
        });
    }

    private void useFakeTransportForLoadRates(double usdTry, double eurRate,
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
        responses.put(RateRepository.GOLD_HISTORY_URL, new RateTransport.Response(200,
                "{\"points\":["
                        + "{\"d\":\"" + yesterday + "\",\"c\":2300.0},"
                        + "{\"d\":\"" + todayIso() + "\",\"c\":2400.0}"
                        + "]}"));
        responses.put(RateRepository.BRENT_URL, new RateTransport.Response(200,
                "{\"chart\":{\"result\":[{\"meta\":{\"regularMarketPrice\":78.42},"
                        + "\"indicators\":{\"quote\":[{\"close\":["
                        + "76.10,77.05,77.99,78.12,78.42]}]}}],\"error\":null}}"));
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
