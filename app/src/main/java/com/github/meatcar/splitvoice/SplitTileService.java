package com.github.meatcar.splitvoice;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public final class SplitTileService extends TileService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private RouteController routes;
    private boolean listening;
    private boolean awaitingFeedback;
    private final Runnable listener = this::update;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!listening) return;
            routes.refresh();
            main.postDelayed(this, 3000);
        }
    };

    static boolean added(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("tile", false);
    }

    static void setAdded(Context context, boolean added) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("tile", added).apply();
    }

    @Override public void onCreate() {
        super.onCreate();
        routes = ((SplitVoiceApplication) getApplication()).routes();
    }

    @Override public void onTileAdded() {
        super.onTileAdded();
        setAdded(this, true);
    }

    @Override public void onTileRemoved() {
        setAdded(this, false);
        super.onTileRemoved();
    }

    @Override public void onStartListening() {
        super.onStartListening();
        if (listening) return;
        listening = true;
        setAdded(this, true);
        poll.run();
        routes.observe(listener);
    }

    @Override public void onStopListening() {
        listening = false;
        awaitingFeedback = false;
        main.removeCallbacks(poll);
        routes.removeObserver(listener);
        super.onStopListening();
    }

    @Override public void onDestroy() {
        onStopListening();
        super.onDestroy();
    }

    @Override public void onClick() {
        super.onClick();
        if (isLocked()) unlockAndRun(this::act);
        else act();
    }

    private void act() {
        switch (routes.state()) {
            case WORKING -> { }
            case READY, ACTIVE -> toggle();
            case RECOVERY -> { if (routes.connected()) toggle(); else openApp(); }
            case UNAVAILABLE, BLOCKED -> openApp();
        }
    }

    private void toggle() {
        awaitingFeedback = true;
        routes.toggle();
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        } else startActivityAndCollapse(intent);
    }

    private void update() {
        if (!listening) return;
        Tile tile = getQsTile();
        if (tile == null) return;
        RouteController.State state = routes.state();
        tile.setState(switch (state) {
            case ACTIVE -> Tile.STATE_ACTIVE;
            case WORKING -> Tile.STATE_UNAVAILABLE;
            default -> Tile.STATE_INACTIVE;
        });
        String subtitle = getString(switch (state) {
            case UNAVAILABLE -> R.string.tile_state_setup;
            case WORKING -> R.string.tile_state_working;
            case READY -> R.string.tile_state_off;
            case ACTIVE -> R.string.tile_state_on;
            case RECOVERY -> R.string.tile_state_restore;
            case BLOCKED -> R.string.tile_state_blocked;
        });
        tile.setLabel(getString(R.string.app_name));
        tile.setSubtitle(subtitle);
        tile.setStateDescription(subtitle + ". " + routes.message());
        tile.updateTile();
        if (awaitingFeedback && !routes.busy()) {
            awaitingFeedback = false;
            Toast.makeText(this, routes.message(), Toast.LENGTH_LONG).show();
        }
    }
}
