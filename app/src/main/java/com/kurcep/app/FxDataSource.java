package com.kurcep.app;

import org.json.JSONObject;

/** Exchange-rate endpoints and their URL policy. */
final class FxDataSource {
    static final String CURRENT_URL = "https://open.er-api.com/v6/latest/USD";

    private final RateJsonClient client;

    FxDataSource(RateJsonClient client) {
        this.client = client;
    }

    JSONObject current() throws Exception {
        return client.getJson(CURRENT_URL);
    }

    JSONObject previous(String date) throws Exception {
        return client.getJson(previousUrl(date));
    }

    /** ExchangeRate-API's own update time in epoch millis, or 0 when absent. */
    static long dataTime(JSONObject current) {
        long seconds = current.optLong("time_last_update_unix", 0L);
        return seconds > 0 ? seconds * 1000L : 0L;
    }

    static String previousUrl(String date) {
        return "https://api.frankfurter.dev/v1/" + date + "?from=USD&to=TRY,EUR";
    }
}
