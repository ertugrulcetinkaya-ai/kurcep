package com.kurcep.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private final NumberFormat currency = Formatters.currency();
    private TextView usdValue;
    private TextView eurValue;
    private TextView goldValue;
    private TextView usdChange;
    private TextView eurChange;
    private TextView goldChange;
    private TextView updatedValue;
    private View refreshButton;

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
        updatedValue = findViewById(R.id.updated_value);
        refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> refresh());

        RateData cached = RateStore.load(this);
        if (cached != null) show(cached);
        refresh();
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.main_root);
        int baseLeft = root.getPaddingLeft();
        int baseTop = root.getPaddingTop();
        int baseRight = root.getPaddingRight();
        int baseBottom = root.getPaddingBottom();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets systemBars = insets.getInsets(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                top = systemBars.top;
                bottom = systemBars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(baseLeft, baseTop + top, baseRight, baseBottom + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void refresh() {
        refreshButton.setEnabled(false);
        updatedValue.setText(R.string.updating);
        RateRepository.fetch(new RateRepository.Callback() {
            @Override public void onSuccess(RateData data) {
                if (isFinishing()) return;
                RateStore.save(MainActivity.this, data);
                show(data);
                refreshButton.setEnabled(true);
                RatesWidgetProvider.updateAll(MainActivity.this,
                        AppWidgetManager.getInstance(MainActivity.this),
                        new ComponentName(MainActivity.this, RatesWidgetProvider.class));
            }

            @Override public void onError(Exception error) {
                if (isFinishing()) return;
                refreshButton.setEnabled(true);
                RateData cached = RateStore.load(MainActivity.this);
                if (cached != null) show(cached);
                else updatedValue.setText(R.string.no_data);
                Toast.makeText(MainActivity.this, R.string.update_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void show(RateData data) {
        usdValue.setText(getString(R.string.lira_value, currency.format(data.usdTry)));
        eurValue.setText(getString(R.string.lira_value, currency.format(data.eurTry)));
        goldValue.setText(getString(R.string.lira_value, currency.format(data.gramGoldTry)));
        showChange(usdChange, data.usdDailyChange);
        showChange(eurChange, data.eurDailyChange);
        showChange(goldChange, data.goldDailyChange);
        String time = new SimpleDateFormat("HH:mm", new Locale("tr", "TR"))
                .format(new Date(data.updatedAt));
        updatedValue.setText(getString(R.string.last_updated, time));
    }

    private void showChange(TextView view, double change) {
        if (Double.isNaN(change)) {
            view.setText("—");
            view.setTextColor(getColor(R.color.muted));
            return;
        }
        boolean up = change >= 0;
        view.setText(getString(R.string.daily_change, up ? "↑" : "↓", currency.format(Math.abs(change))));
        view.setTextColor(getColor(up ? R.color.change_up : R.color.change_down));
    }
}
