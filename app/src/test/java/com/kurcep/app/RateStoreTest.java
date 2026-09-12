package com.kurcep.app;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RateStoreTest {
    @Test
    public void allocatesMonotonicRefreshIds() {
        SharedPreferences prefs = inMemoryPreferences();

        long first = RateStore.nextRefreshId(prefs);
        long second = RateStore.nextRefreshId(prefs);

        assertTrue(second > first);
    }

    @Test
    public void savesAndLoadsRatesAndDailyChanges() {
        SharedPreferences prefs = inMemoryPreferences();
        RateSnapshot input = snapshot(40.25, 43.75, 2500.50,
                1.25, -0.75, 2.5, 78.42, 0.85, System.currentTimeMillis());

        RateStore.save(prefs, input);
        RateSnapshot output = RateStore.load(prefs);

        assertNotNull(output);
        assertEquals(input.usdTry.value, output.usdTry.value, 0.0001);
        assertEquals(input.eurTry.value, output.eurTry.value, 0.0001);
        assertEquals(input.gramGoldTry.value, output.gramGoldTry.value, 0.0001);
        assertEquals(input.brentUsd.value, output.brentUsd.value, 0.0001);
        assertEquals(input.usdTry.dailyChange, output.usdTry.dailyChange, 0.0001);
        assertEquals(input.eurTry.dailyChange, output.eurTry.dailyChange, 0.0001);
        assertEquals(input.gramGoldTry.dailyChange, output.gramGoldTry.dailyChange, 0.0001);
        assertEquals(input.brentUsd.dailyChange, output.brentUsd.dailyChange, 0.0001);
        assertEquals(input.refreshId, output.refreshId);
    }

    @Test
    public void doesNotDeriveDailyChangeFromAnUnverifiedPreviousSnapshot() {
        SharedPreferences prefs = inMemoryPreferences();
        long previousDay = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L;
        RateStore.save(prefs, snapshot(100, 200, 300,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, previousDay));
        RateStore.save(prefs, snapshot(110, 180, 330,
                Double.NaN, Double.NaN, Double.NaN, 75.0, Double.NaN, System.currentTimeMillis()));

        RateSnapshot output = RateStore.load(prefs);
        assertTrue(Double.isNaN(output.usdTry.dailyChange));
        assertTrue(Double.isNaN(output.eurTry.dailyChange));
        assertTrue(Double.isNaN(output.gramGoldTry.dailyChange));
    }

    @Test
    public void clearsCachedDailyChangesWhenNewSnapshotHasNoDailyChanges() {
        SharedPreferences prefs = inMemoryPreferences();
        long firstUpdated = System.currentTimeMillis();
        RateStore.save(prefs, snapshot(100, 200, 300,
                1.0, -2.0, 3.0, 75.0, 4.0, firstUpdated, 10));
        RateStore.save(prefs, snapshot(101, 201, 301,
                Double.NaN, Double.NaN, Double.NaN, 76.0, Double.NaN,
                firstUpdated + 1_000, 11));

        RateSnapshot output = RateStore.load(prefs);
        assertTrue(Double.isNaN(output.usdTry.dailyChange));
        assertTrue(Double.isNaN(output.eurTry.dailyChange));
        assertTrue(Double.isNaN(output.gramGoldTry.dailyChange));
        assertTrue(Double.isNaN(output.brentUsd.dailyChange));
    }

    @Test
    public void ignoresOlderRefreshResultEvenWhenItFinishesLater() {
        SharedPreferences prefs = inMemoryPreferences();
        long now = System.currentTimeMillis();
        RateSnapshot newer = snapshot(110, 210, 310,
                1.0, 2.0, 3.0, 80.0, 4.0, now, 2);
        RateSnapshot olderButLater = snapshot(100, 200, 300,
                9.0, 9.0, 9.0, 70.0, 9.0, now + 60_000, 1);

        assertTrue(RateStore.save(prefs, newer));
        assertTrue(!RateStore.save(prefs, olderButLater));

        RateSnapshot output = RateStore.load(prefs);
        assertEquals(110.0, output.usdTry.value, 0.0001);
        assertEquals(2L, output.refreshId);
    }

    @Test
    public void concurrentSavesLeaveTheNewestRefreshApplied() throws Exception {
        SharedPreferences prefs = inMemoryPreferences();
        long now = System.currentTimeMillis();
        RateSnapshot older = snapshot(100, 200, 300,
                1.0, 1.0, 1.0, 70.0, 1.0, now, 1);
        RateSnapshot newer = snapshot(110, 210, 310,
                2.0, 2.0, 2.0, 80.0, 2.0, now + 1_000, 2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        executor.submit(() -> {
            start.await();
            RateStore.save(prefs, older);
            return null;
        });
        executor.submit(() -> {
            start.await();
            RateStore.save(prefs, newer);
            return null;
        });
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        RateSnapshot output = RateStore.load(prefs);
        assertEquals(2L, output.refreshId);
        assertEquals(110.0, output.usdTry.value, 0.0001);
    }

    private static RateSnapshot snapshot(double usd, double eur, double gold,
                                         double dailyUsd, double dailyEur, double dailyGold,
                                         double brent, double dailyBrent, long updatedAt) {
        return snapshot(usd, eur, gold, dailyUsd, dailyEur, dailyGold,
                brent, dailyBrent, updatedAt, updatedAt);
    }

    private static RateSnapshot snapshot(double usd, double eur, double gold,
                                         double dailyUsd, double dailyEur, double dailyGold,
                                         double brent, double dailyBrent,
                                         long updatedAt, long refreshId) {
        return new RateSnapshot(
                new RateQuote(usd, dailyUsd),
                new RateQuote(eur, dailyEur),
                new RateQuote(gold, dailyGold),
                new RateQuote(brent, dailyBrent),
                updatedAt,
                refreshId);
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
