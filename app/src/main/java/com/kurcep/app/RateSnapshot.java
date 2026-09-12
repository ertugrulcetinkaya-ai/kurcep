package com.kurcep.app;

/** Immutable refresh result grouped by instrument instead of positional fields. */
final class RateSnapshot {
    final RateQuote usdTry;
    final RateQuote eurTry;
    final RateQuote gramGoldTry;
    final RateQuote brentUsd;
    final long refreshId;
    final long updatedAt;

    RateSnapshot(RateQuote usdTry, RateQuote eurTry, RateQuote gramGoldTry,
                 RateQuote brentUsd, long updatedAt, long refreshId) {
        this.usdTry = usdTry;
        this.eurTry = eurTry;
        this.gramGoldTry = gramGoldTry;
        this.brentUsd = brentUsd;
        this.updatedAt = updatedAt;
        this.refreshId = refreshId;
    }
}
