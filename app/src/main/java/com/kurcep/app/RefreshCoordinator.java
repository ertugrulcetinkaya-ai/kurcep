package com.kurcep.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Coordinates one shared refresh for the Activity and widget worker. */
final class RefreshCoordinator implements AutoCloseable {
    interface Callback {
        void onSuccess(RateSnapshot data);

        void onError(Exception error);
    }

    interface Subscription {
        void cancel();
    }

    private interface StoreFactory {
        RefreshStore create(Context context);
    }

    private final Object lock = new Object();
    private final RefreshDataLoader repository;
    private final ExecutorService refreshExecutor;
    private final Executor callbackExecutor;
    private final StoreFactory storeFactory;
    private final AtomicLong refreshSequence = new AtomicLong();
    private RefreshTask active;

    RefreshCoordinator(RateRepository repository) {
        this(repository, Executors.newSingleThreadExecutor(newRefreshThreadFactory()),
                mainThreadExecutor());
    }

    RefreshCoordinator(RefreshDataLoader repository, ExecutorService refreshExecutor,
                       Executor callbackExecutor) {
        this(repository, refreshExecutor, callbackExecutor, AndroidRefreshStore::new);
    }

    RefreshCoordinator(RefreshDataLoader repository, RefreshStore store,
                       ExecutorService refreshExecutor, Executor callbackExecutor) {
        this(repository, refreshExecutor, callbackExecutor, ignored -> store);
    }

    private RefreshCoordinator(RefreshDataLoader repository, ExecutorService refreshExecutor,
                               Executor callbackExecutor, StoreFactory storeFactory) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.refreshExecutor = Objects.requireNonNull(refreshExecutor, "refreshExecutor");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.storeFactory = Objects.requireNonNull(storeFactory, "storeFactory");
    }

    Subscription refresh(Context context, Callback callback) {
        Objects.requireNonNull(context, "context");
        return register(storeFactory.create(applicationContext(context)), callback);
    }

    /** Package-private JVM-test entry point using an injected store. */
    Subscription refresh(Callback callback) {
        return register(storeFactory.create(null), callback);
    }

    private Subscription register(RefreshStore store, Callback callback) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(callback, "callback");
        synchronized (lock) {
            RefreshTask task = getOrStartLocked(store);
            Registration registration = new Registration(task, callback);
            task.registrations.add(registration);
            return registration;
        }
    }

    RateSnapshot refreshBlocking(Context context) throws Exception {
        Objects.requireNonNull(context, "context");
        return refreshBlocking(storeFactory.create(applicationContext(context)));
    }

    /** Package-private JVM-test entry point using an injected store. */
    RateSnapshot refreshBlocking(RefreshStore store) throws Exception {
        RefreshTask task;
        synchronized (lock) {
            task = getOrStartLocked(Objects.requireNonNull(store, "store"));
        }
        try {
            return task.get();
        } catch (InterruptedException error) {
            // This waiter owns only its wait. The FutureTask can be shared by Activity
            // registrations, so interrupting one consumer must not cancel the refresh.
            Thread.currentThread().interrupt();
            throw error;
        } catch (CancellationException error) {
            throw new InterruptedException("Refresh iptal edildi");
        } catch (ExecutionException error) {
            throw asException(error.getCause());
        }
    }

    long nextRefreshIdForTest() {
        return nextRefreshId();
    }

    private RefreshTask getOrStartLocked(RefreshStore store) {
        if (active == null || active.isDone()) {
            active = new RefreshTask(store, nextRefreshId());
            refreshExecutor.execute(active);
        }
        return active;
    }

    private long nextRefreshId() {
        long now = System.currentTimeMillis();
        while (true) {
            long previous = refreshSequence.get();
            if (previous == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            long next = Math.max(now, previous + 1);
            if (refreshSequence.compareAndSet(previous, next)) {
                return next;
            }
        }
    }

    private RateSnapshot executeRefresh(RefreshStore store, long refreshId) throws Exception {
        RateSnapshot data = repository.loadRates(refreshId);
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Refresh iptal edildi");
        }
        boolean saved = store.save(data);
        if (saved) {
            return data;
        }
        RateSnapshot current = store.load();
        return current == null ? data : current;
    }

    @Override
    public void close() {
        RefreshTask task;
        synchronized (lock) {
            task = active;
        }
        if (task != null) {
            task.cancel(true);
        }
        refreshExecutor.shutdownNow();
        repository.close();
    }

    private final class RefreshTask extends FutureTask<RateSnapshot> {
        private final List<Registration> registrations = new ArrayList<>();

        RefreshTask(RefreshStore store, long refreshId) {
            super(() -> executeRefresh(store, refreshId));
        }

        @Override
        protected void done() {
            List<Registration> completed;
            synchronized (lock) {
                if (active == this) {
                    active = null;
                }
                completed = new ArrayList<>(registrations);
                registrations.clear();
            }

            try {
                RateSnapshot data = get();
                for (Registration registration : completed) {
                    postSuccess(registration, data);
                }
            } catch (CancellationException error) {
                for (Registration registration : completed) {
                    postError(registration, new InterruptedException("Refresh iptal edildi"));
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                for (Registration registration : completed) {
                    postError(registration, error);
                }
            } catch (ExecutionException error) {
                Exception cause = asException(error.getCause());
                for (Registration registration : completed) {
                    postError(registration, cause);
                }
            }
        }

        private void postSuccess(Registration registration, RateSnapshot data) {
            callbackExecutor.execute(() -> {
                if (!registration.isCancelled()) {
                    registration.callback.onSuccess(data);
                }
            });
        }

        private void postError(Registration registration, Exception error) {
            callbackExecutor.execute(() -> {
                if (!registration.isCancelled()) {
                    registration.callback.onError(error);
                }
            });
        }
    }

    private final class Registration implements Subscription {
        private final RefreshTask task;
        private final Callback callback;
        private boolean cancelled;

        Registration(RefreshTask task, Callback callback) {
            this.task = task;
            this.callback = callback;
        }

        @Override
        public void cancel() {
            synchronized (lock) {
                cancelled = true;
                task.registrations.remove(this);
            }
        }

        boolean isCancelled() {
            synchronized (lock) {
                return cancelled;
            }
        }
    }

    private static Context applicationContext(Context context) {
        Context application = context.getApplicationContext();
        return application == null ? context : application;
    }

    private static Executor mainThreadExecutor() {
        Handler handler = new Handler(Looper.getMainLooper());
        return handler::post;
    }

    private static Exception asException(Throwable error) {
        if (error instanceof Exception) {
            return (Exception) error;
        }
        return new PermanentRateException("Beklenmeyen refresh hatası", error);
    }

    private static ThreadFactory newRefreshThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "kurcep-refresh-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
