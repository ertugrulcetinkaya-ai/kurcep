package com.kurcep.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Coordinates one shared refresh for the Activity and widget worker. */
final class RefreshCoordinator implements AutoCloseable {
    interface Callback {
        void onSuccess(RateSnapshot data);
        void onError(Exception error);
    }

    interface Subscription {
        void cancel();
    }

    private final Object lock = new Object();
    private final RateRepository repository;
    private final ExecutorService refreshExecutor;
    private final Handler main = new Handler(Looper.getMainLooper());
    private RefreshTask active;

    RefreshCoordinator(RateRepository repository) {
        this(repository, Executors.newSingleThreadExecutor(newRefreshThreadFactory()));
    }

    RefreshCoordinator(RateRepository repository, ExecutorService refreshExecutor) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.refreshExecutor = Objects.requireNonNull(refreshExecutor, "refreshExecutor");
    }

    Subscription refresh(Context context, Callback callback) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(callback, "callback");
        synchronized (lock) {
            RefreshTask task = getOrStartLocked(context.getApplicationContext());
            Registration registration = new Registration(task, callback);
            task.registrations.add(registration);
            return registration;
        }
    }

    RateSnapshot refreshBlocking(Context context) throws Exception {
        RefreshTask task;
        synchronized (lock) {
            task = getOrStartLocked(context.getApplicationContext());
        }
        try {
            return task.get();
        } catch (InterruptedException error) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw error;
        } catch (CancellationException error) {
            throw new InterruptedException("Refresh iptal edildi");
        } catch (ExecutionException error) {
            throw asException(error.getCause());
        }
    }

    private RefreshTask getOrStartLocked(Context context) {
        if (active == null || active.isDone()) {
            long refreshId = RateStore.nextRefreshId(context);
            active = new RefreshTask(context, refreshId);
            refreshExecutor.execute(active);
        }
        return active;
    }

    private RateSnapshot executeRefresh(Context context, long refreshId) throws Exception {
        RateSnapshot data = repository.loadRates(refreshId);
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Refresh iptal edildi");
        }
        boolean saved = RateStore.save(context, data);
        if (saved) {
            return data;
        }
        RateSnapshot current = RateStore.load(context);
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

        RefreshTask(Context context, long refreshId) {
            super(() -> executeRefresh(context, refreshId));
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
            main.post(() -> {
                if (!registration.isCancelled()) {
                    registration.callback.onSuccess(data);
                }
            });
        }

        private void postError(Registration registration, Exception error) {
            main.post(() -> {
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
