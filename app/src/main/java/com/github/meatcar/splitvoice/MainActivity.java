package com.github.meatcar.splitvoice;

import android.Manifest;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String SHIZUKU = "moe.shizuku.privileged.api";
    private static final int NOTIFICATION_PERMISSION = 1;
    private static final int CONTENT_MAX_WIDTH_DP = 600;

    private RouteController routes;
    private AudioWatch watch;
    private RouteController.State settled = RouteController.State.UNAVAILABLE;
    private final Runnable listener = this::render;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        routes = ((SplitVoiceApplication) getApplication()).routes();
        watch = new AudioWatch(this, routes::refresh);
        getWindow().setDecorFitsSystemWindows(false);
        setContentView(R.layout.main);
        findViewById(R.id.scroll).setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int gutter = Math.max(dp(16), dp(getResources().getConfiguration().screenWidthDp - CONTENT_MAX_WIDTH_DP) / 2);
            view.setPadding(bars.left + gutter, bars.top, bars.right + gutter, bars.bottom);
            return insets;
        });

        findViewById(R.id.primary).setOnClickListener(view -> routes.toggle());
        step(R.id.step_install, 1, R.string.setup_install, R.string.setup_install_body,
                R.string.setup_install_action, view -> open(R.string.url_shizuku_download));
        step(R.id.step_start, 2, R.string.setup_start, R.string.setup_start_body,
                R.string.setup_start_action, view -> openShizuku());
        step(R.id.step_connect, 3, R.string.setup_connect, R.string.setup_connect_body,
                R.string.setup_connect, view -> routes.connect(true));
        label(R.id.check_receiver, R.string.need_receiver);
        label(R.id.check_headphones, R.string.need_headphones);
        label(R.id.check_voice_chat, R.string.need_voice_chat);

        CompoundButton automatic = findViewById(R.id.auto_route);
        automatic.setOnClickListener(view -> {
            AutoRoute.setEnabled(this, automatic.isChecked());
            AutoRouteService.sync(this);
            render();
        });
        CompoundButton notifications = findViewById(R.id.notifications);
        notifications.setText(twoLine(R.string.notifications, getString(R.string.notifications_summary)));
        notifications.setOnClickListener(view -> {
            if (notifications.isChecked() && Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifications.setChecked(false);
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
            } else RecoveryNotification.setEnabled(this, notifications.isChecked(), routes);
        });
        findViewById(R.id.add_tile).setOnClickListener(view -> addTile());

        link(R.id.how_it_works, R.string.how_it_works, R.string.how_it_works_summary, view -> open(R.string.url_how_it_works));
        link(R.id.troubleshooting, R.string.troubleshooting, R.string.troubleshooting_summary,
                view -> open(R.string.url_troubleshooting));
        link(R.id.diagnostics, R.string.diagnostics, R.string.diagnostics_summary, view -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.diagnostics), Diagnostics.report(this, routes)));
            Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show();
        });
        link(R.id.report_problem, R.string.report_problem, R.string.report_problem_summary,
                view -> open(R.string.url_report_problem));
    }

    @Override protected void onStart() {
        super.onStart();
        routes.observe(listener);
        routes.refresh();
        watch.start();
        AutoRouteService.sync(this);
    }

    @Override protected void onStop() {
        watch.stop();
        routes.removeObserver(listener);
        super.onStop();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != NOTIFICATION_PERMISSION) return;
        RecoveryNotification.setEnabled(this, results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED, routes);
        render();
    }

    private void render() {
        RouteController.State state = routes.state();
        boolean working = state == RouteController.State.WORKING;
        if (!working) settled = state;
        boolean connected = routes.connected();
        boolean pending = routes.pending();

        renderStatus(settled, working);
        ((TextView) findViewById(R.id.status)).setText(routes.message());
        findViewById(R.id.setup).setVisibility(connected ? View.GONE : View.VISIBLE);
        if (!connected) renderSetup();
        Bundle inspection = routes.inspection();
        findViewById(R.id.checks).setVisibility(connected && inspection != null ? View.VISIBLE : View.GONE);
        if (connected && inspection != null) renderChecks(inspection);

        Button primary = findViewById(R.id.primary);
        boolean canSplit = settled == RouteController.State.READY;
        primary.setVisibility(pending || canSplit ? View.VISIBLE : View.GONE);
        primary.setText(pending ? R.string.restore : R.string.split);
        primary.setEnabled(!routes.busy() && connected);

        boolean automatic = AutoRoute.enabled(this);
        CompoundButton auto = findViewById(R.id.auto_route);
        auto.setChecked(automatic);
        auto.setText(twoLine(R.string.auto_route, getString(automatic && !connected
                ? R.string.auto_route_paused : R.string.auto_route_summary)));
        ((CompoundButton) findViewById(R.id.notifications)).setChecked(RecoveryNotification.enabled(this));
        boolean tileAdded = SplitTileService.added(this);
        ((TextView) findViewById(R.id.tile_text)).setText(twoLine(R.string.tile,
                getString(tileAdded ? R.string.tile_summary_added : R.string.tile_summary)));
        findViewById(R.id.add_tile).setVisibility(tileAdded ? View.GONE : View.VISIBLE);
    }

    private void renderStatus(RouteController.State state, boolean working) {
        int container;
        int content;
        int iconBackground;
        int icon;
        switch (state) {
            case ACTIVE -> {
                container = R.color.primary_container; content = R.color.on_primary_container;
                iconBackground = R.color.primary; icon = R.drawable.ic_check;
            }
            case RECOVERY -> {
                container = R.color.error_container; content = R.color.on_error_container;
                iconBackground = R.color.error; icon = R.drawable.ic_error;
            }
            case BLOCKED -> {
                container = R.color.surface_container; content = R.color.on_surface;
                iconBackground = R.color.secondary_container; icon = R.drawable.ic_info;
            }
            default -> {
                container = R.color.surface_container; content = R.color.on_surface;
                iconBackground = R.color.secondary_container; icon = R.drawable.ic_split_voice;
            }
        }
        findViewById(R.id.status_card).setBackgroundTintList(getColorStateList(container));
        ImageView image = findViewById(R.id.status_icon);
        image.setImageResource(icon);
        image.setBackgroundTintList(getColorStateList(iconBackground));
        image.setImageTintList(getColorStateList(state == RouteController.State.ACTIVE ? R.color.on_primary
                : state == RouteController.State.RECOVERY ? R.color.error_container : R.color.on_secondary_container));
        image.setVisibility(working ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.status_progress).setVisibility(working ? View.VISIBLE : View.GONE);
        TextView title = findViewById(R.id.status_title);
        title.setText(RouteController.title(state));
        title.setTextColor(getColor(content));
        ((TextView) findViewById(R.id.status)).setTextColor(getColor(content));
        for (int id : new int[]{R.id.check_receiver, R.id.check_headphones, R.id.check_voice_chat}) {
            ((TextView) findViewById(id).findViewById(R.id.check_label)).setTextColor(getColor(content));
        }
    }

    private void renderSetup() {
        boolean installed = getPackageManager().getLaunchIntentForPackage(SHIZUKU) != null;
        boolean running = routes.shizukuRunning();
        renderStep(R.id.step_install, installed, !installed);
        renderStep(R.id.step_start, running, installed && !running);
        renderStep(R.id.step_connect, false, running);
        findViewById(R.id.step_connect).findViewById(R.id.step_action).setEnabled(!routes.busy());
    }

    private void renderChecks(Bundle inspection) {
        renderDevice(R.id.check_receiver, count(inspection.getIntArray("inputTypes"), AudioDeviceInfo.TYPE_USB_DEVICE));
        renderDevice(R.id.check_headphones, count(inspection.getIntArray("outputTypes"), AudioDeviceInfo.TYPE_BLE_HEADSET));
        boolean voiceChat = inspection.getInt("mode", -1) == AudioManager.MODE_IN_COMMUNICATION;
        renderCheck(R.id.check_voice_chat, getString(voiceChat ? R.string.voice_chat_on : R.string.voice_chat_off),
                voiceChat ? Check.OK : Check.MISSING);
    }

    private enum Check { OK, MISSING, PROBLEM }

    private void renderDevice(int row, int count) {
        if (count == 1) renderCheck(row, getString(R.string.device_found), Check.OK);
        else if (count == 0) renderCheck(row, getString(R.string.device_missing), Check.MISSING);
        else renderCheck(row, getString(R.string.device_extra, count), Check.PROBLEM);
    }

    private void renderCheck(int row, String value, Check check) {
        View root = findViewById(row);
        int color = switch (check) {
            case OK -> R.color.primary;
            case MISSING -> R.color.on_surface_variant;
            case PROBLEM -> R.color.error;
        };
        TextView text = root.findViewById(R.id.check_value);
        text.setText(value);
        text.setTextColor(getColor(color));
        ImageView icon = root.findViewById(R.id.check_icon);
        icon.setImageResource(switch (check) {
            case OK -> R.drawable.ic_check_circle;
            case MISSING -> R.drawable.ic_pending;
            case PROBLEM -> R.drawable.ic_error;
        });
        icon.setImageTintList(getColorStateList(color));
    }

    private void renderStep(int step, boolean done, boolean current) {
        View root = findViewById(step);
        TextView number = root.findViewById(R.id.step_number);
        number.setBackgroundResource(current ? R.drawable.circle : R.drawable.step_pending);
        number.setBackgroundTintList(current ? getColorStateList(R.color.primary) : null);
        number.setTextColor(getColor(current ? R.color.on_primary : R.color.on_surface_variant));
        number.setVisibility(done ? View.INVISIBLE : View.VISIBLE);
        root.findViewById(R.id.step_done).setVisibility(done ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.step_body).setVisibility(done ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.step_action).setVisibility(current ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.step_title).setStateDescription(getString(done ? R.string.step_done : R.string.step_todo));
    }

    private void step(int step, int number, int title, int body, int action, View.OnClickListener onClick) {
        View root = findViewById(step);
        ((TextView) root.findViewById(R.id.step_number)).setText(String.valueOf(number));
        ((TextView) root.findViewById(R.id.step_title)).setText(title);
        ((TextView) root.findViewById(R.id.step_body)).setText(body);
        Button button = root.findViewById(R.id.step_action);
        button.setText(action);
        button.setOnClickListener(onClick);
    }

    private void label(int row, int label) {
        ((TextView) findViewById(row).findViewById(R.id.check_label)).setText(label);
    }

    private void link(int id, int title, int summary, View.OnClickListener onClick) {
        TextView row = findViewById(id);
        row.setText(twoLine(title, getString(summary)));
        row.setOnClickListener(onClick);
    }

    private CharSequence twoLine(int title, CharSequence summary) {
        SpannableStringBuilder text = new SpannableStringBuilder(getString(title)).append('\n');
        int start = text.length();
        text.append(summary);
        text.setSpan(new RelativeSizeSpan(0.875f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(getColor(R.color.on_surface_variant)), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private static int count(int[] values, int wanted) {
        int count = 0;
        if (values != null) for (int value : values) if (value == wanted) count++;
        return count;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void open(int url) { start(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(url)))); }

    private void openShizuku() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(SHIZUKU);
        if (intent != null) start(intent);
        else open(R.string.url_shizuku_download);
    }

    private void start(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show(); }
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, R.string.tile_manual, Toast.LENGTH_LONG).show();
            return;
        }
        getSystemService(StatusBarManager.class).requestAddTileService(
                new ComponentName(this, SplitTileService.class), getString(R.string.app_name),
                Icon.createWithResource(this, R.drawable.ic_split_voice), getMainExecutor(), result -> {
                    boolean added = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                            || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED;
                    if (added) SplitTileService.setAdded(this, true);
                    Toast.makeText(this, added ? R.string.tile_added : R.string.tile_declined, Toast.LENGTH_LONG).show();
                    render();
                });
    }
}
