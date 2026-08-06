package com.kurcep.app;

final class RateMath {
    static double percentChange(double current, double previous) {
        return Double.isNaN(previous) || previous == 0
                ? Double.NaN : ((current - previous) / previous) * 100;
    }

    private RateMath() {}
}
