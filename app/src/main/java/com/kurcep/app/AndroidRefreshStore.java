package com.kurcep.app;

import android.content.Context;

final class AndroidRefreshStore implements RefreshStore {
    private final Context context;

    AndroidRefreshStore(Context context) {
        this.context = context;
    }

    @Override
    public boolean save(RateSnapshot data) {
        return RateStore.save(context, data);
    }

    @Override
    public RateSnapshot load() {
        return RateStore.load(context);
    }
}
