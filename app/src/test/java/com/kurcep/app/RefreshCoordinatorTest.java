package com.kurcep.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

public class RefreshCoordinatorTest {
    private RefreshCoordinator coordinator;

    @After
    public void tearDown() {
        if (coordinator != null) {
            coordinator.close();
        }
    }

    @Test
    public void twoSubscribersShareOneLoadAndBothReceiveSuccess() throws Exception {
        BlockingLoader loader = new BlockingLoader(false);
        TestStore store = new TestStore();
        coordinator = newCoordinator(loader, store);
        CountDownLatch callbacks = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();

        coordinator.refresh(callback(callbacks, successes, new AtomicInteger()));
        coordinator.refresh(callback(callbacks, successes, new AtomicInteger()));
        assertTrue(loader.started.await(2, TimeUnit.SECONDS));
        loader.release.countDown();

        assertTrue(callbacks.await(2, TimeUnit.SECONDS));
        assertEquals(1, loader.loads.get());
        assertEquals(2, successes.get());
        assertEquals(1, store.saves.get());
    }

    @Test
    public void cancellingFirstSubscriptionDoesNotAffectSecond() throws Exception {
        BlockingLoader loader = new BlockingLoader(false);
        TestStore store = new TestStore();
        coordinator = newCoordinator(loader, store);
        CountDownLatch secondCallback = new CountDownLatch(1);
        AtomicInteger firstCallbacks = new AtomicInteger();

        RefreshCoordinator.Subscription first = coordinator.refresh(new RefreshCoordinator.Callback() {
            @Override
            public void onSuccess(RateSnapshot data) {
                firstCallbacks.incrementAndGet();
            }

            @Override
            public void onError(Exception error) {
                firstCallbacks.incrementAndGet();
            }
        });
        coordinator.refresh(new RefreshCoordinator.Callback() {
            @Override
            public void onSuccess(RateSnapshot data) {
                secondCallback.countDown();
            }

            @Override
            public void onError(Exception error) {
                secondCallback.countDown();
            }
        });
        first.cancel();
        assertTrue(loader.started.await(2, TimeUnit.SECONDS));
        loader.release.countDown();

        assertTrue(secondCallback.await(2, TimeUnit.SECONDS));
        assertEquals(0, firstCallbacks.get());
        assertEquals(1, loader.loads.get());
    }

    @Test
    public void interruptingBlockingWaiterDoesNotCancelSharedTask() throws Exception {
        BlockingLoader loader = new BlockingLoader(false);
        TestStore store = new TestStore();
        coordinator = newCoordinator(loader, store);
        CountDownLatch activityCallback = new CountDownLatch(1);
        AtomicBoolean workerInterrupted = new AtomicBoolean();
        AtomicBoolean workerFinished = new AtomicBoolean();

        coordinator.refresh(new RefreshCoordinator.Callback() {
            @Override
            public void onSuccess(RateSnapshot data) {
                activityCallback.countDown();
            }

            @Override
            public void onError(Exception error) {
                activityCallback.countDown();
            }
        });
        Thread worker = new Thread(() -> {
            try {
                coordinator.refreshBlocking(store);
            } catch (InterruptedException error) {
                workerInterrupted.set(true);
            } catch (Exception error) {
                throw new AssertionError(error);
            } finally {
                workerFinished.set(true);
            }
        });
        worker.start();

        assertTrue(loader.started.await(2, TimeUnit.SECONDS));
        worker.interrupt();
        worker.join(2_000);
        assertTrue(workerFinished.get());
        assertTrue(workerInterrupted.get());
        assertFalse(loader.interrupted.get());

        loader.release.countDown();
        assertTrue(activityCallback.await(2, TimeUnit.SECONDS));
        assertEquals(1, loader.loads.get());
        assertEquals(1, store.saves.get());
    }

