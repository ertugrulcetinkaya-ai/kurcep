package com.kurcep.app;

/**
 * Minimal HTTP transport used by {@link RateRepository}. The production
 * implementation is HttpURLConnection based; unit tests inject a fake
 * instance. The transport only performs the raw HTTP request: status-code
 * handling and JSON parsing stay in {@link RateRepository}.
 */
interface RateTransport {
    Response get(String url) throws Exception;

    final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
