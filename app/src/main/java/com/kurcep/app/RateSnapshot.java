package com.kurcep.app;

/** Immutable refresh result grouped by instrument instead of positional fields. */
final class RateSnapshot {
    /** Which provider path produced the currency and gold quotes. */
    enum Source {
        /** finans.truncgil.com Turkish market feed (intraday). */
        TRUNCGIL,
        /** ExchangeRate-API + Gold API + Frankfurter/XAUS (daily FX). */
        FALLBACK
    }

    final RateQuote usdTry;
    final RateQuote eurTry;
    final RateQuote gramGoldTry;
    final RateQuote brentUsd;
    final long refreshId;
    /** When the app fetched this snapshot. */
    final long updatedAt;
    /** The provider's own data timestamp; 0 when unknown (e.g. legacy cache). */
    final long dataAt;
    final Source source;

    RateSnapshot(RateQuote usdTry, RateQuote eurTry, RateQuote gramGoldTry,
                 RateQuote brentUsd, long updatedAt, long refreshId) {
        this(usdTry, eurTry, gramGoldTry, brentUsd, updatedAt, refreshId, 0L, Source.FALLBACK);
    }

    RateSnapshot(RateQuote usdTry, RateQuote eurTry, RateQuote gramGoldTry,
                 RateQuote brentUsd, long updatedAt, long refreshId, long dataAt,
                 Source source) {
        this.usdTry = usdTry;
        this.eurTry = eurTry;
        this.gramGoldTry = gramGoldTry;
        this.brentUsd = brentUsd;
        this.updatedAt = updatedAt;
        this.refreshId = refreshId;
        this.dataAt = dataAt;
        this.source = source == null ? Source.FALLBACK : source;
    }

    /** The time the displayed currency data refers to: provider time when known. */
    long displayTime() {
        return dataAt > 0 ? dataAt : updatedAt;
    }
}
