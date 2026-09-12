package com.kurcep.app;

import android.util.Log;

final class AndroidRateDiagnostics implements RateDiagnostics {
    private static final String TAG = "RateRepository";

    @Override
    public void warn(String message, Throwable error) {
        Log.w(TAG, message, error);
    }
}
