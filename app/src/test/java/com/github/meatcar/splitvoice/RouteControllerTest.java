package com.github.meatcar.splitvoice;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RouteControllerTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private final SharedPreferences journal = context.getSharedPreferences("routing-recovery", Context.MODE_PRIVATE);

    @Test public void tileToggleWritesJournalBeforeApplyAndReadsBackBeforeActive() throws Exception {
        RouteController routes = new RouteController(context);
        FakeRouteService remote = new FakeRouteService(journal);
        ReflectionHelpers.setField(routes, "service", remote);
        routes.toggle();
        FakeRouteService.awaitIdle(routes);
        assertTrue(remote.applied);
        assertTrue(routes.pending());
        assertEquals(RouteController.State.ACTIVE, routes.state());
        remote.mode = 0;
        routes.toggle();
        FakeRouteService.awaitIdle(routes);
        assertFalse(routes.pending());
        assertFalse(remote.applied);
        assertEquals(RouteController.State.READY, routes.state());
    }

    @Test public void deadServiceKeepsJournalAndAllowsReconnect() throws Exception {
        RouteController routes = new RouteController(context);
        FakeRouteService remote = new FakeRouteService(journal);
        ReflectionHelpers.setField(routes, "service", remote);
        routes.toggle();
        FakeRouteService.awaitIdle(routes);
        remote.dead = true;
        routes.restore();
        FakeRouteService.awaitIdle(routes);
        assertTrue(routes.pending());
        assertFalse(routes.connected());
        assertEquals(RouteController.State.RECOVERY, routes.state());
    }

    @Test public void failedApplyRetainsRecoveryAcrossControllerRestart() throws Exception {
        RouteController routes = new RouteController(context);
        FakeRouteService remote = new FakeRouteService(journal);
        remote.failApply = true;
        ReflectionHelpers.setField(routes, "service", remote);
        routes.toggle();
        FakeRouteService.awaitIdle(routes);
        assertTrue(routes.pending());
        assertEquals(RouteController.State.RECOVERY, routes.state());
        RouteController restarted = new RouteController(context);
        assertTrue(restarted.pending());
        assertEquals(RouteController.State.RECOVERY, restarted.state());
        ReflectionHelpers.setField(restarted, "service", remote);
        remote.mode = 0;
        restarted.restore();
        FakeRouteService.awaitIdle(restarted);
        assertFalse(restarted.pending());
    }

    @Test public void restoreDuringVoiceRefusesAndDeviceLossInvalidatesSelectedState() throws Exception {
        RouteController routes = new RouteController(context);
        FakeRouteService remote = new FakeRouteService(journal);
        ReflectionHelpers.setField(routes, "service", remote);
        routes.toggle();
        FakeRouteService.awaitIdle(routes);
        routes.restore();
        FakeRouteService.awaitIdle(routes);
        assertTrue(routes.pending());
        assertTrue(remote.applied);
        assertEquals("End the voice chat first. Nothing changed.", routes.message());
        remote.devicesReady = false;
        routes.refresh();
        FakeRouteService.awaitIdle(routes);
        assertEquals(RouteController.State.RECOVERY, routes.state());
        assertTrue(routes.pending());
    }

    @Test public void repeatedTapsAndRefreshCannotOverlapAnApply() throws Exception {
        RouteController routes = new RouteController(context);
        FakeRouteService remote = new FakeRouteService(journal);
        remote.holdApply = new CountDownLatch(1);
        ReflectionHelpers.setField(routes, "service", remote);
        routes.toggle();
        try {
            assertTrue(remote.enteredApply.await(2, TimeUnit.SECONDS));
            assertEquals(RouteController.State.WORKING, routes.state());
            routes.toggle();
            routes.restore();
            routes.refresh();
        } finally {
            remote.holdApply.countDown();
        }
        FakeRouteService.awaitIdle(routes);
        assertEquals(1, remote.applyCalls);
        assertEquals(0, remote.restoreCalls);
        assertEquals(RouteController.State.ACTIVE, routes.state());
    }

    @Test public void noJournalIsWrittenWhenVoiceIsStopped() throws Exception {
        RouteController routes = new RouteController(context);
        FakeRouteService remote = new FakeRouteService(journal);
        remote.mode = 0;
        ReflectionHelpers.setField(routes, "service", remote);
        routes.toggle();
        FakeRouteService.awaitIdle(routes);
        assertFalse(routes.pending());
        assertEquals(0, remote.applyCalls);
        assertEquals("Start a voice chat first. Nothing changed.", routes.message());
    }

}
