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

    private RateDisplayFormatter() {}
}
