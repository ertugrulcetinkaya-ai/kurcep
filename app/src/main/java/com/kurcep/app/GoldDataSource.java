package com.kurcep.app;

import org.json.JSONArray;
import org.json.JSONObject;

/** Gold spot/history endpoints and historical close extraction. */
final class GoldDataSource {
    static final String CURRENT_URL = "https://api.gold-api.com/price/XAU";
    static final String HISTORY_URL = "https://xaus.com/api/v1/history";

    private final RateJsonClient client;

    GoldDataSource(RateJsonClient client) {
        this.client = client;
    }

    JSONObject current() throws Exception {
        return client.getJson(CURRENT_URL);
    }

    JSONObject history() throws Exception {
        return client.getJson(HISTORY_URL);
    }

    double previousClose(JSONObject history, String yesterday) throws Exception {
        JSONArray points = history.optJSONArray("points");
        if (points == null) {
            throw new InvalidRateException("Altın geçmiş noktaları yok");
        }
        for (int i = points.length() - 1; i >= 0; i--) {
            JSONObject point = points.optJSONObject(i);
            if (point == null || point.isNull("d") || point.isNull("c")) {
                continue;
            }
            String date = point.getString("d");
            if (date.compareTo(yesterday) <= 0) {
                double close = point.getDouble("c");
                if (RateJsonValues.isPositiveFinite(close)) {
                    return close;
                }
            }
        }
        throw new InvalidRateException("Altın geçerli geçmiş kapanışı yok");
    }
}
