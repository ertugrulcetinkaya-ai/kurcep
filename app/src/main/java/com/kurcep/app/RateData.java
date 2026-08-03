package com.kurcep.app;

final class RateData {
    final double usdTry;
    final double eurTry;
    final double gramGoldTry;
    final long updatedAt;

    RateData(double usdTry, double eurTry, double gramGoldTry, long updatedAt) {
        this.usdTry = usdTry;
        this.eurTry = eurTry;
        this.gramGoldTry = gramGoldTry;
        this.updatedAt = updatedAt;
    }
}
