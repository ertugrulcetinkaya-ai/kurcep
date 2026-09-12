package com.kurcep.app;

/** Persistence boundary used by the refresh coordinator. */
interface RefreshStore {
    boolean save(RateSnapshot data);

    RateSnapshot load();
}
