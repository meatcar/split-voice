package com.github.meatcar.splitvoice;

import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

public final class RouteService extends IRouteService.Stub {
    static final int SHELL_UID = 2000;
    private final RouteTrial.Backend backend;

    public RouteService() throws Exception {
        RouteTrial.require(Process.myUid() == SHELL_UID, "Shizuku must run with wireless debugging, not root.");
        backend = new AudioServiceBackend();
    }

    private interface Operation { void run(Bundle result) throws Exception; }

    private synchronized Bundle execute(Operation operation) {
        long identity = Binder.clearCallingIdentity();
        Bundle result = new Bundle();
        try {
            operation.run(result);
            result.putBoolean("ok", true);
        } catch (Exception e) {
            result.putBoolean("ok", false);
            result.putString("message", e instanceof IllegalStateException ? e.getMessage()
                    : "This Android version rejected the routing call (" + e.getClass().getSimpleName() + "). Send diagnostics.");
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return result;
    }

    @Override public Bundle inspect() { return execute(result -> RouteTrial.inspect(backend, result)); }
    @Override public Bundle route(Bundle request) { return execute(result -> RouteTrial.route(backend, request, result)); }
    @Override public void destroy() { System.exit(0); }
}
