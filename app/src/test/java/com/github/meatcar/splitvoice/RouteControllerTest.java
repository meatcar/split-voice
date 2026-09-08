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
        FakeService remote = new FakeService();
        ReflectionHelpers.setField(routes, "service", remote);
        routes.toggle();
        awaitIdle(routes);
        assertTrue(remote.applied);
        assertTrue(routes.pending());
        assertEquals(RouteController.State.ACTIVE, routes.state());
        remote.mode = 0;
        routes.toggle();
        awaitIdle(routes);
        assertFalse(routes.pending());
        assertFalse(remote.applied);
        assertEquals(RouteController.State.READY, routes.state());
    }

    @Test public void deadServiceKeepsJournalAndAllowsReconnect() throws Exception {
        RouteController routes = new RouteController(context);
        FakeService remote = new FakeService();
        ReflectionHelpers.setField(routes, "service", remote);
        routes.toggle();
        awaitIdle(routes);
        remote.dead = true;
        routes.restore();
        awaitIdle(routes);
        assertTrue(routes.pending());
        assertFalse(routes.connected());
        assertEquals(RouteController.State.RECOVERY, routes.state());
    }

    @Test public void failedApplyRetainsRecoveryAcrossControllerRestart() throws Exception {
        RouteController routes = new RouteController(context);
        FakeService remote = new FakeService();
        remote.failApply = true;
        ReflectionHelpers.setField(routes, "service", remote);
        routes.toggle();
        awaitIdle(routes);
        assertTrue(routes.pending());
        assertEquals(RouteController.State.RECOVERY, routes.state());
        RouteController restarted = new RouteController(context);
        assertTrue(restarted.pending());
        assertEquals(RouteController.State.RECOVERY, restarted.state());
        ReflectionHelpers.setField(restarted, "service", remote);
        remote.mode = 0;
        restarted.restore();
        awaitIdle(restarted);
        assertFalse(restarted.pending());
    }

    @Test public void restoreDuringVoiceRefusesAndDeviceLossInvalidatesSelectedState() throws Exception {
        RouteController routes = new RouteController(context);
        FakeService remote = new FakeService();
        ReflectionHelpers.setField(routes, "service", remote);
        routes.toggle();
        awaitIdle(routes);
        routes.restore();
        awaitIdle(routes);
        assertTrue(routes.pending());
        assertTrue(remote.applied);
        assertEquals("Stop Voice before restoring.", routes.message());
        remote.devicesReady = false;
        routes.refresh();
        awaitIdle(routes);
        assertEquals(RouteController.State.RECOVERY, routes.state());
        assertTrue(routes.pending());
    }

    @Test public void repeatedTapsAndRefreshCannotOverlapAnApply() throws Exception {
        RouteController routes = new RouteController(context);
        FakeService remote = new FakeService();
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
        awaitIdle(routes);
        assertEquals(1, remote.applyCalls);
        assertEquals(0, remote.restoreCalls);
        assertEquals(RouteController.State.ACTIVE, routes.state());
    }

    @Test public void noJournalIsWrittenWhenVoiceIsStopped() throws Exception {
        RouteController routes = new RouteController(context);
        FakeService remote = new FakeService();
        remote.mode = 0;
        ReflectionHelpers.setField(routes, "service", remote);
        routes.toggle();
        awaitIdle(routes);
        assertFalse(routes.pending());
        assertEquals(0, remote.applyCalls);
        assertEquals("Start Voice before applying.", routes.message());
    }

    private final class FakeService extends IRouteService.Stub {
        boolean applied;
        boolean dead;
        boolean failApply;
        boolean devicesReady = true;
        int applyCalls;
        int restoreCalls;
        CountDownLatch holdApply;
        final CountDownLatch enteredApply = new CountDownLatch(1);
        int mode = 3;
        @Override public void destroy() { fail("Unexpected service destruction"); }
        @Override public Bundle inspect(String receiver, String earbuds) {
            Bundle result = new Bundle();
            result.putBoolean("ok", true);
            result.putBoolean("devicesReady", devicesReady);
            result.putBoolean("empty", !applied);
            result.putBoolean("matches", devicesReady && applied && "receiver".equals(receiver) && "earbuds".equals(earbuds));
            result.putString("receiver", "receiver");
            result.putString("earbuds", "earbuds");
            result.putInt("mode", mode);
            return result;
        }
        @Override public Bundle apply(String receiver, String earbuds) {
            assertTrue(journal.getBoolean("pending", false));
            assertEquals("receiver", journal.getString("receiver", null));
            assertEquals("earbuds", journal.getString("earbuds", null));
            applyCalls++;
            enteredApply.countDown();
            if (holdApply != null) {
                try { assertTrue(holdApply.await(2, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
            }
            applied = true;
            Bundle result = new Bundle();
            result.putBoolean("ok", !failApply);
            if (failApply) result.putString("message", "Partial apply failure");
            return result;
        }
        @Override public Bundle restore(String receiver, String earbuds) throws RemoteException {
            restoreCalls++;
            if (dead) throw new RemoteException("Service died");
            Bundle result = new Bundle();
            result.putBoolean("ok", mode == 0);
            if (mode == 0) applied = false;
            else result.putString("message", "Stop Voice before restoring.");
            return result;
        }
    }

    private static void awaitIdle(RouteController routes) throws Exception {
        for (int i = 0; i < 400; i++) {
            shadowOf(Looper.getMainLooper()).idle();
            if (!routes.busy()) return;
            Thread.sleep(5);
        }
        fail("Routing worker did not finish");
    }
}
