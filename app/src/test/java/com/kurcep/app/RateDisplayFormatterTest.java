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

    @Test
    public void dataTimeShowsDateOnlyWhenNotToday() {
        long now = System.currentTimeMillis();

        assertTrue(RateDisplayFormatter.dataTime(now, now).matches("\\d{2}:\\d{2}"));
        String old = RateDisplayFormatter.dataTime(now - 3L * 24 * 60 * 60 * 1000, now);
        assertTrue(old, old.matches("\\d{1,2} \\S+ \\d{2}:\\d{2}"));
    }

    @Test
    public void formatsBuySellOnlyWhenBothAreAvailable() {
        assertEquals("Alış ₺50,3864 · Satış ₺50,3983", RateDisplayFormatter.buySell(
                Formatters.precise(), new RateQuote(50.3983, 0.05, 50.3864, 50.3983)));
        assertEquals(null, RateDisplayFormatter.buySell(
                Formatters.precise(), new RateQuote(50.39, 0.05)));
    }

    @Test
    public void namesEachSource() {
        assertEquals("Truncgil Finans",
                RateDisplayFormatter.sourceName(RateSnapshot.Source.TRUNCGIL));
        assertEquals("ExchangeRate-API",
                RateDisplayFormatter.sourceName(RateSnapshot.Source.FALLBACK));
    }
}
