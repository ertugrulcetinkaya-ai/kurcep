package com.kurcep.app;

import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public final class RatesWidgetWorker extends Worker {
    private static final String TAG = "RatesWidgetWorker";
    private static final String UNIQUE_WORK = "rates_widget_refresh";

    public RatesWidgetWorker(Context context, WorkerParameters parameters) {
        super(context, parameters);
    }

    static void enqueue(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(RatesWidgetWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                UNIQUE_WORK, ExistingWorkPolicy.KEEP, request);
    }

    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        RefreshCoordinator coordinator = ((KurCepApplication) context).getRefreshCoordinator();
        try {
            coordinator.refreshBlocking(context);
            if (isStopped()) {
                return Result.failure();
            }
            RatesWidgetProvider.updateAll(context,
                    android.appwidget.AppWidgetManager.getInstance(context),
                    new ComponentName(context, RatesWidgetProvider.class));
            return Result.success();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Result.failure();
        } catch (Exception error) {
            Log.w(TAG, "Widget güncellemesi başarısız", error);
            RatesWidgetProvider.updateAll(context,
                    android.appwidget.AppWidgetManager.getInstance(context),
                    new ComponentName(context, RatesWidgetProvider.class));
            if (isRetryable(error) && getRunAttemptCount() < 3) {
                return Result.retry();
            }
            return Result.failure();
        }
    }

    private static boolean isRetryable(Exception error) {
        if (error instanceof RateHttpException) {
            return ((RateHttpException) error).isRetryable();
        }
        return error instanceof TransientRateException;
    }
}
