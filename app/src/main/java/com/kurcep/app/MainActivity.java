package com.kurcep.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.NumberFormat;

public class MainActivity extends Activity {
    private final NumberFormat currency = Formatters.currency();
    private TextView usdValue;
    private TextView eurValue;
    private TextView goldValue;
    private TextView brentValue;
    private TextView usdChange;
    private TextView eurChange;
    private TextView goldChange;
    private TextView brentChange;
    private TextView updatedValue;
    private View refreshButton;
    private long activeUiRefreshId;
    private RefreshCoordinator.Subscription refreshSubscription;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applySystemBarInsets();
        usdValue = findViewById(R.id.usd_value);
        eurValue = findViewById(R.id.eur_value);
        goldValue = findViewById(R.id.gold_value);
        usdChange = findViewById(R.id.usd_change);
        eurChange = findViewById(R.id.eur_change);
        goldChange = findViewById(R.id.gold_change);
        brentValue = findViewById(R.id.brent_value);
        brentChange = findViewById(R.id.brent_change);
        updatedValue = findViewById(R.id.updated_value);
        refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> refresh());

        RateSnapshot cached = RateStore.load(this);
        if (cached != null) show(cached);
        refresh();
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.main_root);
        int baseLeft = root.getPaddingLeft();
        int baseTop = root.getPaddingTop();
        int baseRight = root.getPaddingRight();
        int baseBottom = root.getPaddingBottom();
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(baseLeft, baseTop + systemBars.top,
                    baseRight, baseBottom + systemBars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void refresh() {
        if (refreshSubscription != null) {
            refreshSubscription.cancel();
        }
        refreshButton.setEnabled(false);
        updatedValue.setText(R.string.updating);
        long uiRefreshId = ++activeUiRefreshId;
        refreshSubscription = getRefreshCoordinator().refresh(this, new RefreshCoordinator.Callback() {
            @Override public void onSuccess(RateSnapshot data) {
                if (!isCurrentRefresh(uiRefreshId)) return;
                show(data);
                refreshSubscription = null;
                refreshButton.setEnabled(true);
                RatesWidgetProvider.updateAll(MainActivity.this,
                        AppWidgetManager.getInstance(MainActivity.this),
                        new ComponentName(MainActivity.this, RatesWidgetProvider.class));
            }

            @Override public void onError(Exception error) {
                if (!isCurrentRefresh(uiRefreshId)) return;
                refreshSubscription = null;
                refreshButton.setEnabled(true);
                RateSnapshot cached = RateStore.load(MainActivity.this);
                if (cached != null) show(cached);
                else updatedValue.setText(R.string.no_data);
                Toast.makeText(MainActivity.this, R.string.update_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private RefreshCoordinator getRefreshCoordinator() {
        return ((KurCepApplication) getApplication()).getRefreshCoordinator();
    }

    private boolean isCurrentRefresh(long uiRefreshId) {
        return !destroyed && !isFinishing() && activeUiRefreshId == uiRefreshId;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (refreshSubscription != null) {
            refreshSubscription.cancel();
            refreshSubscription = null;
        }
        super.onDestroy();
    }

    private void show(RateSnapshot data) {
        usdValue.setText(getString(R.string.lira_value, currency.format(data.usdTry.value)));
        eurValue.setText(getString(R.string.lira_value, currency.format(data.eurTry.value)));
        goldValue.setText(getString(R.string.lira_value,
                currency.format(data.gramGoldTry.value)));
        brentValue.setText(!data.brentUsd.isAvailable() ? "—"
                : getString(R.string.usd_value, currency.format(data.brentUsd.value)));
        showChange(usdChange, data.usdTry);
        showChange(eurChange, data.eurTry);
        showChange(goldChange, data.gramGoldTry);
        showChange(brentChange, data.brentUsd);
        updatedValue.setText(getString(R.string.last_updated,
                RateDisplayFormatter.time(data.updatedAt)));
    }

    private void showChange(TextView view, RateQuote quote) {
        if (!quote.hasDailyChange()) {
            view.setTextColor(getColor(R.color.muted));
        } else {
            boolean up = quote.dailyChange >= 0;
            view.setTextColor(getColor(up ? R.color.change_up : R.color.change_down));
        }
        view.setText(RateDisplayFormatter.change(currency, quote));
    }
}
