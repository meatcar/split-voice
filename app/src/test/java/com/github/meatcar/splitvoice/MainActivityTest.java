package com.github.meatcar.splitvoice;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.util.TypedValue;
import android.os.Bundle;
import android.os.Looper;
import android.Manifest;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 35}, qualifiers = "w360dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityTest {
    @Test public void firstLaunchWalksThroughSetupOneStepAtATime() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertEquals("Finish setup", text(activity, R.id.status_title));
            assertEquals("Start Shizuku to continue.", text(activity, R.id.status));
            assertEquals(View.VISIBLE, activity.findViewById(R.id.setup).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.checks).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.primary).getVisibility());
            assertEquals(View.VISIBLE, actions(activity, R.id.step_install).getVisibility());
            assertEquals(View.GONE, actions(activity, R.id.step_start).getVisibility());
            assertEquals(View.GONE, actions(activity, R.id.step_connect).getVisibility());
            activity.findViewById(R.id.step_install).findViewById(R.id.step_action).performClick();
            assertEquals("https://shizuku.rikka.app/download/",
                    shadowOf(activity).getNextStartedActivity().getDataString());
            capture(activity, "setup");
        }
    }

    @Test public void installedShizukuMovesSetupToStartingIt() throws Exception {
        var app = RuntimeEnvironment.getApplication();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage("moe.shizuku.privileged.api");
        shadowOf(app.getPackageManager()).addResolveInfoForIntent(launcher, resolveInfo("moe.shizuku.privileged.api"));
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.step_install).findViewById(R.id.step_done).getVisibility());
            assertEquals(View.GONE, actions(activity, R.id.step_install).getVisibility());
            assertEquals(View.VISIBLE, actions(activity, R.id.step_start).getVisibility());
            activity.findViewById(R.id.step_start).findViewById(R.id.step_action).performClick();
            assertEquals("moe.shizuku.privileged.api", shadowOf(activity).getNextStartedActivity().getPackage());
            capture(activity, "setup-installed");
        }
    }

    @Test public void recoverySurvivesRecreationAndNeverClaimsActiveFromJournal() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        assertTrue(context.getSharedPreferences("routing-recovery", Context.MODE_PRIVATE)
                .edit().putBoolean("pending", true).putString("receiver", "test receiver")
                .putString("earbuds", "test earbuds").commit());
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            controller.recreate();
            MainActivity activity = controller.get();
            assertEquals("Restore needed", text(activity, R.id.status_title));
            assertEquals("Start Shizuku, then tap Restore.", text(activity, R.id.status));
            assertEquals(View.VISIBLE, activity.findViewById(R.id.setup).getVisibility());
            assertEquals("Restore", text(activity, R.id.primary));
            assertFalse(activity.findViewById(R.id.primary).isEnabled());
            capture(activity, "recovery");
        }
    }

    @Test public void connectedScreenShowsWhatIsReadyAndOneAction() throws Exception {
        for (boolean active : new boolean[]{false, true}) {
            var journal = RuntimeEnvironment.getApplication().getSharedPreferences("routing-recovery", Context.MODE_PRIVATE);
            assertTrue(journal.edit().putBoolean("pending", active).commit());
            Bundle inspection = inspection(new int[]{11, 15}, new int[]{2, 26}, 3);
            inspection.putBoolean("empty", !active);
            inspection.putBoolean("matches", active);
            inspection.putBoolean("devicesReady", true);
            try (var controller = launchConnected(inspection)) {
                MainActivity activity = controller.get();
                assertEquals(active ? "Split is on" : "Ready to split", text(activity, R.id.status_title));
                assertEquals(View.GONE, activity.findViewById(R.id.setup).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.checks).getVisibility());
                assertEquals("Connected", check(activity, R.id.check_receiver));
                assertEquals("Connected", check(activity, R.id.check_headphones));
                assertEquals("In progress", check(activity, R.id.check_voice_chat));
                assertEquals(active ? "Restore" : "Split", text(activity, R.id.primary));
                assertTrue(activity.findViewById(R.id.primary).isEnabled());
                capture(activity, active ? "active" : "ready");
            }
        }
    }

    @Test public void blockedScreenNamesTheMissingAndExtraDevices() throws Exception {
        Bundle inspection = inspection(new int[]{15}, new int[]{2, 26, 26}, 0);
        inspection.putBoolean("empty", true);
        try (var controller = launchConnected(inspection)) {
            MainActivity activity = controller.get();
            assertEquals("Not ready", text(activity, R.id.status_title));
            assertEquals("Connect one mic receiver and one pair of LE Audio headphones.", text(activity, R.id.status));
            assertEquals("Not connected", check(activity, R.id.check_receiver));
            assertEquals("2 connected", check(activity, R.id.check_headphones));
            assertEquals("Not started", check(activity, R.id.check_voice_chat));
            assertEquals(View.GONE, activity.findViewById(R.id.primary).getVisibility());
            capture(activity, "blocked");
        }
    }

    @Test public void splitButtonAppliesThroughTheController() throws Exception {
        var app = (SplitVoiceApplication) RuntimeEnvironment.getApplication();
        FakeRouteService remote = new FakeRouteService(app.getSharedPreferences("routing-recovery", Context.MODE_PRIVATE));
        ReflectionHelpers.setField(app.routes(), "service", remote);
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            FakeRouteService.awaitIdle(app.routes());
            MainActivity activity = controller.get();
            assertEquals("Split", text(activity, R.id.primary));
            activity.findViewById(R.id.primary).performClick();
            FakeRouteService.awaitIdle(app.routes());
            assertEquals(1, remote.applyCalls);
            assertEquals("Split is on", text(activity, R.id.status_title));
            assertEquals("Restore", text(activity, R.id.primary));
        }
    }

    @Test public void diagnosticsDescribeTheSetupWithoutDeviceIdentifiers() throws Exception {
        RouteController routes = ((SplitVoiceApplication) RuntimeEnvironment.getApplication()).routes();
        Bundle inspection = new Bundle();
        inspection.putInt("mode", 3);
        inspection.putBoolean("empty", true);
        inspection.putBoolean("devicesReady", true);
        inspection.putString("receiver", "AA:BB:CC:DD:EE:FF");
        inspection.putString("earbuds", "11:22:33:44:55:66");
        inspection.putIntArray("inputTypes", new int[]{3, 11, 15});
        inspection.putIntArray("outputTypes", new int[]{2, 26, 99});
        ReflectionHelpers.setField(routes, "inspection", inspection);
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            controller.get().findViewById(R.id.diagnostics).performClick();
            ClipboardManager clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager.class);
            String report = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
            assertTrue(report, report.contains("Android: "));
            assertTrue(report, report.contains("Shizuku: not running"));
            assertTrue(report, report.contains("Input types: wired headset, USB device, built-in mic"));
            assertTrue(report, report.contains("Output types: built-in speaker, LE headset, type 99"));
            assertTrue(report, report.contains("Audio mode: 3"));
            assertFalse(report, report.contains("AA:BB"));
            assertFalse(report, report.contains("11:22"));
        }
    }

    @Config(sdk = 31) @Test public void androidTwelveExplainsManualTileSetupAndNeedsNoNotificationPermission() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.add_tile).performClick();
            assertEquals("Open Quick Settings, tap Edit, and drag Split Voice in.", ShadowToast.getTextOfLatestToast());
            CompoundButton notifications = activity.findViewById(R.id.notifications);
            notifications.performClick();
            assertTrue(notifications.isChecked());
            assertTrue(RecoveryNotification.enabled(activity));
            assertNull(shadowOf(activity).getLastRequestedPermission());
        }
    }

    @Config(sdk = 35) @Test public void androidThirteenAsksForNotificationPermissionBeforeEnablingReminder() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            CompoundButton notifications = activity.findViewById(R.id.notifications);
            notifications.performClick();
            assertFalse(notifications.isChecked());
            assertFalse(RecoveryNotification.enabled(activity));
            assertEquals(Manifest.permission.POST_NOTIFICATIONS,
                    shadowOf(activity).getLastRequestedPermission().requestedPermissions[0]);
        }
    }

    @Test public void lightThemeUsesDarkTextOnLightBackground() throws Exception {
        assertReadableTheme(false);
    }

    @Config(qualifiers = "w360dp-h720dp-night-mdpi") @Test public void darkThemeUsesLightTextOnDarkBackground() throws Exception {
        assertReadableTheme(true);
    }

    @Config(sdk = 35) @Test public void launcherIconIsAdaptiveAndThemeable() throws Exception {
        var app = RuntimeEnvironment.getApplication();
        assertEquals(R.mipmap.ic_launcher, app.getApplicationInfo().icon);
        var icon = app.getDrawable(R.mipmap.ic_launcher);
        assertTrue(icon instanceof AdaptiveIconDrawable);
        assertNotNull(((AdaptiveIconDrawable) icon).getMonochrome());
    }

    @Test public void automaticSplitSwitchPersistsAndControlsTheService() {
        var app = shadowOf(RuntimeEnvironment.getApplication());
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            while (app.getNextStartedService() != null || app.getNextStoppedService() != null) { /* drain launch sync */ }
            CompoundButton automatic = controller.get().findViewById(R.id.auto_route);
            assertFalse(automatic.isChecked());
            automatic.performClick();
            assertTrue(AutoRoute.enabled(controller.get()));
            assertTrue(automatic.getText().toString().contains("Paused until Split Voice connects to Shizuku."));
            assertEquals(AutoRouteService.class.getName(), app.getNextStartedService().getComponent().getClassName());
            automatic.performClick();
            assertFalse(AutoRoute.enabled(controller.get()));
            assertEquals(AutoRouteService.class.getName(), app.getNextStoppedService().getComponent().getClassName());
        }
    }

    private static void assertReadableTheme(boolean dark) throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            var background = new TypedValue();
            assertTrue(activity.getTheme().resolveAttribute(android.R.attr.colorBackground, background, true));
            double backgroundLuminance = Color.luminance(background.data);
            double textLuminance = Color.luminance(((TextView) activity.findViewById(R.id.status_title)).getCurrentTextColor());
            assertEquals(dark, backgroundLuminance < 0.2);
            assertEquals(dark, textLuminance > 0.6);
            capture(activity, dark ? "setup-dark" : "setup-light");
        }
    }

    @Test public void addedTileHidesTheAddButton() {
        SplitTileService.setAdded(RuntimeEnvironment.getApplication(), true);
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            assertEquals(View.GONE, controller.get().findViewById(R.id.add_tile).getVisibility());
            assertTrue(text(controller.get(), R.id.tile_text).contains("Added."));
        }
    }

    private static ActivityController<MainActivity> launchConnected(Bundle inspection) throws Exception {
        RouteController routes = ((SplitVoiceApplication) RuntimeEnvironment.getApplication()).routes();
        inspection.putBoolean("ok", true);
        ReflectionHelpers.setField(routes, "service", new IRouteService.Stub() {
            @Override public Bundle inspect(String receiver, String earbuds) { return inspection; }
            @Override public Bundle apply(String receiver, String earbuds) { throw new AssertionError("launch must not apply"); }
            @Override public Bundle restore(String receiver, String earbuds) { throw new AssertionError("launch must not restore"); }
            @Override public void destroy() { throw new AssertionError("launch must not destroy the service"); }
        });
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        FakeRouteService.awaitIdle(routes);
        return controller;
    }

    private static Bundle inspection(int[] inputTypes, int[] outputTypes, int mode) {
        Bundle inspection = new Bundle();
        inspection.putIntArray("inputTypes", inputTypes);
        inspection.putIntArray("outputTypes", outputTypes);
        inspection.putInt("mode", mode);
        return inspection;
    }

    private static ResolveInfo resolveInfo(String packageName) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = packageName;
        info.activityInfo.name = packageName + ".MainActivity";
        return info;
    }

    private static String text(MainActivity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }

    private static String check(MainActivity activity, int row) {
        return ((TextView) activity.findViewById(row).findViewById(R.id.check_value)).getText().toString();
    }

    private static View actions(MainActivity activity, int step) {
        return activity.findViewById(step).findViewById(R.id.step_action);
    }

    /** Renders the whole scrolling screen with its 16dp gutter, so CI artifacts show every state. */
    private static void capture(MainActivity activity, String name) throws Exception {
        View root = activity.findViewById(R.id.content);
        int width = 360;
        int gutter = 16;
        root.measure(View.MeasureSpec.makeMeasureSpec(width - 2 * gutter, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, root.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        var background = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.colorBackground, background, true);
        canvas.drawColor(background.data);
        canvas.translate(gutter, 0);
        root.draw(canvas);
        File directory = new File("build/reports/ui");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
    }
}
