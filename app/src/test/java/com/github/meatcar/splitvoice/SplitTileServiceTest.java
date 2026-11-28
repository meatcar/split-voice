package com.github.meatcar.splitvoice;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
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

import static com.github.meatcar.splitvoice.RoutingTest.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {36, 37}, shadows = SplitTileServiceTest.LifecycleTileShadow.class)
public class SplitTileServiceTest {
    // NOTE: Robolectric 4.14.1's tile shadow lacks ShadowService inheritance.
    @Implements(TileService.class)
    public static class LifecycleTileShadow extends ShadowService {
        private final Tile tile = Shadow.newInstanceOf(Tile.class);
        boolean locked;
        Runnable unlockAction;
        PendingIntent collapsedTo;
        @Implementation protected Tile getQsTile() { return tile; }
        @Implementation protected boolean isLocked() { return locked; }
        @Implementation protected void unlockAndRun(Runnable action) { unlockAction = action; }
        @Implementation protected void startActivityAndCollapse(PendingIntent intent) { collapsedTo = intent; }
        @Implementation protected void startActivityAndCollapse(Intent intent) {
            collapsedTo = PendingIntent.getActivity(RuntimeEnvironment.getApplication(), 0, intent, PendingIntent.FLAG_IMMUTABLE);
        }
    }

    @Test public void tapInACallCyclesChoicesOnlyAfterUnlock() throws Exception {
        var app = (SplitVoiceApplication) RuntimeEnvironment.getApplication();
        RouteController routes = app.routes();
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA, DJI);
        remote.audio.mode = AudioManager.MODE_IN_COMMUNICATION;
        ReflectionHelpers.setField(routes, "service", remote);
        var service = Robolectric.buildService(SplitTileService.class).create();
        LifecycleTileShadow platform = Shadow.extract(service.get());
        try {
            service.get().onStartListening();
            FakeRouteService.awaitIdle(routes);
            assertEquals(Tile.STATE_ACTIVE, service.get().getQsTile().getState());
            assertEquals("Automatic", service.get().getQsTile().getSubtitle());
            platform.locked = true;
            service.get().onClick();
            assertNotNull(platform.unlockAction);
            FakeRouteService.awaitIdle(routes);
            assertTrue(RouteTrial.holds(remote.audio.input, DJI));
            platform.locked = false;
            platform.unlockAction.run();
            FakeRouteService.awaitIdle(routes);
            assertEquals(Tile.STATE_INACTIVE, service.get().getQsTile().getState());
            assertEquals("Off", service.get().getQsTile().getSubtitle());
            assertTrue(remote.audio.input.isEmpty());
            service.get().onClick();
            FakeRouteService.awaitIdle(routes);
            assertEquals(Tile.STATE_ACTIVE, service.get().getQsTile().getState());
            assertNull(platform.collapsedTo);
        } finally {
            service.get().onStopListening();
            service.destroy();
        }
    }

    @Test public void tapBeforeSetupOpensTheAppAndRemembersTheTile() {
        var service = Robolectric.buildService(SplitTileService.class).create();
        LifecycleTileShadow platform = Shadow.extract(service.get());
        try {
            assertFalse(SplitTileService.added(service.get()));
            service.get().onStartListening();
            assertTrue(SplitTileService.added(service.get()));
            assertEquals(Tile.STATE_INACTIVE, service.get().getQsTile().getState());
            assertEquals("Set up", service.get().getQsTile().getSubtitle());
            service.get().onClick();
            assertEquals(MainActivity.class.getName(),
                    shadowOf(platform.collapsedTo).getSavedIntent().getComponent().getClassName());
            service.get().onTileRemoved();
            assertFalse(SplitTileService.added(service.get()));
        } finally {
            service.get().onStopListening();
            service.destroy();
        }
    }

    @Test public void journalAloneIsRecoveryNotActiveAndRemovingTileIsNotCleanup() {
        var journal = RuntimeEnvironment.getApplication().getSharedPreferences("routing-recovery", Context.MODE_PRIVATE);
        assertTrue(journal.edit().putString("output", "[{\"input\":false,\"type\":26,\"address\":\"11:22\",\"name\":\"\"}]").commit());
        var service = Robolectric.buildService(SplitTileService.class).create();
        try {
            service.get().onStartListening();
            assertEquals(Tile.STATE_INACTIVE, service.get().getQsTile().getState());
            assertEquals("Restore needed", service.get().getQsTile().getSubtitle());
            service.get().onTileRemoved();
        } finally {
            service.get().onStopListening();
            service.destroy();
        }
        assertTrue(journal.contains("output"));
    }
}
