package com.kurcep.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TruncgilDataSourceTest {
    static String istanbulTime(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Europe/Istanbul"));
        return format.format(new Date(millis));
    }

    static String body(String updateDate) {
        return "{\"Update_Date\":\"" + updateDate + "\","
                + "\"USD\":{\"Buying\":50.3864,\"Type\":\"Currency\",\"Selling\":50.3983,\"Change\":0.05},"
                + "\"EUR\":{\"Buying\":55.5268,\"Type\":\"Currency\",\"Selling\":55.5319,\"Change\":-0.05},"
                + "\"GRA\":{\"Selling\":6275.68,\"Type\":\"Gold\",\"Name\":\"GRAMALTIN\","
                + "\"Change\":-0.08,\"Buying\":6274.91},"
                + "\"BRENT\":{\"Type\":\"Gold\",\"Name\":\"BRENT\",\"Selling\":0,\"Change\":-2.69}}";
    }

    @Test
    public void parsesQuotesBuySellChangeAndIstanbulTimestamp() throws Exception {
        long now = System.currentTimeMillis();
        long dataAt = (now - 5 * 60_000L) / 1000L * 1000L;

        MarketRates rates = TruncgilDataSource.parse(new JSONObject(body(istanbulTime(dataAt))), now);

        assertEquals(RateSnapshot.Source.TRUNCGIL, rates.source);
        assertEquals(dataAt, rates.dataAt);
        assertEquals(50.3983, rates.usdTry.value, 1e-9);
        assertEquals(50.3864, rates.usdTry.buying, 1e-9);
        assertEquals(50.3983, rates.usdTry.selling, 1e-9);
        assertEquals(0.05, rates.usdTry.dailyChange, 1e-9);
        assertEquals(55.5319, rates.eurTry.value, 1e-9);
        assertEquals(-0.05, rates.eurTry.dailyChange, 1e-9);
        assertEquals(6275.68, rates.gramGoldTry.value, 1e-9);
        assertEquals(6274.91, rates.gramGoldTry.buying, 1e-9);
        assertEquals(-0.08, rates.gramGoldTry.dailyChange, 1e-9);
    }

    @Test
    public void interpretsUpdateDateAsTurkeyTime() throws Exception {
        // Turkey is UTC+3 all year: 2026-11-16 18:17:01 TRT == 15:17:01 UTC.
        assertEquals(1_794_842_221_000L, TruncgilDataSource.parseUpdateDate("2026-11-16 18:17:01"));
    }

    @Test
    public void acceptsTurkishFormattedStringsAndMissingFields() throws Exception {
        long now = System.currentTimeMillis();
        String json = "{\"Update_Date\":\"" + istanbulTime(now) + "\","
                + "\"USD\":{\"Buying\":\"50,3864\",\"Selling\":\"50,3983\",\"Change\":\"%0,05\"},"
                + "\"EUR\":{\"Selling\":55.53},"
                + "\"GRA\":{\"Buying\":\"6.274,91\",\"Selling\":\"6.275,68\",\"Change\":\"abc\"}}";

        MarketRates rates = TruncgilDataSource.parse(new JSONObject(json), now);

        assertEquals(50.3983, rates.usdTry.value, 1e-9);
        assertEquals(0.05, rates.usdTry.dailyChange, 1e-9);
        assertEquals(55.53, rates.eurTry.value, 1e-9);
        assertTrue(!rates.eurTry.hasBuySell());
        assertTrue(Double.isNaN(rates.eurTry.dailyChange));
        assertEquals(6275.68, rates.gramGoldTry.value, 1e-9);
        assertTrue(Double.isNaN(rates.gramGoldTry.dailyChange));
    }

    @Test
    public void dropsImplausibleDailyChange() throws Exception {
        long now = System.currentTimeMillis();
        String json = body(istanbulTime(now)).replace("\"Change\":0.05", "\"Change\":503.9");

        MarketRates rates = TruncgilDataSource.parse(new JSONObject(json), now);

        assertTrue(Double.isNaN(rates.usdTry.dailyChange));
    }

    @Test
    public void rejectsStaleFutureOrMalformedTimestamps() throws Exception {
        long now = System.currentTimeMillis();
        assertInvalid(body(istanbulTime(now - TruncgilDataSource.MAX_AGE_MS - 60_000L)), now);
        assertInvalid(body(istanbulTime(now + 2 * 60 * 60_000L)), now);
        assertInvalid(body("dün akşam"), now);
        assertInvalid(body(istanbulTime(now)).replace("\"Update_Date\"", "\"Tarih\""), now);
    }

    @Test
    public void rejectsMissingOrInvalidInstruments() throws Exception {
        long now = System.currentTimeMillis();
        String fresh = body(istanbulTime(now));
        assertInvalid(fresh.replace("\"GRA\"", "\"GRAX\""), now);
        assertInvalid(fresh.replace("\"Buying\":50.3864", "\"Buying\":0")
                .replace("\"Selling\":50.3983", "\"Selling\":0"), now);
        // Buying above selling means the fields are swapped or corrupted.
        assertInvalid(fresh.replace("\"Buying\":50.3864", "\"Buying\":60"), now);
    }

    private static void assertInvalid(String json, long now) throws Exception {
        try {
            TruncgilDataSource.parse(new JSONObject(json), now);
            fail("Beklenen InvalidRateException fırlatılmadı: " + json);
        } catch (InvalidRateException expected) {
            // expected
        }
    }
}
