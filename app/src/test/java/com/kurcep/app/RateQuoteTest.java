package com.kurcep.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RateQuoteTest {
    @Test
    public void exposesAvailabilitySeparatelyFromDailyChange() {
        RateQuote quote = new RateQuote(78.42, Double.NaN);

        assertEquals(RateQuote.Status.AVAILABLE, quote.status);
        assertTrue(quote.isAvailable());
        assertFalse(quote.hasDailyChange());
    }

    @Test
    public void representsOptionalSourceFailureExplicitly() {
        RateQuote quote = RateQuote.unavailable();

        assertEquals(RateQuote.Status.UNAVAILABLE, quote.status);
        assertFalse(quote.isAvailable());
        assertFalse(quote.hasDailyChange());
    }
}
