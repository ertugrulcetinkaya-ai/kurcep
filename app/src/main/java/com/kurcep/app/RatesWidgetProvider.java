package com.kurcep.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RatesWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "RatesWidgetProvider";
    private static final String ACTION_REFRESH = "com.kurcep.app.REFRESH_WIDGET";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_REFRESH.equals(action) || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            Bundle extras = intent.getExtras();
            int[] ids = extras == null ? null
                    : extras.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            if (ids == null || ids.length == 0) {
                ids = manager.getAppWidgetIds(new ComponentName(context, RatesWidgetProvider.class));
            }
            render(context, manager, ids, RateStore.load(context), true);
            // Do not keep the broadcast open while remote APIs are contacted. Android can
            // terminate a goAsync() broadcast after a short window, while network calls may
            // legitimately take longer. The callback updates the widget when ready.
            fetchAndUpdate(context, manager, ids);
        } else {
            super.onReceive(context, intent);
        }
    }

    private static void fetchAndUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Context appContext = context.getApplicationContext();
        RateRepository.fetch(new RateRepository.Callback() {
            @Override public void onSuccess(RateData data) {
                RateStore.save(appContext, data);
                render(appContext, manager, ids, data, false);
            }

            @Override public void onError(Exception error) {
                Log.w(TAG, "Widget güncellemesi başarısız", error);
                render(appContext, manager, ids, RateStore.load(appContext), false);
            }
        });
    }

    static void updateAll(Context context, AppWidgetManager manager, ComponentName provider) {
        int[] ids = manager.getAppWidgetIds(provider);
        render(context, manager, ids, RateStore.load(context), false);
    }

    private static void render(Context context, AppWidgetManager manager, int[] ids,
                               RateData data, boolean loading) {
        NumberFormat format = Formatters.currency();
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_rates);
            if (data == null) {
                views.setTextViewText(R.id.widget_usd, "—");
                views.setTextViewText(R.id.widget_eur, "—");
                views.setTextViewText(R.id.widget_gold, "—");
                views.setTextViewText(R.id.widget_usd_change, "—");
                views.setTextViewText(R.id.widget_eur_change, "—");
                views.setTextViewText(R.id.widget_gold_change, "—");
                views.setTextViewText(R.id.widget_updated, context.getString(
                        loading ? R.string.updating : R.string.tap_to_refresh));
            } else {
                views.setTextViewText(R.id.widget_usd, "₺" + format.format(data.usdTry));
                views.setTextViewText(R.id.widget_eur, "₺" + format.format(data.eurTry));
                views.setTextViewText(R.id.widget_gold, "₺" + format.format(data.gramGoldTry));
                setChange(views, R.id.widget_usd_change, data.usdDailyChange, context);
                setChange(views, R.id.widget_eur_change, data.eurDailyChange, context);
                setChange(views, R.id.widget_gold_change, data.goldDailyChange, context);
                String time = new SimpleDateFormat("HH:mm", new Locale("tr", "TR"))
                        .format(new Date(data.updatedAt));
                views.setTextViewText(R.id.widget_updated, loading
                        ? context.getString(R.string.updating)
                        : context.getString(R.string.widget_updated, time));
            }

            Intent refresh = new Intent(context, RatesWidgetProvider.class).setAction(ACTION_REFRESH);
            PendingIntent refreshIntent = PendingIntent.getBroadcast(context, 0, refresh,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent);

            Intent open = new Intent(context, MainActivity.class);
            PendingIntent openIntent = PendingIntent.getActivity(context, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_title, openIntent);
            manager.updateAppWidget(id, views);
        }
    }

    private static void setChange(RemoteViews views, int viewId, double change, Context context) {
        if (Double.isNaN(change)) {
            views.setTextViewText(viewId, "—");
            return;
        }
        boolean up = change >= 0;
        NumberFormat format = Formatters.currency();
        views.setTextViewText(viewId, (up ? "↑ " : "↓ ") + format.format(Math.abs(change)) + "%");
        views.setTextColor(viewId, context.getColor(up ? R.color.change_up : R.color.change_down));
    }
}
