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

    static String previousUrl(String date) {
        return "https://api.frankfurter.app/" + date + "?from=USD&to=TRY,EUR";
    }
}
