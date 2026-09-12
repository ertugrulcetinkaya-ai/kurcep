package com.kurcep.app;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static com.kurcep.app.RateJsonValues.requirePositive;
import static com.kurcep.app.RateJsonValues.requiredObject;
import static com.kurcep.app.RateJsonValues.requiredPositive;

/** Orchestrates provider calls and combines their results into one immutable snapshot. */
final class RateRepository implements RefreshDataLoader {
    static final String FX_URL = FxDataSource.CURRENT_URL;
    static final String GOLD_URL = GoldDataSource.CURRENT_URL;
    static final String GOLD_HISTORY_URL = GoldDataSource.HISTORY_URL;
    static final String BRENT_URL = BrentDataSource.URL;
    static final double GRAMS_PER_TROY_OUNCE = 31.1034768;
    static final int MAX_RESPONSE_BYTES = RateJsonClient.MAX_RESPONSE_BYTES;
    private static final int NETWORK_THREADS = 5;

    private final RateJsonClient jsonClient;
    private final FxDataSource fxSource;
    private final GoldDataSource goldSource;
    private final BrentDataSource brentSource;
    private final ExecutorService networkExecutor;
    private final RateDiagnostics diagnostics;

    RateRepository(RateTransport transport) {
        this(transport, newFixedThreadPool(), RateDiagnostics.NO_OP);
    }

    RateRepository(RateTransport transport, ExecutorService networkExecutor) {
        this(transport, networkExecutor, RateDiagnostics.NO_OP);
    }

