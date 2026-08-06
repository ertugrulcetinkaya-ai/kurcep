package com.kurcep.app;

import java.text.NumberFormat;
import java.util.Locale;

final class Formatters {
    static NumberFormat currency() {
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format;
    }

    private Formatters() {}
}