    @Test
    public void failedTaskIsClearedAndNextRefreshStartsANewTask() throws Exception {
        BlockingLoader loader = new BlockingLoader(true);
        TestStore store = new TestStore();
        coordinator = newCoordinator(loader, store);
        CountDownLatch firstError = new CountDownLatch(1);
        CountDownLatch secondSuccess = new CountDownLatch(1);

        coordinator.refresh(new RefreshCoordinator.Callback() {
            @Override
            public void onSuccess(RateSnapshot data) {
                firstError.countDown();
            }

            @Override
            public void onError(Exception error) {
                firstError.countDown();
            }
        });
        assertTrue(firstError.await(2, TimeUnit.SECONDS));
        loader.release.countDown();

        coordinator.refresh(new RefreshCoordinator.Callback() {
            @Override
            public void onSuccess(RateSnapshot data) {
                secondSuccess.countDown();
            }

            @Override
            public void onError(Exception error) {
                secondSuccess.countDown();
            }
        });
        assertTrue(secondSuccess.await(2, TimeUnit.SECONDS));
        assertEquals(2, loader.loads.get());
        assertEquals(1, store.saves.get());
    }

    @Test
    public void cancelledCallbackIsNotDeliveredAfterTaskCompletes() throws Exception {
        BlockingLoader loader = new BlockingLoader(false);
        TestStore store = new TestStore();
        coordinator = newCoordinator(loader, store);
        AtomicInteger callbacks = new AtomicInteger();
        RefreshCoordinator.Subscription subscription = coordinator.refresh(
                callback(new CountDownLatch(1), callbacks, new AtomicInteger()));
        subscription.cancel();
        assertTrue(loader.started.await(2, TimeUnit.SECONDS));
        loader.release.countDown();
        assertTrue(store.saved.await(2, TimeUnit.SECONDS));

        assertEquals(0, callbacks.get());
    }

    @Test
    public void refreshIdsAreMonotonicWithoutSynchronousPersistence() {
        BlockingLoader loader = new BlockingLoader(false);
        coordinator = newCoordinator(loader, new TestStore());

        long first = coordinator.nextRefreshIdForTest();
        long second = coordinator.nextRefreshIdForTest();

        assertTrue(second > first);
    }

    private RefreshCoordinator newCoordinator(BlockingLoader loader, TestStore store) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        return new RefreshCoordinator(loader, store, executor, Runnable::run);
    }

    private static RefreshCoordinator.Callback callback(CountDownLatch callbacks,
                                                         AtomicInteger successes,
                                                         AtomicInteger errors) {
        return new RefreshCoordinator.Callback() {
            @Override
            public void onSuccess(RateSnapshot data) {
                successes.incrementAndGet();
                callbacks.countDown();
            }

            @Override
            public void onError(Exception error) {
                errors.incrementAndGet();
                callbacks.countDown();
            }
        };
    }

    private static final class TestStore implements RefreshStore {
        private final AtomicInteger saves = new AtomicInteger();
        private final CountDownLatch saved = new CountDownLatch(1);
        private volatile RateSnapshot current;

        @Override
        public boolean save(RateSnapshot data) {
            saves.incrementAndGet();
            current = data;
            saved.countDown();
            return true;
        }

        @Override
        public RateSnapshot load() {
            return current;
        }
    }

    private static final class BlockingLoader implements RefreshDataLoader {
        private final AtomicInteger loads = new AtomicInteger();
        private final AtomicBoolean interrupted = new AtomicBoolean();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final boolean failFirst;

        BlockingLoader(boolean failFirst) {
            this.failFirst = failFirst;
        }

        @Override
        public RateSnapshot loadRates(long refreshId) throws Exception {
            int attempt = loads.incrementAndGet();
            started.countDown();
            if (failFirst && attempt == 1) {
                throw new PermanentRateException("test failure");
            }
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                interrupted.set(true);
                throw error;
            }
            return new RateSnapshot(
                    new RateQuote(40, 1),
                    new RateQuote(45, 1),
                    new RateQuote(2_000, 1),
                    new RateQuote(80, 1),
                    System.currentTimeMillis(),
                    refreshId);
        }

        @Override
        public void close() {
            release.countDown();
        }
    }
}
