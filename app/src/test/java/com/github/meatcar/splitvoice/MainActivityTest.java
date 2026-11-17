package com.github.meatcar.splitvoice;

import android.content.ClipboardManager;
import android.content.Context;
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
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 35}, qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityTest {
    @Test public void disconnectedLaunchOffersSetupWithoutRoutingWrites() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertTrue(activity.findViewById(R.id.connect).isEnabled());
            assertEquals("Open Shizuku", ((TextView) activity.findViewById(R.id.connect)).getText());
            assertEquals(View.GONE, activity.findViewById(R.id.check).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.restore).getVisibility());
            assertEquals("Shizuku unavailable", ((TextView) activity.findViewById(R.id.status_title)).getText());
            capture(activity.getWindow().getDecorView(), "setup");
            ScrollView scroll = activity.findViewById(R.id.scroll);
            assertFalse(scroll.canScrollVertically(1));
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
            assertEquals("Recovery required", ((TextView) activity.findViewById(R.id.status_title)).getText());
            assertEquals("Start Shizuku to restore.", ((TextView) activity.findViewById(R.id.status)).getText());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.restore).getVisibility());
            assertFalse(activity.findViewById(R.id.restore).isEnabled());
            capture(activity.getWindow().getDecorView(), "recovery");
        }
    }

    @Test public void connectedScreenShowsOnlyRelevantControls() throws Exception {
        var app = (SplitVoiceApplication) RuntimeEnvironment.getApplication();
        RouteController routes = app.routes();
        for (boolean active : new boolean[]{false, true}) {
            var journal = app.getSharedPreferences("routing-recovery", Context.MODE_PRIVATE);
            assertTrue(journal.edit().putBoolean("pending", active).commit());
            IRouteService service = new IRouteService.Stub() {
                @Override public Bundle inspect(String receiver, String earbuds) {
                    Bundle result = new Bundle();
                    result.putBoolean("ok", true);
                    result.putBoolean("empty", !active);
                    result.putBoolean("matches", active);
                    result.putBoolean("devicesReady", true);
                    return result;
                }
                @Override public Bundle apply(String receiver, String earbuds) { throw new AssertionError("UI launch must not apply"); }
                @Override public Bundle restore(String receiver, String earbuds) { throw new AssertionError("UI launch must not restore"); }
                @Override public void destroy() { throw new AssertionError("UI launch must not destroy service"); }
            };
            ReflectionHelpers.setField(routes, "service", service);
            try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
                for (int i = 0; i < 400 && routes.busy(); i++) {
                    shadowOf(Looper.getMainLooper()).idle();
                    Thread.sleep(5);
                }
                assertFalse("Route inspection did not finish", routes.busy());
                MainActivity activity = controller.get();
                assertEquals(active ? "Split selected" : "Ready to split",
                        ((TextView) activity.findViewById(R.id.status_title)).getText());
                assertEquals(View.GONE, activity.findViewById(R.id.connect).getVisibility());
                assertTrue(activity.findViewById(R.id.check).isEnabled());
                assertEquals(active ? View.VISIBLE : View.GONE, activity.findViewById(R.id.restore).getVisibility());
                capture(activity.getWindow().getDecorView(), active ? "active" : "ready");
                assertFalse(activity.findViewById(R.id.scroll).canScrollVertically(1));
            }
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
            assertEquals("Edit Quick Settings to add Split Voice.", ShadowToast.getTextOfLatestToast());
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

    @Config(qualifiers = "w360dp-h640dp-night-mdpi") @Test public void darkThemeUsesLightTextOnDarkBackground() throws Exception {
        assertReadableTheme(true);
    }

    @Config(sdk = 35) @Test public void launcherIconIsAdaptiveAndThemeable() throws Exception {
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
            capture(activity.getWindow().getDecorView(), dark ? "setup-dark" : "setup-light");
        }
    }

    private static void capture(View root, String name) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 360, 640);
        Bitmap bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File directory = new File("build/reports/ui");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
    }
}
