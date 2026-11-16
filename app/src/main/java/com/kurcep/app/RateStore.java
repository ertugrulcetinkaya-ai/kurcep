package com.kurcep.app;

import android.content.Context;
import android.content.SharedPreferences;

final class RateStore {
    private static final String PREFS = "rates";
    private static final String REFRESH_ID = "refresh_id";
    private static final Object LOCK = new Object();

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
            editor
                    .putLong("usd", Double.doubleToRawLongBits(data.usdTry.value))
                    .putLong("eur", Double.doubleToRawLongBits(data.eurTry.value))
                    .putLong("gold", Double.doubleToRawLongBits(data.gramGoldTry.value))
                    .putLong("brent", Double.doubleToRawLongBits(data.brentUsd.value))
                    .putLong(REFRESH_ID, data.refreshId)
                    .putLong("updated", data.updatedAt)
                    .putLong("data_at", data.dataAt)
                    .putString("source", data.source.name())
                    // Remove the legacy derived-snapshot cache during migration. Daily
                    // changes now come only from the current refresh result.
                    .remove("previous_usd")
                    .remove("previous_eur")
                    .remove("previous_gold")
                    .remove("previous_brent")
                    .remove("previous_updated");
            writeDailyChange(editor, "daily_usd", data.usdTry.dailyChange);
            writeDailyChange(editor, "daily_eur", data.eurTry.dailyChange);
            writeDailyChange(editor, "daily_gold", data.gramGoldTry.dailyChange);
            writeDailyChange(editor, "daily_brent", data.brentUsd.dailyChange);
            writeDailyChange(editor, "buy_usd", data.usdTry.buying);
            writeDailyChange(editor, "sell_usd", data.usdTry.selling);
            writeDailyChange(editor, "buy_eur", data.eurTry.buying);
            writeDailyChange(editor, "sell_eur", data.eurTry.selling);
            writeDailyChange(editor, "buy_gold", data.gramGoldTry.buying);
            writeDailyChange(editor, "sell_gold", data.gramGoldTry.selling);
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
        long dataAt = prefs.getLong("data_at", 0L);
        return new RateSnapshot(
                new RateQuote(usd, dailyUsd, readDailyChange(prefs, "buy_usd"),
                        readDailyChange(prefs, "sell_usd")),
                new RateQuote(eur, dailyEur, readDailyChange(prefs, "buy_eur"),
                        readDailyChange(prefs, "sell_eur")),
                new RateQuote(gold, dailyGold, readDailyChange(prefs, "buy_gold"),
                        readDailyChange(prefs, "sell_gold")),
                new RateQuote(brent, dailyBrent),
                updatedAt,
                refreshId,
                dataAt,
                readSource(prefs.getString("source", null)));
    }

    static RateSnapshot.Source readSource(String name) {
        if (name != null) {
            for (RateSnapshot.Source source : RateSnapshot.Source.values()) {
                if (source.name().equals(name)) return source;
            }
        }
        return RateSnapshot.Source.FALLBACK;
    }

    /** Stores an optional double; NaN removes the key so it reads back as NaN. */
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

    private RateStore() {}
}
