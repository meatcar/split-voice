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
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class MainActivity extends Activity {
    private static final String SHIZUKU = "moe.shizuku.privileged.api";
    private static final int NOTIFICATION_PERMISSION = 1;

    private RouteController routes;
    private AudioWatch watch;
    private RouteController.State settled = RouteController.State.SETUP;
    private String headline = "";
    private List<String> chipChoices = List.of();
    private final Runnable listener = this::render;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        routes = ((SplitVoiceApplication) getApplication()).routes();
        watch = new AudioWatch(this, routes::refresh);
        setContentView(R.layout.main);
        Ui.edgeToEdge(this);

        step(R.id.step_install, 1, R.string.setup_install, R.string.setup_install_body,
                R.string.setup_install_action, view -> open(R.string.url_shizuku_download));
        step(R.id.step_start, 2, R.string.setup_start, R.string.setup_start_body,
                R.string.setup_start_action, view -> openShizuku());
        step(R.id.step_connect, 3, R.string.setup_connect, R.string.setup_connect_body,
                R.string.setup_connect, view -> routes.connect(true));
        ((ImageView) findViewById(R.id.route_mic).findViewById(R.id.route_icon)).setImageResource(R.drawable.ic_mic);
        ((ImageView) findViewById(R.id.route_headphones).findViewById(R.id.route_icon))
                .setImageResource(R.drawable.ic_headphones);
        findViewById(R.id.allow_notifications).setOnClickListener(view ->
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION));
        findViewById(R.id.advanced).setOnClickListener(view -> startActivity(new Intent(this, AdvancedActivity.class)));
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
        AutoRouteService.sync(this, routes);
    }

    @Override protected void onStop() {
        watch.stop();
        routes.removeObserver(listener);
        super.onStop();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != NOTIFICATION_PERMISSION) return;
        AutoRouteService.sync(this, routes);
        render();
    }

    private void render() {
        RouteController.State state = routes.state();
        boolean working = state == RouteController.State.WORKING;
        if (!working) settled = state;
        boolean call = settled == RouteController.State.CALL && routes.status() != null;
        boolean split = call && routes.status().split();

        renderStatus(settled, split, working);
        if (!working || headline.isEmpty()) headline = routes.headline();
        ((TextView) findViewById(R.id.status_title)).setText(headline);
        ((TextView) findViewById(R.id.status)).setText(routes.detail());
        findViewById(R.id.setup).setVisibility(routes.connected() ? View.GONE : View.VISIBLE);
        if (!routes.connected()) renderSetup();
        findViewById(R.id.routes).setVisibility(call ? View.VISIBLE : View.GONE);
        findViewById(R.id.chips_scroll).setVisibility(call ? View.VISIBLE : View.GONE);
        if (call) {
            renderRoute(R.id.route_mic, routes.line(true), R.string.route_mic);
            renderRoute(R.id.route_headphones, routes.line(false), R.string.route_headphones);
            renderChips(routes.status().choice());
        }

        boolean notifications = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        ((TextView) findViewById(R.id.notifications_text)).setText(Ui.twoLine(this, getString(R.string.notifications),
                getString(notifications ? R.string.notifications_allowed : R.string.notifications_summary)));
        findViewById(R.id.allow_notifications).setVisibility(notifications ? View.GONE : View.VISIBLE);
        boolean tileAdded = SplitTileService.added(this);
        ((TextView) findViewById(R.id.tile_text)).setText(Ui.twoLine(this, getString(R.string.tile),
                getString(tileAdded ? R.string.tile_summary_added : R.string.tile_summary)));
        findViewById(R.id.add_tile).setVisibility(tileAdded ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.advanced)).setText(Ui.twoLine(this, getString(R.string.advanced), advancedSummary()));
    }

    private String advancedSummary() {
        Settings settings = routes.settings();
        int profiles = settings.profiles().size();
        int overrides = settings.overrides().size();
        if (profiles == 0 && overrides == 0 && Settings.AUTO.equals(settings.allApps())) {
            return getString(R.string.advanced_summary);
        }
        return getString(R.string.advanced_counts, routes.label(settings.allApps()),
                getResources().getQuantityString(R.plurals.profiles, profiles, profiles),
                getResources().getQuantityString(R.plurals.overrides, overrides, overrides));
    }

    private void renderRoute(int row, RouteController.Line line, int kind) {
        View root = findViewById(row);
        ((TextView) root.findViewById(R.id.route_device)).setText(line.device());
        TextView why = root.findViewById(R.id.route_why);
        why.setText(line.why());
        why.setVisibility(line.why() == null ? View.GONE : View.VISIBLE);
        root.setContentDescription(getString(kind) + ": " + line.device() + (line.why() == null ? "" : ". " + line.why()));
    }

    private void renderChips(String current) {
        RadioGroup group = findViewById(R.id.chips);
        List<String> choices = routes.choices();
        if (!choices.equals(chipChoices)) {
            chipChoices = choices;
            group.removeAllViews();
            for (String choice : choices) {
                RadioButton chip = (RadioButton) getLayoutInflater().inflate(R.layout.chip, group, false);
                chip.setId(View.generateViewId());
                chip.setTag(choice);
                chip.setOnClickListener(view -> routes.pickForCall(choice));
                group.addView(chip);
            }
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            RadioButton chip = (RadioButton) group.getChildAt(i);
            chip.setText(routes.label((String) chip.getTag()));
            if (chip.getTag().equals(current)) group.check(chip.getId());
        }
    }

    private void renderStatus(RouteController.State state, boolean split, boolean working) {
        int container = R.color.surface_container;
        int content = R.color.on_surface;
        int iconBackground = R.color.secondary_container;
        int iconTint = R.color.on_secondary_container;
        int icon = R.drawable.ic_split_voice;
        if (split) {
            container = R.color.primary_container; content = R.color.on_primary_container;
            iconBackground = R.color.primary; iconTint = R.color.on_primary; icon = R.drawable.ic_check;
        } else if (state == RouteController.State.RECOVERY) {
            container = R.color.error_container; content = R.color.on_error_container;
            iconBackground = R.color.error; iconTint = R.color.error_container; icon = R.drawable.ic_error;
        } else if (state == RouteController.State.ERROR) {
            icon = R.drawable.ic_info;
        }
        findViewById(R.id.status_card).setBackgroundTintList(getColorStateList(container));
        ImageView image = findViewById(R.id.status_icon);
        image.setImageResource(icon);
        image.setBackgroundTintList(getColorStateList(iconBackground));
        image.setImageTintList(getColorStateList(iconTint));
        image.setVisibility(working ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.status_progress).setVisibility(working ? View.VISIBLE : View.GONE);
        for (int id : new int[]{R.id.status_title, R.id.status}) ((TextView) findViewById(id)).setTextColor(getColor(content));
        for (int row : new int[]{R.id.route_mic, R.id.route_headphones}) {
            View root = findViewById(row);
            ((TextView) root.findViewById(R.id.route_device)).setTextColor(getColor(content));
            ((TextView) root.findViewById(R.id.route_why)).setTextColor(getColor(content));
            ((ImageView) root.findViewById(R.id.route_icon)).setImageTintList(getColorStateList(content));
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

    private void link(int id, int title, int summary, View.OnClickListener onClick) {
        TextView row = findViewById(id);
        row.setText(Ui.twoLine(this, getString(title), getString(summary)));
        row.setOnClickListener(onClick);
    }

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
