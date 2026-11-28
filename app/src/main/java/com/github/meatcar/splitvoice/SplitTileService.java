package com.github.meatcar.splitvoice;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class SplitTileService extends TileService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private RouteController routes;
    private boolean listening;
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
        if (routes.state() == RouteController.State.CALL) routes.pickForCall(routes.nextChoice());
        else openApp();
    }

    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
    }

    private void update() {
        if (!listening) return;
        Tile tile = getQsTile();
        if (tile == null) return;
        RouteController.State state = routes.state();
        RouteController.Status status = routes.status();
        boolean split = state == RouteController.State.CALL && status.split();
        tile.setState(split ? Tile.STATE_ACTIVE
                : state == RouteController.State.WORKING ? Tile.STATE_UNAVAILABLE : Tile.STATE_INACTIVE);
        String subtitle = split ? routes.label(status.choice()) : switch (state) {
            case SETUP -> getString(R.string.tile_state_setup);
            case ERROR -> getString(R.string.tile_state_blocked);
            default -> routes.headline();
        };
        tile.setLabel(getString(R.string.app_name));
        tile.setSubtitle(subtitle);
        tile.setStateDescription(subtitle + ". " + routes.detail());
        tile.updateTile();
    }
}
