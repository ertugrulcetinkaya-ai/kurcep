package com.kurcep.app;

final class RateData {
    final double usdTry;
    final double eurTry;
    final double gramGoldTry;
    final double usdDailyChange;
    final double eurDailyChange;
    final double goldDailyChange;
    final long updatedAt;

    RateData(double usdTry, double eurTry, double gramGoldTry,
             double usdDailyChange, double eurDailyChange, double goldDailyChange,
             long updatedAt) {
        this.usdTry = usdTry;
        this.eurTry = eurTry;
        this.gramGoldTry = gramGoldTry;
        this.usdDailyChange = usdDailyChange;
        this.eurDailyChange = eurDailyChange;
        this.goldDailyChange = goldDailyChange;
        this.updatedAt = updatedAt;
    }
}
