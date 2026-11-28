package com.github.meatcar.splitvoice;

import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** Runs on a real Android framework with no Shizuku, so it exercises the unavailable path only. */
@RunWith(AndroidJUnit4.class)
public class SmokeTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void launchesAndExplainsMissingShizuku() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("Finish setup", ((TextView) activity.findViewById(R.id.status_title)).getText().toString());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.setup).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.routes).getVisibility());
            });
        }
    }

    @Test public void copiesDiagnosticsWithoutAnyShizukuConnection() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.diagnostics).performClick();
                String report = activity.getSystemService(ClipboardManager.class)
                        .getPrimaryClip().getItemAt(0).getText().toString();
                assertTrue(report, report.startsWith("Split Voice diagnostics"));
                assertTrue(report, report.contains("Shizuku: not running"));
            });
        }
    }

    @Test public void tileServiceIsBoundOnlyByTheSystem() throws Exception {
        var info = context.getPackageManager().getServiceInfo(new ComponentName(context, SplitTileService.class), 0);
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", info.permission);
        assertTrue(info.exported);
    }
}
