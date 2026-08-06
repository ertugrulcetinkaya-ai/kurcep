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

import java.util.concurrent.CountDownLatch;
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
                UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request);
    }

    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        CountDownLatch completed = new CountDownLatch(1);
        boolean[] successful = {false};
        RateRepository.fetch(new RateRepository.Callback() {
            @Override public void onSuccess(RateData data) {
                RateStore.save(context, data);
                RatesWidgetProvider.updateAll(context,
                        android.appwidget.AppWidgetManager.getInstance(context),
                        new ComponentName(context, RatesWidgetProvider.class));
                successful[0] = true;
                completed.countDown();
            }

            @Override public void onError(Exception error) {
                Log.w(TAG, "Widget güncellemesi başarısız", error);
                RatesWidgetProvider.updateAll(context,
                        android.appwidget.AppWidgetManager.getInstance(context),
                        new ComponentName(context, RatesWidgetProvider.class));
                completed.countDown();
            }
        });

        try {
            if (!completed.await(3, TimeUnit.MINUTES)) {
                Log.w(TAG, "Widget güncellemesi zaman aşımına uğradı");
                return Result.retry();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }
        return successful[0] ? Result.success() : Result.retry();
    }
}
