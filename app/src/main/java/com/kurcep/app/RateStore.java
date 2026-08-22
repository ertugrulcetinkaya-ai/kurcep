package com.kurcep.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

final class RateStore {
    private static final String PREFS = "rates";

    static void save(Context context, RateData data) {
        save(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), data);
    }

    static void save(SharedPreferences prefs, RateData data) {
        SharedPreferences.Editor editor = prefs.edit();
        if (prefs.contains("updated") && !sameDay(prefs.getLong("updated", 0), data.updatedAt)) {
            editor.putLong("previous_usd", prefs.getLong("usd", 0));
            editor.putLong("previous_eur", prefs.getLong("eur", 0));
            editor.putLong("previous_gold", prefs.getLong("gold", 0));
            editor.putLong("previous_brent", prefs.getLong("brent", 0));
            editor.putLong("previous_updated", prefs.getLong("updated", 0));
        }
        editor
                .putLong("usd", Double.doubleToRawLongBits(data.usdTry))
                .putLong("eur", Double.doubleToRawLongBits(data.eurTry))
                .putLong("gold", Double.doubleToRawLongBits(data.gramGoldTry))
                .putLong("brent", Double.doubleToRawLongBits(data.brentUsd))
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
        if (!Double.isNaN(data.brentDailyChange)) {
            prefs.edit()
                    .putLong("daily_brent", Double.doubleToRawLongBits(data.brentDailyChange))
                    .apply();
        }
    }

    static RateData load(Context context) {
        return load(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    static RateData load(SharedPreferences prefs) {
        if (!prefs.contains("updated")) return null;
        double usd = Double.longBitsToDouble(prefs.getLong("usd", 0));
        double eur = Double.longBitsToDouble(prefs.getLong("eur", 0));
        double gold = Double.longBitsToDouble(prefs.getLong("gold", 0));
        double brent = prefs.contains("brent")
                ? Double.longBitsToDouble(prefs.getLong("brent", 0)) : Double.NaN;
        double previousUsd = prefs.contains("previous_updated")
                ? Double.longBitsToDouble(prefs.getLong("previous_usd", 0)) : Double.NaN;
        double previousEur = prefs.contains("previous_updated")
                ? Double.longBitsToDouble(prefs.getLong("previous_eur", 0)) : Double.NaN;
        double previousGold = prefs.contains("previous_updated")
                ? Double.longBitsToDouble(prefs.getLong("previous_gold", 0)) : Double.NaN;
        double previousBrent = prefs.contains("previous_updated")
                ? Double.longBitsToDouble(prefs.getLong("previous_brent", 0)) : Double.NaN;
        double dailyUsd = prefs.contains("daily_usd")
                ? Double.longBitsToDouble(prefs.getLong("daily_usd", 0))
                : RateMath.percentChange(usd, previousUsd);
        double dailyEur = prefs.contains("daily_eur")
                ? Double.longBitsToDouble(prefs.getLong("daily_eur", 0))
                : RateMath.percentChange(eur, previousEur);
        double dailyGold = prefs.contains("daily_gold")
                ? Double.longBitsToDouble(prefs.getLong("daily_gold", 0))
                : RateMath.percentChange(gold, previousGold);
        double dailyBrent = prefs.contains("daily_brent")
                ? Double.longBitsToDouble(prefs.getLong("daily_brent", 0))
                : RateMath.percentChange(brent, previousBrent);
        return new RateData(usd, eur, gold, dailyUsd, dailyEur, dailyGold,
                brent, dailyBrent, prefs.getLong("updated", 0));
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
