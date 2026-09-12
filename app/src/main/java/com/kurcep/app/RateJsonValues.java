package com.kurcep.app;

import org.json.JSONException;
import org.json.JSONObject;

/** Shared validation for provider-specific JSON parsers. */
final class RateJsonValues {
    private static final double MAX_FINANCIAL_VALUE = 1_000_000_000d;

    static JSONObject requiredObject(JSONObject object, String key)
            throws InvalidRateException {
        JSONObject value = object.optJSONObject(key);
        if (value == null) {
            throw new InvalidRateException("Eksik JSON nesnesi: " + key);
        }
        return value;
    }

    static double requiredPositive(JSONObject object, String key)
            throws InvalidRateException {
        try {
            return requirePositive(object.getDouble(key), key);
        } catch (JSONException error) {
            throw new InvalidRateException("Eksik veya sayısal olmayan alan: " + key, error);
        }
    }

    static double requirePositive(double value, String name) throws InvalidRateException {
        if (!isPositiveFinite(value) || value > MAX_FINANCIAL_VALUE) {
            throw new InvalidRateException("Geçersiz finansal değer (" + name + "): " + value);
        }
        return value;
    }

    static boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private RateJsonValues() {}
}
