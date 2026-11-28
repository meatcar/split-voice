package com.github.meatcar.splitvoice;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.media.AudioManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** The real shell routing logic over an in-memory audio server. */
final class FakeRouteService extends IRouteService.Stub {
    final RouteTrialTest.MemoryBackend audio = new RouteTrialTest.MemoryBackend();
    boolean dead;
    int routeCalls;
    Runnable beforeRoute = () -> { };
    CountDownLatch holdRoute;
    final CountDownLatch enteredRoute = new CountDownLatch(1);

    FakeRouteService(Device... devices) {
        audio.devices.addAll(List.of(devices));
        audio.mode = AudioManager.MODE_NORMAL;
    }

    @Override public void destroy() { fail("Unexpected service destruction"); }

    @Override public Bundle inspect() throws RemoteException {
        if (dead) throw new RemoteException("Service died");
        Bundle result = new Bundle();
        try { RouteTrial.inspect(audio, result); }
        catch (Exception e) { throw new AssertionError(e); }
        result.putBoolean("ok", true);
        return result;
    }

    @Override public Bundle route(Bundle request) throws RemoteException {
        if (dead) throw new RemoteException("Service died");
        routeCalls++;
        beforeRoute.run();
        enteredRoute.countDown();
        if (holdRoute != null) {
            try { assertTrue(holdRoute.await(2, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); }
        }
        Bundle result = new Bundle();
        try { RouteTrial.route(audio, request, result); }
        catch (Exception e) { throw new AssertionError(e); }
        result.putBoolean("ok", true);
        return result;
    }

    static void awaitIdle(RouteController routes) throws Exception {
        for (int i = 0; i < 400; i++) {
            shadowOf(Looper.getMainLooper()).idle();
            if (!routes.busy()) {
                shadowOf(Looper.getMainLooper()).idle();
                if (!routes.busy()) return;
            }
            Thread.sleep(5);
        }
        fail("Routing worker did not finish");
    }
}