    RateRepository(RateTransport transport, ExecutorService networkExecutor,
                   RateDiagnostics diagnostics) {
        this.jsonClient = new RateJsonClient(Objects.requireNonNull(transport, "transport"));
        this.fxSource = new FxDataSource(jsonClient);
        this.goldSource = new GoldDataSource(jsonClient);
        this.brentSource = new BrentDataSource(jsonClient);
        this.networkExecutor = Objects.requireNonNull(networkExecutor, "networkExecutor");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    static RateRepository production() {
        return new RateRepository(new HttpRateTransport(), newFixedThreadPool(),
                new AndroidRateDiagnostics());
    }

    RateSnapshot loadRates() throws Exception {
        return loadRates(System.currentTimeMillis());
    }

    @Override
    public RateSnapshot loadRates(long refreshId) throws Exception {
        String yesterday = yesterday();
        List<Future<?>> requests = new ArrayList<>(NETWORK_THREADS);
        Future<JSONObject> fxFuture = submit(requests, fxSource::current);
        Future<JSONObject> goldFuture = submit(requests, goldSource::current);
        Future<RateQuote> brentFuture = submit(requests, this::fetchBrent);
        Future<JSONObject> previousFxFuture = submit(requests, () -> fxSource.previous(yesterday));
        Future<JSONObject> goldHistoryFuture = submit(requests, goldSource::history);
        boolean successful = false;

        try {
            JSONObject rates = requiredObject(await(fxFuture), "rates");
            double usdTry = requiredPositive(rates, "TRY");
            double usdPerEur = requiredPositive(rates, "EUR");
            double eurTry = requirePositive(usdTry / usdPerEur, "EUR/TRY");

            double goldUsdPerOunce = requiredPositive(await(goldFuture), "price");
            double gramGoldTry = requirePositive(
                    goldUsdPerOunce * usdTry / GRAMS_PER_TROY_OUNCE, "gram gold TRY");

            JSONObject previousFx = awaitOptional(previousFxFuture, "Döviz geçmiş verisi");
            JSONObject goldHistory = awaitOptional(goldHistoryFuture, "Altın geçmiş verisi");
            double[] currencyChanges = calculateCurrencyDailyChange(
                    usdTry, eurTry, previousFx);
            double dailyGoldChange = calculateGoldDailyChange(
                    gramGoldTry, previousFx, goldHistory, yesterday);
            RateQuote brent = await(brentFuture);

            successful = true;
            return new RateSnapshot(
                    new RateQuote(usdTry, currencyChanges[0]),
                    new RateQuote(eurTry, currencyChanges[1]),
                    new RateQuote(gramGoldTry, dailyGoldChange),
                    brent,
                    System.currentTimeMillis(),
                    refreshId);
        } finally {
            if (!successful) {
                cancelAll(requests);
            }
        }
    }

    JSONObject getJson(String address) throws Exception {
        return jsonClient.getJson(address);
    }

    private double[] calculateCurrencyDailyChange(double usdTry, double eurTry,
                                                  JSONObject historical) {
        if (historical == null) {
            return new double[]{Double.NaN, Double.NaN};
        }
        try {
            JSONObject rates = requiredObject(historical, "rates");
            double previousUsdTry = requiredPositive(rates, "TRY");
            double previousUsdPerEur = requiredPositive(rates, "EUR");
            double previousEurTry = requirePositive(
                    previousUsdTry / previousUsdPerEur, "previous EUR/TRY");
            return new double[]{
                    RateMath.percentChange(usdTry, previousUsdTry),
                    RateMath.percentChange(eurTry, previousEurTry)
            };
        } catch (Exception error) {
            diagnostics.warn("Döviz geçmiş verisi geçersiz", error);
            return new double[]{Double.NaN, Double.NaN};
        }
    }

    private double calculateGoldDailyChange(double gramGoldTry, JSONObject historicalFx,
                                            JSONObject goldHistory, String yesterday) {
        if (historicalFx == null || goldHistory == null) {
            return Double.NaN;
        }
        try {
            JSONObject rates = requiredObject(historicalFx, "rates");
            double previousUsdTry = requiredPositive(rates, "TRY");
            double previousGoldUsdPerOunce = goldSource.previousClose(goldHistory, yesterday);
            double previousGoldTry = requirePositive(
                    previousGoldUsdPerOunce * previousUsdTry / GRAMS_PER_TROY_OUNCE,
                    "previous gram gold TRY");
            return RateMath.percentChange(gramGoldTry, previousGoldTry);
        } catch (Exception error) {
            diagnostics.warn("Altın geçmiş verisi geçersiz", error);
            return Double.NaN;
        }
    }

    // Brent is optional: a failure here removes only Brent from the snapshot.
    private RateQuote fetchBrent() throws Exception {
        try {
            return brentSource.fetch();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        } catch (Exception error) {
            diagnostics.warn("Brent verisi alınamadı", error);
            return RateQuote.unavailable();
        }
    }

    private <T> Future<T> submit(List<Future<?>> requests,
                                 java.util.concurrent.Callable<T> task) {
        Future<T> future = networkExecutor.submit(task);
        requests.add(future);
        return future;
    }

    private <T> T await(Future<T> future) throws Exception {
        try {
            return future.get();
        } catch (InterruptedException error) {
            cancelAll(java.util.Collections.singletonList(future));
            Thread.currentThread().interrupt();
            throw error;
        } catch (ExecutionException error) {
            throw asException(error.getCause());
        }
    }

    private JSONObject awaitOptional(Future<JSONObject> future, String source) throws Exception {
        try {
            return await(future);
        } catch (InterruptedException error) {
            throw error;
        } catch (Exception error) {
            diagnostics.warn(source + " alınamadı", error);
            return null;
        }
    }

    private static void cancelAll(List<? extends Future<?>> requests) {
        for (Future<?> request : requests) {
            request.cancel(true);
        }
    }

    static String previousFxUrl(String date) {
        return FxDataSource.previousUrl(date);
    }

    static String readLimitedBody(java.io.InputStream stream) throws Exception {
        return RateJsonClient.readLimitedBody(stream);
    }

    private static String yesterday() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private static ExecutorService newFixedThreadPool() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "kurcep-rate-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(NETWORK_THREADS, factory);
    }

    @Override
    public void close() {
        networkExecutor.shutdownNow();
    }

    private static Exception asException(Throwable error) {
        if (error instanceof Exception) {
            return (Exception) error;
        }
        return new PermanentRateException("Beklenmeyen repository hatası", error);
    }
}
