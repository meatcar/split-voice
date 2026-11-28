package com.github.meatcar.splitvoice;

import static com.github.meatcar.splitvoice.RoutingTest.*;
import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 37, qualifiers = "w360dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AdvancedActivityTest {
    private final Settings settings = new Settings(RuntimeEnvironment.getApplication());

    private static RouteController routes() {
        return ((SplitVoiceApplication) RuntimeEnvironment.getApplication()).routes();
    }

    private static void connect(Device... devices) throws Exception {
        ReflectionHelpers.setField(routes(), "service", new FakeRouteService(devices));
        routes().refresh();
        FakeRouteService.awaitIdle(routes());
    }

    @Test public void defaultsNeedNoSetup() throws Exception {
        try (var controller = Robolectric.buildActivity(AdvancedActivity.class).setup()) {
            AdvancedActivity activity = controller.get();
            List<String> rows = rows(activity);
            assertTrue(rows.toString(), rows.get(0).startsWith("Automatic\n"));
            assertEquals(List.of("New profile", "Add app"), rows.subList(1, rows.size()));
            Screens.capture(activity, "advanced-default");
        }
    }

    @Test public void overridesAndSeenAppsAreListedWithTheirChoice() throws Exception {
        Profile walking = settings.create("Walking");
        settings.save(walking.withSide(true, List.of(saved(DJI), saved(JABRA_MIC))).withSide(false, List.of(saved(JABRA))));
        settings.setOverride("com.whatsapp", walking.id());
        settings.markSeen("com.discord");
        try (var controller = Robolectric.buildActivity(AdvancedActivity.class).setup()) {
            AdvancedActivity activity = controller.get();
            assertEquals(List.of(
                    "Automatic\nThe newest USB or wired mic, with the newest headphones. Without such a mic, Android chooses.",
                    "Walking\nMic: DJI Mic, then Jabra Elite 10. Headphones: Jabra Elite 10.", "New profile",
                    "com.whatsapp\nWalking", "com.discord\nUses All apps setting", "Add app"), rows(activity));
            Screens.capture(activity, "advanced");
        }
    }

    @Test public void profileOrderChangesAndMissingDevicesStayListed() throws Exception {
        connect(PHONE_MIC, SPEAKER, JABRA_MIC, JABRA);
        Profile walking = settings.create("Walking");
        settings.save(walking.withSide(true, List.of(saved(DJI), saved(JABRA_MIC))));
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), ProfileActivity.class)
                .putExtra(ProfileActivity.ID, walking.id());
        try (var controller = Robolectric.buildActivity(ProfileActivity.class, intent).setup()) {
            ProfileActivity activity = controller.get();
            assertEquals("DJI Mic\nNot connected", rows(activity).get(1));
            assertEquals("Jabra Elite 10\nConnected", rows(activity).get(2));
            Screens.capture(activity, "profile");

            row(activity, 2).findViewById(R.id.row_up).performClick();
            assertEquals(List.of(saved(JABRA_MIC), saved(DJI)), settings.profile(walking.id()).mics());
            assertEquals("Move DJI Mic up", row(activity, 2).findViewById(R.id.row_up).getContentDescription());

            row(activity, 4).performClick();
            AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
            assertEquals("Jabra Elite 10", dialog.getListView().getAdapter().getItem(1).toString());
            ReflectionHelpers.callInstanceMethod(dialog.getListView(), "performItemClick",
                    ReflectionHelpers.ClassParameter.from(View.class, null),
                    ReflectionHelpers.ClassParameter.from(int.class, 1),
                    ReflectionHelpers.ClassParameter.from(long.class, 1L));
            assertEquals(List.of(saved(JABRA)), settings.profile(walking.id()).headphones());

            row(activity, 1).findViewById(R.id.row_remove).performClick();
            assertEquals(List.of(saved(DJI)), settings.profile(walking.id()).mics());
        }
    }

    private static List<View> rowViews(android.app.Activity activity) {
        List<View> result = new ArrayList<>();
        collect(activity.findViewById(R.id.content), result);
        return result;
    }

    private static void collect(View view, List<View> rows) {
        if (view.findViewById(R.id.row_text) == view) return;
        if (view instanceof ViewGroup group) {
            if (view.findViewById(R.id.row_chevron) != null && group.findViewById(R.id.row_chevron).getParent() == group) {
                rows.add(view);
                return;
            }
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), rows);
        }
    }

    private static View row(android.app.Activity activity, int index) { return rowViews(activity).get(index); }

    private static List<String> rows(android.app.Activity activity) {
        List<String> result = new ArrayList<>();
        for (View row : rowViews(activity)) result.add(((TextView) row.findViewById(R.id.row_text)).getText().toString());
        return result;
    }
}
