package com.kurcep.app;

/** Logging seam that keeps repository parsing and tests independent of Android APIs. */
interface RateDiagnostics {
    RateDiagnostics NO_OP = (message, error) -> { };

    void warn(String message, Throwable error);
}
