package com.github.meatcar.splitvoice;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AutoRouteTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private final SharedPreferences journal = context.getSharedPreferences("routing-recovery", Context.MODE_PRIVATE);
    private final RouteController routes = new RouteController(context);
    private final FakeRouteService remote = new FakeRouteService(journal);
    private final AutoRoute auto = new AutoRoute(routes);

    private void refresh() throws Exception {
        routes.refresh();
        FakeRouteService.awaitIdle(routes);
    }

    private void watch(int mode) throws Exception {
        remote.mode = mode;
        ReflectionHelpers.setField(routes, "service", remote);
        auto.start();
        FakeRouteService.awaitIdle(routes);
    }

    @Test public void appliesWhenVoiceStartsAndRestoresWhenItEnds() throws Exception {
        watch(0);
        refresh();
        assertEquals(0, remote.applyCalls);
        remote.mode = 3;
        refresh();
        assertEquals(1, remote.applyCalls);
        assertEquals(RouteController.State.ACTIVE, routes.state());
        refresh();
        assertEquals(1, remote.applyCalls);
        remote.mode = 0;
        refresh();
        assertEquals(1, remote.restoreCalls);
        assertFalse(routes.pending());
        assertEquals(RouteController.State.READY, routes.state());
        remote.mode = 3;
        refresh();
        assertEquals("a second session splits again", 2, remote.applyCalls);
    }

    @Test public void waitsForDevicesDuringVoiceThenAppliesOnce() throws Exception {
        remote.devicesReady = false;
        watch(3);
        refresh();
        assertEquals(0, remote.applyCalls);
        remote.devicesReady = true;
        refresh();
        refresh();
        assertEquals(1, remote.applyCalls);
    }

    @Test public void neverRetriesAFailedApplyWithinASession() throws Exception {
        remote.failApply = true;
        watch(3);
        refresh();
        refresh();
        assertEquals(1, remote.applyCalls);
        assertTrue(routes.pending());
    }

    @Test public void neverRetriesAFailedRestoreAndLeavesRecoveryForTheUser() throws Exception {
        watch(3);
        refresh();
        remote.failRestore = true;
        remote.mode = 0;
        refresh();
        refresh();
        assertEquals(1, remote.restoreCalls);
        assertTrue(routes.pending());
    }

    @Test public void doesNotRestoreWhileAnotherAudioModeIsActive() throws Exception {
        watch(3);
        refresh();
        for (int mode : new int[]{1, 2, 4}) {
            remote.mode = mode;
            refresh();
        }
        assertEquals(0, remote.restoreCalls);
        assertTrue(routes.pending());
    }

    @Test public void decisionTableIgnoresBusyUnknownAndNonVoiceModes() {
        ReflectionHelpers.setField(routes, "service", remote);
        assertEquals(AutoRoute.Action.NONE, auto.next(RouteController.State.WORKING, 3, false));
        assertEquals(AutoRoute.Action.NONE, auto.next(RouteController.State.READY, 0, false));
        assertEquals(AutoRoute.Action.NONE, auto.next(RouteController.State.READY, -1, false));
        assertEquals(AutoRoute.Action.NONE, auto.next(RouteController.State.RECOVERY, -1, true));
        assertEquals(AutoRoute.Action.NONE, auto.next(RouteController.State.BLOCKED, 3, false));
        assertEquals(AutoRoute.Action.APPLY, auto.next(RouteController.State.READY, 3, false));
        assertEquals(AutoRoute.Action.NONE, auto.next(RouteController.State.READY, 3, false));
    }

    @Test public void settingIsOffUntilChosen() {
        assertFalse(AutoRoute.enabled(context));
        AutoRoute.setEnabled(context, true);
        assertTrue(AutoRoute.enabled(context));
    }
}
