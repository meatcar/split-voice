package com.github.meatcar.splitvoice;

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

    @Override public void onCreate() {
        super.onCreate();
        routes = ((SplitVoiceApplication) getApplication()).routes();
    }

    @Override public void onStartListening() {
        super.onStartListening();
        if (listening) return;
        listening = true;
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
        if (isLocked()) {
            unlockAndRun(this::toggle);
        } else toggle();
    }

    private void toggle() {
        if (routes.busy()) return;
        if (!routes.connected()) {
            Toast.makeText(this, "Long-press Split Voice for Shizuku setup and recovery.", Toast.LENGTH_LONG).show();
            return;
        }
        awaitingFeedback = true;
        routes.toggle();
    }

    private void update() {
        if (!listening) return;
        Tile tile = getQsTile();
        if (tile == null) return;
        RouteController.State state = routes.state();
        tile.setState(switch (state) {
            case ACTIVE -> Tile.STATE_ACTIVE;
            case READY -> Tile.STATE_INACTIVE;
            case RECOVERY -> routes.connected() ? Tile.STATE_INACTIVE : Tile.STATE_UNAVAILABLE;
            default -> Tile.STATE_UNAVAILABLE;
        });
        tile.setLabel(getString(R.string.app_name));
        tile.setSubtitle(routes.title());
        tile.setContentDescription(getString(R.string.app_name) + ". " + routes.title() + ". " + routes.message());
        tile.setStateDescription(routes.message());
        tile.updateTile();
        if (awaitingFeedback && !routes.busy()) {
            awaitingFeedback = false;
            Toast.makeText(this, routes.message(), Toast.LENGTH_LONG).show();
        }
    }
}
