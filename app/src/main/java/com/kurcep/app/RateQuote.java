package com.kurcep.app;

/** A single instrument value and its optional daily percentage change. */
final class RateQuote {
    enum Status { AVAILABLE, UNAVAILABLE }

    final double value;
    final double dailyChange;
    final Status status;

    RateQuote(double value, double dailyChange) {
        this.value = value;
        this.dailyChange = dailyChange;
        this.status = hasFinitePositiveValue(value) ? Status.AVAILABLE : Status.UNAVAILABLE;
    }

    static RateQuote unavailable() {
        return new RateQuote(Double.NaN, Double.NaN);
    }

    boolean isAvailable() {
        return status == Status.AVAILABLE;
    }

    boolean hasDailyChange() {
        return Double.isFinite(dailyChange);
    }

    private static boolean hasFinitePositiveValue(double value) {
        return Double.isFinite(value) && value > 0;
    }
}
