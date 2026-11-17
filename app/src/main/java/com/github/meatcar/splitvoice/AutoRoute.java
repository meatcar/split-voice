package com.github.meatcar.splitvoice;

import android.content.Context;

// NOTE: once per voice session, never retried; actions go through the controller so safety checks apply
final class AutoRoute {
    enum Action { NONE, APPLY, RESTORE }

    private static final int MODE_NORMAL = 0;
    private static final int MODE_VOICE = 3;

    private final RouteController routes;
    private final Runnable listener = this::evaluate;
    private boolean applyAttempted;
    private boolean restoreAttempted;

    AutoRoute(RouteController routes) { this.routes = routes; }

    static boolean enabled(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("auto", false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("auto", enabled).apply();
    }

    void start() { routes.observe(listener); }
    void stop() { routes.removeObserver(listener); }

    private void evaluate() {
        Action action = next(routes.state(), routes.mode(), routes.pending());
        if (action == Action.APPLY) routes.toggle();
        else if (action == Action.RESTORE) routes.restore();
    }

    Action next(RouteController.State state, int mode, boolean pending) {
        if (state == RouteController.State.WORKING) return Action.NONE;
        if (mode == MODE_VOICE) {
            restoreAttempted = false;
            if (state != RouteController.State.READY || applyAttempted) return Action.NONE;
            applyAttempted = true;
            return Action.APPLY;
        }
        applyAttempted = false;
        if (mode != MODE_NORMAL || !pending || !routes.connected() || restoreAttempted) return Action.NONE;
        restoreAttempted = true;
        return Action.RESTORE;
    }
}
