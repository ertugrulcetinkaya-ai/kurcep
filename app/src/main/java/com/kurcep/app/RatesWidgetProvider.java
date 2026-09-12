package com.kurcep.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.text.NumberFormat;

public class RatesWidgetProvider extends AppWidgetProvider {
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
            RatesWidgetWorker.enqueue(context);
        } else {
            super.onReceive(context, intent);
        }
    }

    static void updateAll(Context context, AppWidgetManager manager, ComponentName provider) {
        int[] ids = manager.getAppWidgetIds(provider);
        render(context, manager, ids, RateStore.load(context), false);
    }

    private static void render(Context context, AppWidgetManager manager, int[] ids,
                               RateSnapshot data, boolean loading) {
        NumberFormat format = Formatters.currency();
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_rates);
            if (data == null) {
                views.setTextViewText(R.id.widget_usd, "—");
                views.setTextViewText(R.id.widget_eur, "—");
                views.setTextViewText(R.id.widget_gold, "—");
                views.setTextViewText(R.id.widget_brent, "—");
                views.setTextViewText(R.id.widget_usd_change, "—");
                views.setTextViewText(R.id.widget_eur_change, "—");
                views.setTextViewText(R.id.widget_gold_change, "—");
                views.setTextViewText(R.id.widget_brent_change, "—");
                views.setTextViewText(R.id.widget_updated, context.getString(
                        loading ? R.string.updating : R.string.tap_to_refresh));
            } else {
                views.setTextViewText(R.id.widget_usd, "₺" + format.format(data.usdTry.value));
                views.setTextViewText(R.id.widget_eur, "₺" + format.format(data.eurTry.value));
                views.setTextViewText(R.id.widget_gold,
                        "₺" + format.format(data.gramGoldTry.value));
                setChange(views, R.id.widget_usd_change, data.usdTry, context);
                setChange(views, R.id.widget_eur_change, data.eurTry, context);
                setChange(views, R.id.widget_gold_change,
                        data.gramGoldTry, context);
                views.setTextViewText(R.id.widget_brent, !data.brentUsd.isAvailable()
                        ? "—" : "$" + format.format(data.brentUsd.value));
                setChange(views, R.id.widget_brent_change, data.brentUsd, context);
                views.setTextViewText(R.id.widget_updated, loading
                        ? context.getString(R.string.updating)
                        : context.getString(R.string.widget_updated,
                        RateDisplayFormatter.time(data.updatedAt)));
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

    private static void setChange(RemoteViews views, int viewId,
                                  RateQuote quote, Context context) {
        if (!quote.hasDailyChange()) {
            views.setTextColor(viewId, context.getColor(R.color.muted));
        } else {
            boolean up = quote.dailyChange >= 0;
            views.setTextColor(viewId, context.getColor(up ? R.color.change_up : R.color.change_down));
        }
        NumberFormat format = Formatters.currency();
        views.setTextViewText(viewId, RateDisplayFormatter.change(format, quote));
    }
}
