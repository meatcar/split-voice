package com.github.meatcar.splitvoice;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {36, 37})
public class AutoRouteServiceTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private final Settings settings = new Settings(context);

    private RouteController routes() { return ((SplitVoiceApplication) context).routes(); }

    @Test public void runsInTheForegroundByDefault() {
        var controller = Robolectric.buildService(AutoRouteService.class).create().startCommand(0, 1);
        Notification shown = shadowOf(controller.get()).getLastForegroundNotification();
        assertNotNull(shown);
        assertFalse(shadowOf(controller.get()).isStoppedBySelf());
        controller.destroy();
    }

    @Test public void stopsWhenEveryChoiceIsOffAndNothingNeedsRestoring() {
        settings.setAllApps(Settings.OFF);
        var controller = Robolectric.buildService(AutoRouteService.class).create().startCommand(0, 1);
        assertTrue(shadowOf(controller.get()).isStoppedBySelf());
        controller.destroy();
    }

    @Test public void staysForAPendingRestoreEvenWhenOff() {
        settings.setAllApps(Settings.OFF);
        assertTrue(context.getSharedPreferences("routing-recovery", Context.MODE_PRIVATE).edit()
                .putString("input", "[{\"input\":true,\"type\":11,\"address\":\"card=1\",\"name\":\"\"}]").commit());
        var controller = Robolectric.buildService(AutoRouteService.class).create().startCommand(0, 1);
        assertFalse(shadowOf(controller.get()).isStoppedBySelf());
        controller.destroy();
    }

    @Test public void syncFollowsTheSettings() {
        var app = shadowOf(RuntimeEnvironment.getApplication());
        AutoRouteService.sync(context, routes());
        Intent started = app.getNextStartedService();
        assertEquals(AutoRouteService.class.getName(), started.getComponent().getClassName());
        settings.setAllApps(Settings.OFF);
        AutoRouteService.sync(context, routes());
        assertEquals(AutoRouteService.class.getName(), app.getNextStoppedService().getComponent().getClassName());
    }

    @Test public void callActionsOfferOtherChoicesWithOffLast() {
        assertEquals(List.of(Settings.AUTO, "b", Settings.OFF),
                AutoRouteService.actions(List.of(Settings.AUTO, "a", "b", "c", "d", Settings.OFF), "a"));
        assertEquals(List.of(Settings.AUTO, "a", "b"),
                AutoRouteService.actions(List.of(Settings.AUTO, "a", "b", "c", Settings.OFF), Settings.OFF));
        assertEquals(List.of(Settings.OFF), AutoRouteService.actions(List.of(Settings.AUTO, Settings.OFF), Settings.AUTO));
    }
}
