package com.kurcep.app;

interface RefreshDataLoader extends AutoCloseable {
    RateSnapshot loadRates(long refreshId) throws Exception;

    @Override
    void close();
}
