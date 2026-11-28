package com.github.meatcar.splitvoice;

import android.app.Application;

public final class SplitVoiceApplication extends Application {
    private RouteController routes;

    @Override public void onCreate() {
        super.onCreate();
        routes = new RouteController(this);
    }

    RouteController routes() { return routes; }
}
