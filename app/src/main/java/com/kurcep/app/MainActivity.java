package com.kurcep.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private final NumberFormat currency = NumberFormat.getNumberInstance(new Locale("tr", "TR"));
    private TextView usdValue;
    private TextView eurValue;
    private TextView goldValue;
    private TextView updatedValue;
    private View refreshButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        currency.setMinimumFractionDigits(2);
        currency.setMaximumFractionDigits(2);
        usdValue = findViewById(R.id.usd_value);
        eurValue = findViewById(R.id.eur_value);
        goldValue = findViewById(R.id.gold_value);
        updatedValue = findViewById(R.id.updated_value);
        refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> refresh());

        RateData cached = RateStore.load(this);
        if (cached != null) show(cached);
        refresh();
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
        String time = new SimpleDateFormat("HH:mm", new Locale("tr", "TR"))
                .format(new Date(data.updatedAt));
        updatedValue.setText(getString(R.string.last_updated, time));
    }
}
