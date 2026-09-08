package com.github.meatcar.splitvoice;

import android.Manifest;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private RouteController routes;
    private final Runnable listener = this::refresh;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        routes = ((SplitVoiceApplication) getApplication()).routes();
        setContentView(R.layout.main);
        findViewById(R.id.scroll).setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        findViewById(R.id.connect).setOnClickListener(view -> {
            if (routes.shizukuRunning()) routes.connect(true);
            else openShizuku();
        });
        findViewById(R.id.check).setOnClickListener(view -> routes.refresh());
        findViewById(R.id.restore).setOnClickListener(view -> routes.restore());
        findViewById(R.id.add_tile).setOnClickListener(view -> addTile());
        CompoundButton notifications = findViewById(R.id.notifications);
        notifications.setOnClickListener(view -> {
            if (notifications.isChecked() && Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifications.setChecked(false);
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
            } else RecoveryNotification.setEnabled(this, notifications.isChecked(), routes);
        });
    }

    @Override protected void onStart() {
        super.onStart();
        routes.observe(listener);
        routes.refresh();
    }

    @Override protected void onStop() {
        routes.removeObserver(listener);
        super.onStop();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 2) {
            RecoveryNotification.setEnabled(this, results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED, routes);
            refresh();
        }
    }

    private void refresh() {
        ((TextView) findViewById(R.id.status_title)).setText(routes.title());
        ((TextView) findViewById(R.id.status)).setText(routes.message());
        ((TextView) findViewById(R.id.connect)).setText(routes.shizukuRunning() ? R.string.connect : R.string.open_shizuku);
        findViewById(R.id.connect).setVisibility(routes.connected() ? View.GONE : View.VISIBLE);
        findViewById(R.id.connect).setEnabled(!routes.busy() && !routes.connected());
        findViewById(R.id.check).setVisibility(routes.connected() ? View.VISIBLE : View.GONE);
        findViewById(R.id.check).setEnabled(!routes.busy() && routes.connected());
        findViewById(R.id.restore).setVisibility(routes.pending() ? View.VISIBLE : View.GONE);
        findViewById(R.id.restore).setEnabled(!routes.busy() && routes.connected() && routes.pending());
        ((CompoundButton) findViewById(R.id.notifications)).setChecked(RecoveryNotification.enabled(this));
    }

    private void openShizuku() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (intent == null) intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/"));
        try { startActivity(intent); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Install Shizuku from shizuku.rikka.app", Toast.LENGTH_LONG).show();
        }
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager.class).requestAddTileService(
                    new ComponentName(this, SplitTileService.class), getString(R.string.app_name),
                    Icon.createWithResource(this, R.drawable.ic_split_voice), getMainExecutor(), result -> {
                        boolean added = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                                || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED;
                        Toast.makeText(this, added ? R.string.tile_added : R.string.tile_declined, Toast.LENGTH_LONG).show();
                    });
        } else Toast.makeText(this, R.string.tile_manual, Toast.LENGTH_LONG).show();
    }
}
