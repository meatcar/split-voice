package com.github.meatcar.splitvoice;

import static com.github.meatcar.splitvoice.RoutingTest.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 37)
public class RouteControllerTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private final SharedPreferences journal = context.getSharedPreferences("routing-recovery", Context.MODE_PRIVATE);
    private final Settings settings = new Settings(context);

    private RouteController connect(FakeRouteService remote) throws Exception {
        RouteController routes = new RouteController(context);
        ReflectionHelpers.setField(routes, "service", remote);
        routes.refresh();
        FakeRouteService.awaitIdle(routes);
        return routes;
    }

    private static void call(FakeRouteService remote, String app) {
        remote.audio.mode = AudioManager.MODE_IN_COMMUNICATION;
        remote.audio.app = app;
    }

    private static void hangUp(FakeRouteService remote) {
        remote.audio.mode = AudioManager.MODE_NORMAL;
        remote.audio.app = null;
    }

    private static void refresh(RouteController routes) throws Exception {
        routes.refresh();
        FakeRouteService.awaitIdle(routes);
    }

    @Test public void automaticRecordsBeforeWritingAndCleansUpWhenTheCallEnds() throws Exception {
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA_MIC, JABRA, DJI);
        remote.beforeRoute = () -> {
            if (remote.audio.mode == AudioManager.MODE_IN_COMMUNICATION) {
                assertTrue(journal.getString("input", "").contains(DJI.address()));
                assertTrue(journal.getString("output", "").contains(JABRA.address()));
            }
        };
        RouteController routes = connect(remote);
        assertEquals(RouteController.State.READY, routes.state());
        assertFalse(routes.pending());

        call(remote, "com.discord");
        refresh(routes);
        assertEquals(RouteController.State.CALL, routes.state());
        assertTrue(RouteTrial.holds(remote.audio.input, DJI));
        assertTrue(RouteTrial.holds(remote.audio.output, JABRA));
        assertEquals("Split", routes.headline());
        assertEquals(new RouteController.Line("DJI Mic", null), routes.line(true));
        assertTrue(routes.pending());
        assertEquals(List.of("com.discord"), settings.seen());

        hangUp(remote);
        refresh(routes);
        assertEquals(RouteController.State.READY, routes.state());
        assertTrue(remote.audio.input.isEmpty());
        assertTrue(remote.audio.output.isEmpty());
        assertFalse(routes.pending());
    }

    @Test public void withoutAnExternalMicAutomaticChangesNothing() throws Exception {
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA_MIC, JABRA);
        call(remote, "com.discord");
        RouteController routes = connect(remote);
        assertEquals("Not split", routes.headline());
        assertEquals(new RouteController.Line("Android's choice", "No USB or wired mic connected"), routes.line(true));
        assertEquals(new RouteController.Line("Android's choice", null), routes.line(false));
        assertEquals(0, remote.audio.writes);
        assertFalse(routes.pending());
    }

    @Test public void unplugAndReplugMidCallFollowsTheProfileOrder() throws Exception {
        Profile walking = settings.create("Walking");
        settings.save(walking.withSide(true, List.of(saved(DJI), saved(JABRA_MIC))).withSide(false, List.of(saved(JABRA))));
        settings.setAllApps(walking.id());
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA_MIC, JABRA, DJI);
        call(remote, "com.discord");
        RouteController routes = connect(remote);
        assertTrue(RouteTrial.holds(remote.audio.input, DJI));

        remote.audio.devices.remove(DJI);
        remote.audio.input.clear();
        refresh(routes);
        assertTrue(RouteTrial.holds(remote.audio.input, JABRA_MIC));
        assertEquals("com.discord · Walking", routes.detail());

        Device replugged = new Device(50, true, AudioDeviceInfo.TYPE_USB_DEVICE, "card=2;device=0", "DJI Mic");
        remote.audio.devices.add(replugged);
        refresh(routes);
        assertTrue(RouteTrial.holds(remote.audio.input, replugged));
        hangUp(remote);
        refresh(routes);
        assertTrue(remote.audio.input.isEmpty());
        assertFalse(routes.pending());
    }

    @Test public void refusedDevicesAreSkippedAndNamedUntilTheCallEnds() throws Exception {
        Profile desk = settings.create("Desk");
        settings.save(desk.withSide(false, List.of(saved(JABRA), saved(SPEAKER))));
        settings.setAllApps(desk.id());
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA);
        remote.audio.refuses = java.util.Set.of(JABRA.key());
        call(remote, "com.discord");
        RouteController routes = connect(remote);
        assertTrue(RouteTrial.holds(remote.audio.output, SPEAKER));
        assertEquals(new RouteController.Line("Phone speaker", "Android refused Jabra Elite 10"), routes.line(false));

        hangUp(remote);
        refresh(routes);
        remote.audio.refuses = java.util.Set.of();
        call(remote, "com.discord");
        refresh(routes);
        assertTrue(RouteTrial.holds(remote.audio.output, JABRA));
        assertTrue(routes.status().refused().isEmpty());
    }

    @Test public void anotherAppsChoiceWinsAndOutlivesTheCall() throws Exception {
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA, DJI);
        remote.audio.output.add(SPEAKER);
        call(remote, "com.discord");
        RouteController routes = connect(remote);
        assertTrue(RouteTrial.holds(remote.audio.input, DJI));
        assertTrue(RouteTrial.holds(remote.audio.output, SPEAKER));
        assertEquals(new RouteController.Line("Phone speaker", "Chosen by another app. Split Voice leaves it alone."),
                routes.line(false));

        hangUp(remote);
        refresh(routes);
        assertTrue(remote.audio.input.isEmpty());
        assertTrue(RouteTrial.holds(remote.audio.output, SPEAKER));
        assertFalse(routes.pending());
    }

    @Test public void appOverridesApplyAndAPickLastsOnlyForThisCall() throws Exception {
        settings.setOverride("com.whatsapp", Settings.OFF);
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA, DJI);
        call(remote, "com.whatsapp");
        RouteController routes = connect(remote);
        assertEquals("Off", routes.headline());
        assertTrue(remote.audio.input.isEmpty());

        routes.pickForCall(Settings.AUTO);
        FakeRouteService.awaitIdle(routes);
        assertTrue(RouteTrial.holds(remote.audio.input, DJI));
        assertTrue(routes.status().picked());
        assertEquals("com.whatsapp · Automatic · this call", routes.detail());
        routes.pickForCall(routes.nextChoice());
        FakeRouteService.awaitIdle(routes);
        assertTrue(remote.audio.input.isEmpty());
        assertFalse(routes.status().picked());

        routes.pickForCall(Settings.AUTO);
        FakeRouteService.awaitIdle(routes);
        hangUp(remote);
        refresh(routes);
        call(remote, "com.whatsapp");
        refresh(routes);
        assertTrue(remote.audio.input.isEmpty());
        assertEquals(Settings.OFF, routes.status().choice());
    }

    @Test public void deletingAProfileMidCallMovesItsAppsBackToAllApps() throws Exception {
        Profile phone = settings.create("Phone");
        settings.save(phone.withSide(true, List.of(saved(PHONE_MIC))));
        settings.setOverride("com.discord", phone.id());
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA, DJI);
        call(remote, "com.discord");
        RouteController routes = connect(remote);
        assertTrue(RouteTrial.holds(remote.audio.input, PHONE_MIC));

        settings.delete(phone.id());
        FakeRouteService.awaitIdle(routes);
        assertTrue(RouteTrial.holds(remote.audio.input, DJI));
        assertEquals(Settings.AUTO, routes.status().choice());
        assertTrue(settings.overrides().isEmpty());
    }

    @Test public void aNewProcessRestoresWhatTheOldOneLeftBehind() throws Exception {
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA, DJI);
        call(remote, "com.discord");
        connect(remote);
        assertTrue(RouteTrial.holds(remote.audio.input, DJI));

        hangUp(remote);
        RouteController restarted = new RouteController(context);
        assertTrue(restarted.pending());
        assertEquals(RouteController.State.RECOVERY, restarted.state());
        ReflectionHelpers.setField(restarted, "service", remote);
        refresh(restarted);
        assertTrue(remote.audio.input.isEmpty());
        assertTrue(remote.audio.output.isEmpty());
        assertFalse(restarted.pending());
    }

    @Test public void theRecordFromVersion35IsStillRestored() throws Exception {
        assertTrue(journal.edit().putBoolean("pending", true).putString("receiver", DJI.address())
                .putString("earbuds", JABRA.address()).commit());
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA, DJI);
        remote.audio.input.add(DJI);
        remote.audio.output.add(JABRA);
        RouteController routes = new RouteController(context);
        assertTrue(routes.pending());
        ReflectionHelpers.setField(routes, "service", remote);
        refresh(routes);
        assertTrue(remote.audio.input.isEmpty());
        assertTrue(remote.audio.output.isEmpty());
        assertFalse(routes.pending());
    }

    @Test public void aDeadServiceKeepsTheRecord() throws Exception {
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA, DJI);
        call(remote, "com.discord");
        RouteController routes = connect(remote);
        remote.dead = true;
        hangUp(remote);
        refresh(routes);
        assertFalse(routes.connected());
        assertTrue(routes.pending());
        assertEquals(RouteController.State.RECOVERY, routes.state());
    }

    @Test public void eventsDuringARouteCoalesceIntoOneMorePass() throws Exception {
        FakeRouteService remote = new FakeRouteService(PHONE_MIC, SPEAKER, JABRA, DJI);
        call(remote, "com.discord");
        remote.holdRoute = new CountDownLatch(1);
        RouteController routes = new RouteController(context);
        ReflectionHelpers.setField(routes, "service", remote);
        routes.refresh();
        try {
            assertTrue(remote.enteredRoute.await(2, TimeUnit.SECONDS));
            assertEquals(RouteController.State.WORKING, routes.state());
            routes.refresh();
            routes.refresh();
            routes.pickForCall(Settings.OFF);
        } finally {
            remote.holdRoute.countDown();
        }
        FakeRouteService.awaitIdle(routes);
        assertEquals(2, remote.routeCalls);
        assertTrue(remote.audio.input.isEmpty());
        assertEquals("Off", routes.headline());
    }
}
