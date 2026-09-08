package com.github.meatcar.splitvoice;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h640dp-mdpi")
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
