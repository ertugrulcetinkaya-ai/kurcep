package com.kurcep.app;

/** A single instrument value, its optional daily percentage change and optional buy/sell. */
final class RateQuote {
    enum Status { AVAILABLE, UNAVAILABLE }

    final double value;
    final double dailyChange;
    /** Market buy/sell quotes; NaN when the provider only reports a single (mid) price. */
    final double buying;
    final double selling;
    final Status status;

    RateQuote(double value, double dailyChange) {
        this(value, dailyChange, Double.NaN, Double.NaN);
    }

    RateQuote(double value, double dailyChange, double buying, double selling) {
        this.value = value;
        this.dailyChange = dailyChange;
        this.buying = buying;
        this.selling = selling;
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

    boolean hasBuySell() {
        return hasFinitePositiveValue(buying) && hasFinitePositiveValue(selling);
    }

    private static boolean hasFinitePositiveValue(double value) {
        return Double.isFinite(value) && value > 0;
    }
}
