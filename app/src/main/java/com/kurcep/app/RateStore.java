package com.kurcep.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

final class RateStore {
    private static final String PREFS = "rates";

    static void save(Context context, RateData data) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (prefs.contains("updated") && !sameDay(prefs.getLong("updated", 0), data.updatedAt)) {
            editor.putLong("previous_usd", prefs.getLong("usd", 0));
            editor.putLong("previous_eur", prefs.getLong("eur", 0));
            editor.putLong("previous_gold", prefs.getLong("gold", 0));
            editor.putLong("previous_updated", prefs.getLong("updated", 0));
        }
        editor
                .putLong("usd", Double.doubleToRawLongBits(data.usdTry))
                .putLong("eur", Double.doubleToRawLongBits(data.eurTry))
                .putLong("gold", Double.doubleToRawLongBits(data.gramGoldTry))
                .putLong("updated", data.updatedAt)
                .apply();
        if (!Double.isNaN(data.usdDailyChange) || !Double.isNaN(data.eurDailyChange)
                || !Double.isNaN(data.goldDailyChange)) {
            prefs.edit()
                    .putLong("daily_usd", Double.doubleToRawLongBits(data.usdDailyChange))
                    .putLong("daily_eur", Double.doubleToRawLongBits(data.eurDailyChange))
                    .putLong("daily_gold", Double.doubleToRawLongBits(data.goldDailyChange))
                    .apply();
        }
    }

    static RateData load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains("updated")) return null;
        double usd = Double.longBitsToDouble(prefs.getLong("usd", 0));
        double eur = Double.longBitsToDouble(prefs.getLong("eur", 0));
        double gold = Double.longBitsToDouble(prefs.getLong("gold", 0));
        double previousUsd = prefs.contains("previous_updated")
                ? Double.longBitsToDouble(prefs.getLong("previous_usd", 0)) : Double.NaN;
        double previousEur = prefs.contains("previous_updated")
                ? Double.longBitsToDouble(prefs.getLong("previous_eur", 0)) : Double.NaN;
        double previousGold = prefs.contains("previous_updated")
                ? Double.longBitsToDouble(prefs.getLong("previous_gold", 0)) : Double.NaN;
        double dailyUsd = prefs.contains("daily_usd")
                ? Double.longBitsToDouble(prefs.getLong("daily_usd", 0))
                : percentChange(usd, previousUsd);
        double dailyEur = prefs.contains("daily_eur")
                ? Double.longBitsToDouble(prefs.getLong("daily_eur", 0))
                : percentChange(eur, previousEur);
        double dailyGold = prefs.contains("daily_gold")
                ? Double.longBitsToDouble(prefs.getLong("daily_gold", 0))
                : percentChange(gold, previousGold);
        return new RateData(usd, eur, gold, dailyUsd, dailyEur, dailyGold,
                prefs.getLong("updated", 0));
    }

    private static double percentChange(double current, double previous) {
        return Double.isNaN(previous) || previous == 0 ? Double.NaN
                : ((current - previous) / previous) * 100;
    }

    private static boolean sameDay(long first, long second) {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        a.setTimeInMillis(first);
        b.setTimeInMillis(second);
        return a.get(Calendar.ERA) == b.get(Calendar.ERA)
                && a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private RateStore() {}
}
