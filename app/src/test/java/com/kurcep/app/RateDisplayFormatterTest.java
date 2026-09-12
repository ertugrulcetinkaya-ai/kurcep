package com.kurcep.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.NumberFormat;

import org.junit.Test;

public class RateDisplayFormatterTest {
    @Test
    public void formatsPositiveNegativeAndMissingChangesConsistently() {
        NumberFormat format = Formatters.currency();

        assertEquals("↑ 1,25%", RateDisplayFormatter.change(format, 1.25));
        assertEquals("↓ 1,25%", RateDisplayFormatter.change(format, -1.25));
        assertEquals("—", RateDisplayFormatter.change(format, Double.NaN));
    }

    @Test
    public void formatsUpdatedTimeAsHourAndMinute() {
        String time = RateDisplayFormatter.time(0L);

        assertTrue(time.matches("\\d{2}:\\d{2}"));
    }
}
