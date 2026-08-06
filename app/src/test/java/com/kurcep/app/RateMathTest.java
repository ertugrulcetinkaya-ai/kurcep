package com.kurcep.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RateMathTest {
    @Test
    public void calculatesPositiveAndNegativePercentChanges() {
        assertEquals(10.0, RateMath.percentChange(110.0, 100.0), 0.0001);
        assertEquals(-10.0, RateMath.percentChange(90.0, 100.0), 0.0001);
    }

    @Test
    public void returnsNanForMissingOrZeroBaseline() {
        assertTrue(Double.isNaN(RateMath.percentChange(110.0, Double.NaN)));
        assertTrue(Double.isNaN(RateMath.percentChange(110.0, 0.0)));
    }
}
