package com.kurcep.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

final class RateStore {
    private static final String PREFS = "rates";
    private static final String REFRESH_SEQUENCE = "refresh_sequence";
    private static final String REFRESH_ID = "refresh_id";
    private static final Object LOCK = new Object();

    static long nextRefreshId(Context context) {
        return nextRefreshId(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    static long nextRefreshId(SharedPreferences prefs) {
        synchronized (LOCK) {
            long previous = prefs.getLong(REFRESH_SEQUENCE, 0L);
            long now = System.currentTimeMillis();
            long next = previous == Long.MAX_VALUE
                    ? Long.MAX_VALUE : Math.max(now, previous + 1);
            if (!prefs.edit().putLong(REFRESH_SEQUENCE, next).commit()) {
                throw new IllegalStateException("Refresh sırası kaydedilemedi");
            }
            return next;
        }
    }

    static boolean save(Context context, RateSnapshot data) {
        return saveIfNewer(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), data);
    }

    static boolean save(SharedPreferences prefs, RateSnapshot data) {
        return saveIfNewer(prefs, data);
    }

    static boolean saveIfNewer(SharedPreferences prefs, RateSnapshot data) {
        synchronized (LOCK) {
            if (prefs.contains(REFRESH_ID)
                    && data.refreshId <= prefs.getLong(REFRESH_ID, 0L)) {
                return false;
            }

            SharedPreferences.Editor editor = prefs.edit();
            if (prefs.contains("updated") && !sameDay(prefs.getLong("updated", 0), data.updatedAt)) {
                editor.putLong("previous_usd", prefs.getLong("usd", 0));
                editor.putLong("previous_eur", prefs.getLong("eur", 0));
                editor.putLong("previous_gold", prefs.getLong("gold", 0));
                editor.putLong("previous_brent", prefs.getLong("brent", 0));
                editor.putLong("previous_updated", prefs.getLong("updated", 0));
            }
            editor
                    .putLong("usd", Double.doubleToRawLongBits(data.usdTry.value))
                    .putLong("eur", Double.doubleToRawLongBits(data.eurTry.value))
                    .putLong("gold", Double.doubleToRawLongBits(data.gramGoldTry.value))
                    .putLong("brent", Double.doubleToRawLongBits(data.brentUsd.value))
                    .putLong(REFRESH_ID, data.refreshId)
                    .putLong("updated", data.updatedAt);
            writeDailyChange(editor, "daily_usd", data.usdTry.dailyChange);
            writeDailyChange(editor, "daily_eur", data.eurTry.dailyChange);
            writeDailyChange(editor, "daily_gold", data.gramGoldTry.dailyChange);
            writeDailyChange(editor, "daily_brent", data.brentUsd.dailyChange);
            editor.apply();
            return true;
        }
    }

    static RateSnapshot load(Context context) {
        return load(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    static RateSnapshot load(SharedPreferences prefs) {
        if (!prefs.contains("updated")) return null;
        double usd = Double.longBitsToDouble(prefs.getLong("usd", 0));
        double eur = Double.longBitsToDouble(prefs.getLong("eur", 0));
        double gold = Double.longBitsToDouble(prefs.getLong("gold", 0));
        double brent = prefs.contains("brent")
                ? Double.longBitsToDouble(prefs.getLong("brent", 0)) : Double.NaN;
        double dailyUsd = readDailyChange(prefs, "daily_usd");
        double dailyEur = readDailyChange(prefs, "daily_eur");
        double dailyGold = readDailyChange(prefs, "daily_gold");
        double dailyBrent = readDailyChange(prefs, "daily_brent");
        long updatedAt = prefs.getLong("updated", 0);
        long refreshId = prefs.getLong(REFRESH_ID, updatedAt);
        return new RateSnapshot(
                new RateQuote(usd, dailyUsd),
                new RateQuote(eur, dailyEur),
                new RateQuote(gold, dailyGold),
                new RateQuote(brent, dailyBrent),
                updatedAt,
                refreshId);
    }

    private static void writeDailyChange(SharedPreferences.Editor editor, String key, double value) {
        if (Double.isNaN(value)) {
            editor.remove(key);
        } else {
            editor.putLong(key, Double.doubleToRawLongBits(value));
        }
    }

    private static double readDailyChange(SharedPreferences prefs, String key) {
        return prefs.contains(key)
                ? Double.longBitsToDouble(prefs.getLong(key, 0)) : Double.NaN;
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
