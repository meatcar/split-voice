package com.github.meatcar.splitvoice;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.media.AudioManager;
import android.util.TypedValue;
import android.Manifest;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.List;

import static com.github.meatcar.splitvoice.RoutingTest.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {36, 37}, qualifiers = "w360dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityTest {
    @Test public void firstLaunchWalksThroughSetupOneStepAtATime() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertEquals("Finish setup", text(activity, R.id.status_title));
            assertEquals("Start Shizuku to continue.", text(activity, R.id.status));
            assertEquals(View.VISIBLE, activity.findViewById(R.id.setup).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.routes).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.chips_scroll).getVisibility());
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

    @Test public void recoverySurvivesRecreationAndExplainsHowToFinishIt() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        assertTrue(context.getSharedPreferences("routing-recovery", Context.MODE_PRIVATE)
                .edit().putString("input", "[{\"input\":true,\"type\":11,\"address\":\"card=1\",\"name\":\"\"}]").commit());
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            controller.recreate();
            MainActivity activity = controller.get();
            assertEquals("Restore needed", text(activity, R.id.status_title));
            assertEquals("Start Shizuku. Split Voice then restores normal audio by itself.", text(activity, R.id.status));
            assertEquals(View.VISIBLE, activity.findViewById(R.id.setup).getVisibility());
            capture(activity, "recovery");
        }
    }

    @Test public void readyScreenNeedsNothingFromTheUser() throws Exception {
        try (var controller = launchConnected(new FakeRouteService(PHONE_MIC, SPEAKER, JABRA, DJI))) {
            MainActivity activity = controller.get();
            assertEquals("Ready", text(activity, R.id.status_title));
            assertEquals("Split Voice steps in when a voice chat starts.", text(activity, R.id.status));
            assertEquals(View.GONE, activity.findViewById(R.id.setup).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.routes).getVisibility());
            assertTrue(text(activity, R.id.advanced).contains("Profiles and app overrides"));
            capture(activity, "ready");
        }
    }

    @Test public void callScreenShowsEachRouteAndSwitchesForThisCall() throws Exception {
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA_MIC, JABRA, DJI);
        remote.audio.mode = AudioManager.MODE_IN_COMMUNICATION;
        remote.audio.app = "com.discord";
        try (var controller = launchConnected(remote)) {
            MainActivity activity = controller.get();
            assertEquals("Split", text(activity, R.id.status_title));
            assertEquals("com.discord · Automatic", text(activity, R.id.status));
            assertEquals("DJI Mic", route(activity, R.id.route_mic, R.id.route_device));
            assertEquals("Jabra Elite 10", route(activity, R.id.route_headphones, R.id.route_device));
            RadioGroup chips = activity.findViewById(R.id.chips);
            assertEquals(2, chips.getChildCount());
            assertTrue(((RadioButton) chips.getChildAt(0)).isChecked());
            capture(activity, "call-split");

            chips.getChildAt(1).performClick();
            FakeRouteService.awaitIdle(routes());
            assertEquals("Off", text(activity, R.id.status_title));
            assertEquals("com.discord · Off · this call", text(activity, R.id.status));
            assertTrue(((RadioButton) chips.getChildAt(1)).isChecked());
            assertTrue(remote.audio.input.isEmpty());
        }
    }

    @Test public void callWithoutAnExternalMicSaysWhatIsMissing() throws Exception {
        Settings settings = new Settings(RuntimeEnvironment.getApplication());
        Profile walking = settings.create("Walking");
        settings.save(walking.withSide(true, List.of(RoutingTest.saved(JABRA_MIC))));
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA_MIC, JABRA);
        remote.audio.mode = AudioManager.MODE_IN_COMMUNICATION;
        remote.audio.app = "com.discord";
        try (var controller = launchConnected(remote)) {
            MainActivity activity = controller.get();
            assertEquals("Not split", text(activity, R.id.status_title));
            assertEquals("No USB or wired mic connected", route(activity, R.id.route_mic, R.id.route_why));
            assertEquals(View.GONE, activity.findViewById(R.id.route_headphones).findViewById(R.id.route_why).getVisibility());
            assertEquals(3, ((RadioGroup) activity.findViewById(R.id.chips)).getChildCount());
            capture(activity, "call-fallback");
        }
    }

    @Test public void diagnosticsDescribeTheSetupWithoutDeviceIdentifiers() throws Exception {
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA, DJI,
                new Device(60, false, 99, "AA:BB:CC", "Secret"));
        remote.audio.mode = AudioManager.MODE_IN_COMMUNICATION;
        remote.audio.app = "com.discord";
        try (var controller = launchConnected(remote)) {
            controller.get().findViewById(R.id.diagnostics).performClick();
            ClipboardManager clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager.class);
            String report = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
            assertTrue(report, report.contains("Android: "));
            assertTrue(report, report.contains("Voice app: detected"));
            assertTrue(report, report.contains("Using: Automatic"));
            assertTrue(report, report.contains("Mic: set to USB device"));
            assertTrue(report, report.contains("Inputs: built-in mic, USB device"));
            assertTrue(report, report.contains("Outputs: built-in speaker, LE headset, type 99"));
            for (String secret : new String[]{"AA:BB", "11:22", "card=", "Jabra", "DJI", "Secret", "com.discord"}) {
                assertFalse(report, report.contains(secret));
            }
        }
    }

    @Test public void allowNotificationsAsksForThePermission() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.allow_notifications).getVisibility());
            activity.findViewById(R.id.allow_notifications).performClick();
            assertEquals(Manifest.permission.POST_NOTIFICATIONS,
                    shadowOf(activity).getLastRequestedPermission().requestedPermissions[0]);
        }
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            assertEquals(View.GONE, controller.get().findViewById(R.id.allow_notifications).getVisibility());
        }
    }

    @Test public void advancedRowOpensAdvanced() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            controller.get().findViewById(R.id.advanced).performClick();
            assertEquals(AdvancedActivity.class.getName(),
                    shadowOf(controller.get()).getNextStartedActivity().getComponent().getClassName());
        }
    }

    @Test public void lightThemeUsesDarkTextOnLightBackground() throws Exception {
        assertReadableTheme(false);
    }

    @Config(qualifiers = "w360dp-h720dp-night-mdpi") @Test public void darkThemeUsesLightTextOnDarkBackground() throws Exception {
        assertReadableTheme(true);
    }

    @Test public void launcherIconIsAdaptiveAndThemeable() throws Exception {
        var app = RuntimeEnvironment.getApplication();
        assertEquals(R.mipmap.ic_launcher, app.getApplicationInfo().icon);
        var icon = app.getDrawable(R.mipmap.ic_launcher);
        assertTrue(icon instanceof AdaptiveIconDrawable);
        assertNotNull(((AdaptiveIconDrawable) icon).getMonochrome());
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

    private static RouteController routes() {
        return ((SplitVoiceApplication) RuntimeEnvironment.getApplication()).routes();
    }

    private static ActivityController<MainActivity> launchConnected(FakeRouteService remote) throws Exception {
        ReflectionHelpers.setField(routes(), "service", remote);
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        FakeRouteService.awaitIdle(routes());
        return controller;
    }

    private static String route(MainActivity activity, int row, int field) {
        return ((TextView) activity.findViewById(row).findViewById(field)).getText().toString();
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

    private static View actions(MainActivity activity, int step) {
        return activity.findViewById(step).findViewById(R.id.step_action);
    }

    private static void capture(MainActivity activity, String name) throws Exception { Screens.capture(activity, name); }
}
