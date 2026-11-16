package com.kurcep.app;

import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Turkish market feed (finans.truncgil.com): intraday USD/TRY, EUR/TRY and gram gold with
 * buy/sell quotes and a daily percentage change in a single request.
 *
 * <p>This is an unofficial, unauthenticated feed without an SLA, so parsing is defensive and any
 * failure (HTTP, schema, stale data) makes the repository fall back to the global sources.
 */
final class TruncgilDataSource {
    static final String URL = "https://finans.truncgil.com/v4/today.json";
    /** Older data means the feed has stalled; the global sources are a safer fallback. */
    static final long MAX_AGE_MS = TimeUnit.DAYS.toMillis(3);
    private static final long MAX_CLOCK_SKEW_MS = TimeUnit.HOURS.toMillis(1);
    /** A larger single-day move in these instruments indicates a broken field. */
    private static final double MAX_ABS_DAILY_CHANGE = 50.0;
    private static final TimeZone ISTANBUL = TimeZone.getTimeZone("Europe/Istanbul");

    private final RateJsonClient client;

    TruncgilDataSource(RateJsonClient client) {
        this.client = client;
    }

    JSONObject current() throws Exception {
        return client.getJson(URL);
    }

    static MarketRates parse(JSONObject json, long now) throws InvalidRateException {
        long dataAt = parseUpdateDate(json.optString("Update_Date", ""));
        if (now - dataAt > MAX_AGE_MS) {
            throw new InvalidRateException("Truncgil verisi çok eski: " + json.opt("Update_Date"));
        }
        if (dataAt - now > MAX_CLOCK_SKEW_MS) {
            throw new InvalidRateException("Truncgil veri saati gelecekte: "
                    + json.opt("Update_Date"));
        }
        return new MarketRates(
                quote(json, "USD"),
                quote(json, "EUR"),
                quote(json, "GRA"),
                dataAt,
                RateSnapshot.Source.TRUNCGIL);
    }

    static long parseUpdateDate(String value) throws InvalidRateException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        format.setTimeZone(ISTANBUL);
        format.setLenient(false);
        try {
            return format.parse(value.trim()).getTime();
        } catch (ParseException | RuntimeException error) {
            throw new InvalidRateException("Geçersiz Truncgil tarihi: " + value, error);
        }
    }

    private static RateQuote quote(JSONObject json, String key) throws InvalidRateException {
        JSONObject item = RateJsonValues.requiredObject(json, key);
        double buying = number(item, "Buying");
        double selling = number(item, "Selling");
        if (!RateJsonValues.isPositiveFinite(buying)) buying = Double.NaN;
        if (!RateJsonValues.isPositiveFinite(selling)) selling = Double.NaN;
        if (Double.isFinite(buying) && Double.isFinite(selling) && buying > selling) {
            throw new InvalidRateException("Truncgil alış satıştan büyük: " + key);
        }
        // Retail apps headline the selling price (what a buyer pays).
        double value = Double.isFinite(selling) ? selling : buying;
        RateJsonValues.requirePositive(value, key);
        double change = number(item, "Change");
        if (!Double.isFinite(change) || Math.abs(change) > MAX_ABS_DAILY_CHANGE) {
            change = Double.NaN;
        }
        return new RateQuote(value, change, buying, selling);
    }

    /** Accepts JSON numbers and numeric strings, including Turkish "6.274,91" / "%0,05". */
    static double number(JSONObject item, String key) {
        Object raw = item.opt(key);
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        if (!(raw instanceof String)) {
            return Double.NaN;
        }
        String text = ((String) raw).replace("%", "").replace(" ", "").trim();
        if (text.indexOf(',') >= 0) {
            text = text.replace(".", "").replace(',', '.');
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException error) {
            return Double.NaN;
        }
    }
}
