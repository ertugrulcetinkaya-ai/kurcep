package com.kurcep.app;

import android.content.Context;
import android.content.SharedPreferences;

final class RateStore {
    private static final String PREFS = "rates";

    static void save(Context context, RateData data) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("usd", Double.doubleToRawLongBits(data.usdTry))
                .putLong("eur", Double.doubleToRawLongBits(data.eurTry))
                .putLong("gold", Double.doubleToRawLongBits(data.gramGoldTry))
                .putLong("updated", data.updatedAt)
                .apply();
    }

    static RateData load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains("updated")) return null;
        return new RateData(
                Double.longBitsToDouble(prefs.getLong("usd", 0)),
                Double.longBitsToDouble(prefs.getLong("eur", 0)),
                Double.longBitsToDouble(prefs.getLong("gold", 0)),
                prefs.getLong("updated", 0));
    }

    private RateStore() {}
}
