package com.github.meatcar.splitvoice;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

final class FakeRouteService extends IRouteService.Stub {
    private final SharedPreferences journal;
    FakeRouteService(SharedPreferences journal) { this.journal = journal; }

    boolean applied;
    boolean dead;
    boolean failApply;
    boolean failRestore;
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
        result.putBoolean("ok", mode == 0 && !failRestore);
        if (mode == 0 && !failRestore) applied = false;
        else result.putString("message", failRestore ? "Restore failed" : "Stop Voice before restoring.");
        return result;
    }

    static void awaitIdle(RouteController routes) throws Exception {
        for (int i = 0; i < 400; i++) {
            shadowOf(Looper.getMainLooper()).idle();
            if (!routes.busy()) return;
            Thread.sleep(5);
        }
        fail("Routing worker did not finish");
    }
}
