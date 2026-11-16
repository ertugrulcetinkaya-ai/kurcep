package com.kurcep.app;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Shared, platform-independent display formatting for the Activity and widget. */
final class RateDisplayFormatter {
    static String change(NumberFormat format, RateQuote quote) {
        if (!quote.hasDailyChange()) {
            return "—";
        }
        return change(format, quote.dailyChange);
    }

    static String change(NumberFormat format, double value) {
        if (!Double.isFinite(value)) {
            return "—";
        }
        return (value >= 0 ? "↑ " : "↓ ") + format.format(Math.abs(value)) + "%";
    }

    static String time(long updatedAt) {
        return new SimpleDateFormat("HH:mm", new Locale("tr", "TR"))
                .format(new Date(updatedAt));
    }

    /** "HH:mm" for today, otherwise "d MMM HH:mm" so stale data is visibly stale. */
    static String dataTime(long dataAt, long now) {
        java.util.TimeZone zone = java.util.TimeZone.getDefault();
        Locale tr = new Locale("tr", "TR");
        SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.US);
        day.setTimeZone(zone);
        boolean sameDay = day.format(new Date(dataAt)).equals(day.format(new Date(now)));
        SimpleDateFormat format = new SimpleDateFormat(sameDay ? "HH:mm" : "d MMM HH:mm", tr);
        format.setTimeZone(zone);
        return format.format(new Date(dataAt));
    }

    static String sourceName(RateSnapshot.Source source) {
        return source == RateSnapshot.Source.TRUNCGIL ? "Truncgil Finans" : "ExchangeRate-API";
    }

    /** "Alış ₺50,3864 · Satış ₺50,3983", or null when the quote has no buy/sell pair. */
    static String buySell(NumberFormat format, RateQuote quote) {
        if (!quote.hasBuySell()) {
            return null;
        }
        return "Alış ₺" + format.format(quote.buying) + " · Satış ₺" + format.format(quote.selling);
    }

    private RateDisplayFormatter() {}
}
