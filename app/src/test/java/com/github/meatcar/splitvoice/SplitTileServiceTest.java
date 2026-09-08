package com.github.meatcar.splitvoice;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowService;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = SplitTileServiceTest.LifecycleTileShadow.class)
public class SplitTileServiceTest {
    // NOTE: Robolectric 4.14.1's tile shadow lacks ShadowService inheritance.
    @Implements(TileService.class)
    public static class LifecycleTileShadow extends ShadowService {
        private final Tile tile = Shadow.newInstanceOf(Tile.class);
        boolean locked;
        Runnable unlockAction;
        @Implementation protected Tile getQsTile() { return tile; }
        @Implementation protected boolean isLocked() { return locked; }
        @Implementation protected void unlockAndRun(Runnable action) { unlockAction = action; }
    }

    @Test public void lockedTapWaitsForUnlockThenTogglesWithoutOpeningActivity() throws Exception {
        var app = (SplitVoiceApplication) RuntimeEnvironment.getApplication();
        RouteController routes = app.routes();
        MemoryService remote = new MemoryService();
        ReflectionHelpers.setField(routes, "service", remote);
        var service = Robolectric.buildService(SplitTileService.class).create();
        LifecycleTileShadow platform = Shadow.extract(service.get());
        try {
            service.get().onStartListening();
            awaitIdle(routes);
            assertEquals(Tile.STATE_INACTIVE, service.get().getQsTile().getState());
            platform.locked = true;
            service.get().onClick();
            assertNotNull(platform.unlockAction);
            assertFalse(routes.pending());
            platform.locked = false;
            platform.unlockAction.run();
            awaitIdle(routes);
            assertEquals(Tile.STATE_ACTIVE, service.get().getQsTile().getState());
            assertTrue(routes.pending());
            assertNull(shadowOf(app).getNextStartedActivity());
            service.get().onClick();
            awaitIdle(routes);
            assertTrue(routes.pending());
            assertEquals("Stop Voice before restoring.", routes.message());
            remote.mode = 0;
            service.get().onClick();
            awaitIdle(routes);
            assertFalse(routes.pending());
            assertEquals(Tile.STATE_INACTIVE, service.get().getQsTile().getState());
            assertNull(shadowOf(app).getNextStartedActivity());
        } finally {
            service.destroy();
        }
    }

    @Test public void unavailableTileExplainsSetupWithoutOpeningAnActivity() {
        var service = Robolectric.buildService(SplitTileService.class).create();
        try {
            service.get().onStartListening();
            assertEquals(Tile.STATE_UNAVAILABLE, service.get().getQsTile().getState());
            assertEquals("Shizuku unavailable", service.get().getQsTile().getSubtitle());
            service.get().onClick();
            assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
        } finally {
            service.get().onStopListening();
            service.destroy();
        }
    }

    @Test public void journalAloneIsRecoveryNotActiveAndRemovingTileIsNotCleanup() {
        var journal = RuntimeEnvironment.getApplication().getSharedPreferences("routing-recovery", Context.MODE_PRIVATE);
        assertTrue(journal.edit().putBoolean("pending", true).commit());
        var service = Robolectric.buildService(SplitTileService.class).create();
        try {
            service.get().onStartListening();
            assertEquals(Tile.STATE_UNAVAILABLE, service.get().getQsTile().getState());
            assertEquals("Recovery required", service.get().getQsTile().getSubtitle());
            service.get().onTileRemoved();
        } finally {
            service.get().onStopListening();
            service.destroy();
        }
        assertTrue(journal.getBoolean("pending", false));
    }

    private static final class MemoryService extends IRouteService.Stub {
        boolean applied;
        int mode = 3;
        @Override public void destroy() { fail("Unexpected destruction"); }
        @Override public Bundle inspect(String receiver, String earbuds) {
            Bundle result = new Bundle();
            result.putBoolean("ok", true);
            result.putBoolean("empty", !applied);
            result.putBoolean("devicesReady", true);
            result.putBoolean("matches", applied && "input".equals(receiver) && "output".equals(earbuds));
            result.putInt("mode", mode);
            result.putString("receiver", "input");
            result.putString("earbuds", "output");
            return result;
        }
        @Override public Bundle apply(String receiver, String earbuds) {
            assertEquals("input", receiver);
            assertEquals("output", earbuds);
            applied = true;
            Bundle result = new Bundle();
            result.putBoolean("ok", true);
            return result;
        }
        @Override public Bundle restore(String receiver, String earbuds) {
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
