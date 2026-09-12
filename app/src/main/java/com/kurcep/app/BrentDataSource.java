package com.kurcep.app;

import org.json.JSONArray;
import org.json.JSONObject;

/** Yahoo Finance Brent endpoint and its schema-specific parsing. */
final class BrentDataSource {
    static final String URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/BZ=F?range=5d&interval=1d";

    private final RateJsonClient client;

    BrentDataSource(RateJsonClient client) {
        this.client = client;
    }

    RateQuote fetch() throws Exception {
        JSONObject chart = RateJsonValues.requiredObject(client.getJson(URL), "chart");
        JSONArray results = chart.optJSONArray("result");
        if (results == null || results.length() == 0) {
            throw new InvalidRateException("Brent sonuçları yok");
        }
        JSONObject result = results.getJSONObject(0);
        double current = RateJsonValues.requiredPositive(
                RateJsonValues.requiredObject(result, "meta"), "regularMarketPrice");
        JSONArray closes = RateJsonValues.requiredObject(result, "indicators")
                .getJSONArray("quote").getJSONObject(0).getJSONArray("close");
        if (closes.length() < 2) {
            throw new InvalidRateException("Brent geçmiş verisi yok");
        }
        double previous = Double.NaN;
        for (int i = closes.length() - 2; i >= 0; i--) {
            Object value = closes.opt(i);
            if (value instanceof Number) {
                double candidate = ((Number) value).doubleValue();
                if (RateJsonValues.isPositiveFinite(candidate)) {
                    previous = candidate;
                    break;
                }
            }
        }
        if (Double.isNaN(previous)) {
            throw new InvalidRateException("Brent geçerli kapanış içermiyor");
        }
        return new RateQuote(current, RateMath.percentChange(current, previous));
    }
}
