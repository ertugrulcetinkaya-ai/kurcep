package com.kurcep.app;

/** USD/TRY, EUR/TRY and gram gold from one provider path, with that data's own timestamp. */
final class MarketRates {
    final RateQuote usdTry;
    final RateQuote eurTry;
    final RateQuote gramGoldTry;
    /** Provider's data timestamp in epoch millis, or 0 when the provider does not report it. */
    final long dataAt;
    final RateSnapshot.Source source;

    MarketRates(RateQuote usdTry, RateQuote eurTry, RateQuote gramGoldTry, long dataAt,
                RateSnapshot.Source source) {
        this.usdTry = usdTry;
        this.eurTry = eurTry;
        this.gramGoldTry = gramGoldTry;
        this.dataAt = dataAt;
        this.source = source;
    }
}
