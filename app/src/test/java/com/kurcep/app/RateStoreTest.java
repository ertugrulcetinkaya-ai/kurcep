package com.kurcep.app;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RateStoreTest {
    @Test
    public void savesAndLoadsRatesAndDailyChanges() {
        SharedPreferences prefs = inMemoryPreferences();
        RateData input = new RateData(40.25, 43.75, 2500.50,
                1.25, -0.75, 2.5, 78.42, 0.85, System.currentTimeMillis());

        RateStore.save(prefs, input);
        RateData output = RateStore.load(prefs);

        assertNotNull(output);
        assertEquals(input.usdTry, output.usdTry, 0.0001);
        assertEquals(input.eurTry, output.eurTry, 0.0001);
        assertEquals(input.gramGoldTry, output.gramGoldTry, 0.0001);
        assertEquals(input.brentUsd, output.brentUsd, 0.0001);
        assertEquals(input.usdDailyChange, output.usdDailyChange, 0.0001);
        assertEquals(input.eurDailyChange, output.eurDailyChange, 0.0001);
        assertEquals(input.goldDailyChange, output.goldDailyChange, 0.0001);
        assertEquals(input.brentDailyChange, output.brentDailyChange, 0.0001);
    }

    @Test
    public void derivesChangeFromPreviousDayWhenNoApiChangeExists() {
        SharedPreferences prefs = inMemoryPreferences();
        long previousDay = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L;
        RateStore.save(prefs, new RateData(100, 200, 300,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, previousDay));
        RateStore.save(prefs, new RateData(110, 180, 330,
                Double.NaN, Double.NaN, Double.NaN, 75.0, Double.NaN, System.currentTimeMillis()));

        RateData output = RateStore.load(prefs);
        assertEquals(10.0, output.usdDailyChange, 0.0001);
        assertEquals(-10.0, output.eurDailyChange, 0.0001);
        assertEquals(10.0, output.goldDailyChange, 0.0001);
    }

    private static SharedPreferences inMemoryPreferences() {
        Map<String, Object> values = new HashMap<>();
        final SharedPreferences[] holder = new SharedPreferences[1];
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                RateStoreTest.class.getClassLoader(),
                new Class[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("put") && args != null && args.length == 2) {
                        values.put((String) args[0], args[1]);
                        return proxy;
                    }
                    if (name.equals("remove")) {
                        values.remove(args[0]);
                        return proxy;
                    }
                    if (name.equals("clear")) {
                        values.clear();
                        return proxy;
                    }
                    if (name.equals("commit")) return true;
                    return null;
                });
        holder[0] = (SharedPreferences) Proxy.newProxyInstance(
                RateStoreTest.class.getClassLoader(),
                new Class[]{SharedPreferences.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("contains")) return values.containsKey(args[0]);
                    if (name.equals("getLong")) {
                        Object value = values.get(args[0]);
                        return value == null ? args[1] : value;
                    }
                    if (name.equals("edit")) return editor;
                    if (name.equals("getAll")) return new HashMap<>(values);
                    return null;
                });
        return holder[0];
    }
}
