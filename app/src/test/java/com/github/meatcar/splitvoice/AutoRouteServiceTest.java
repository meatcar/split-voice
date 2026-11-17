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

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 35})
public class AutoRouteServiceTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void runsInTheForegroundOnlyWhileAutomaticSplitIsOn() {
        AutoRoute.setEnabled(context, true);
        var controller = Robolectric.buildService(AutoRouteService.class).create().startCommand(0, 1);
        Notification shown = shadowOf(controller.get()).getLastForegroundNotification();
        assertNotNull(shown);
        assertEquals("Automatic split is on", shown.extras.getString(Notification.EXTRA_TITLE));
        assertFalse(shadowOf(controller.get()).isStoppedBySelf());
        controller.destroy();
    }

    @Test public void stopsItselfWhenTheSettingWasTurnedOff() {
        AutoRoute.setEnabled(context, false);
        var controller = Robolectric.buildService(AutoRouteService.class).create().startCommand(0, 1);
        assertTrue(shadowOf(controller.get()).isStoppedBySelf());
        controller.destroy();
    }

    @Test public void syncStartsAndStopsTheService() {
        var app = shadowOf(RuntimeEnvironment.getApplication());
        AutoRoute.setEnabled(context, true);
        AutoRouteService.sync(context);
        Intent started = app.getNextStartedService();
        assertEquals(AutoRouteService.class.getName(), started.getComponent().getClassName());
        AutoRoute.setEnabled(context, false);
        AutoRouteService.sync(context);
        assertEquals(AutoRouteService.class.getName(), app.getNextStoppedService().getComponent().getClassName());
    }
}
