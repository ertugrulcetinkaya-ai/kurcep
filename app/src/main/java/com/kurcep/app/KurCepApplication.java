package com.kurcep.app;

import android.app.Application;

public final class KurCepApplication extends Application {
    private final RefreshCoordinator refreshCoordinator =
            new RefreshCoordinator(RateRepository.production());

    RefreshCoordinator getRefreshCoordinator() {
        return refreshCoordinator;
    }
}
