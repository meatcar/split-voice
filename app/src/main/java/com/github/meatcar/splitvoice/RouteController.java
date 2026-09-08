package com.github.meatcar.splitvoice;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

final class RouteController {
    enum State { UNAVAILABLE, WORKING, READY, ACTIVE, RECOVERY, BLOCKED }

    private final SharedPreferences journal;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Runnable> listeners = new ArrayList<>();
    private IRouteService service;
    private boolean busy;
    private boolean binding;
    private final Runnable connectionTimeout = this::connectionTimedOut;
    private Bundle inspection;
    private String message = "Start Shizuku, then connect.";
    private final Shizuku.UserServiceArgs arguments = new Shizuku.UserServiceArgs(
            new ComponentName("com.github.meatcar.splitvoice", RouteService.class.getName()))
            .daemon(false).processNameSuffix("routes").debuggable(false).version(1);

    RouteController(Context context) {
        journal = context.getSharedPreferences("routing-recovery", Context.MODE_PRIVATE);
        Shizuku.addBinderReceivedListenerSticky(() -> main.post(this::refresh));
        Shizuku.addBinderDeadListener(() -> main.post(this::disconnected));
        Shizuku.addRequestPermissionResultListener((request, result) -> {
            if (request == 1) main.post(() -> {
                if (result == PackageManager.PERMISSION_GRANTED) connect(false);
                else show("Shizuku permission denied.");
            });
        });
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            main.post(() -> {
                main.removeCallbacks(connectionTimeout);
                binding = false;
                service = IRouteService.Stub.asInterface(binder);
                refresh();
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) { main.post(RouteController.this::disconnected); }
    };

    private void disconnected() {
        main.removeCallbacks(connectionTimeout);
        service = null;
        binding = false;
        inspection = null;
        show(pending() ? "Start Shizuku to restore." : "Start Shizuku, then connect.");
    }

    void connect(boolean allowPermissionPrompt) {
        if (busy || binding || service != null) return;
        try {
            if (!Shizuku.pingBinder()) {
                show(pending() ? "Start Shizuku to restore." : "Start Shizuku, then connect.");
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (allowPermissionPrompt) Shizuku.requestPermission(1);
                else show("Tap Connect to allow Shizuku access.");
                return;
            }
            if (Shizuku.getUid() != 2000) {
                show("Start Shizuku using wireless debugging, not root.");
                return;
            }
            binding = true;
            Shizuku.bindUserService(arguments, connection);
            show("Connecting to Shizuku...");
            main.postDelayed(connectionTimeout, 15000);
        } catch (Exception e) {
            main.removeCallbacks(connectionTimeout);
            binding = false;
            show("Cannot connect: " + e.getClass().getSimpleName());
        }
    }

    private void connectionTimedOut() {
        if (!binding) return;
        try { Shizuku.unbindUserService(arguments, connection, false); }
        catch (Exception ignored) { /* NOTE: A stopped binder cannot be unbound. */ }
        disconnected();
        show("Connection timed out. Restart Shizuku.");
    }

    void refresh() {
        if (service == null) connect(false);
        else execute(false, false);
    }

    void toggle() { execute(true, pending()); }
    void restore() { if (pending()) execute(true, true); }

    private void execute(boolean change, boolean restoring) {
        if (busy || binding || service == null) return;
        IRouteService remote = service;
        busy = true;
        if (change) show(restoring ? "Restoring..." : "Applying split...");
        else notifyListeners();
        worker.execute(() -> {
            Bundle checked = null;
            String outcome = null;
            boolean transportLost = false;
            try {
                if (change) {
                    Bundle result;
                    if (restoring) {
                        result = remote.restore(journal.getString("receiver", null), journal.getString("earbuds", null));
                        requireSuccess(result);
                        RouteTrial.require(journal.edit().clear().commit(), "Routes restored; recovery record could not be cleared.");
                    } else {
                        Bundle selection = remote.inspect(null, null);
                        requireSuccess(selection);
                        RouteTrial.require(selection.getBoolean("empty"), "Existing preferences found. Nothing changed.");
                        RouteTrial.require(selection.getBoolean("devicesReady"), "Connect exactly one receiver input and one LE headset output.");
                        RouteTrial.require(selection.getInt("mode") == 3, "Start Voice before applying.");
                        RouteTrial.require(!selection.getBoolean("sco"), "SCO is active. Use the physical receiver for input.");
                        String receiver = selection.getString("receiver");
                        String earbuds = selection.getString("earbuds");
                        RouteTrial.require(receiver != null && earbuds != null, "Device identities unavailable. Nothing changed.");
                        RouteTrial.require(journal.edit().putBoolean("pending", true)
                                .putString("receiver", receiver).putString("earbuds", earbuds).commit(),
                                "Cannot save recovery state. Nothing applied.");
                        result = remote.apply(receiver, earbuds);
                        requireSuccess(result);
                    }
                    outcome = result.getString("message");
                }
                checked = remote.inspect(journal.getString("receiver", null), journal.getString("earbuds", null));
                requireSuccess(checked);
            } catch (Exception e) {
                checked = null;
                transportLost = e instanceof RemoteException;
                outcome = e instanceof IllegalStateException ? e.getMessage()
                        : "Connection failed. Reconnect Shizuku.";
            }
            Bundle completed = checked;
            String feedback = outcome;
            boolean disconnected = transportLost;
            main.post(() -> {
                busy = false;
                if (disconnected && remote == service) disconnected();
                inspection = remote == service ? completed : null;
                if (remote != service) show(pending() ? "Reconnect Shizuku to restore." : "Reconnect Shizuku.");
                else show(feedback == null ? guidance() : feedback);
            });
        });
    }

    private static void requireSuccess(Bundle result) {
        RouteTrial.require(result != null && result.getBoolean("ok"),
                result == null ? "No service result. State unknown." : result.getString("message", "Route check failed."));
    }

    State state() {
        if (busy || binding) return State.WORKING;
        if (pending()) return service != null && inspection != null && inspection.getBoolean("matches")
                ? State.ACTIVE : State.RECOVERY;
        if (service == null || inspection == null) return State.UNAVAILABLE;
        return inspection.getBoolean("empty") && inspection.getBoolean("devicesReady") && !inspection.getBoolean("sco")
                ? State.READY : State.BLOCKED;
    }

    String title() {
        return switch (state()) {
            case UNAVAILABLE -> "Shizuku unavailable";
            case WORKING -> "Checking routes";
            case READY -> "Ready to split";
            case ACTIVE -> "Split selected";
            case RECOVERY -> "Recovery required";
            case BLOCKED -> "Split unavailable";
        };
    }

    private String guidance() {
        if (pending()) return "Stop Voice to restore.";
        if (inspection == null) return "Reconnect Shizuku.";
        if (!inspection.getBoolean("empty")) return "Another app has routing preferences set.";
        if (!inspection.getBoolean("devicesReady")) return "Connect one receiver and one LE headset.";
        if (inspection.getBoolean("sco")) return "Bluetooth mic active. Connect the physical receiver.";
        return "Start Voice, then tap the Quick Settings tile.";
    }

    boolean shizukuRunning() { return Shizuku.pingBinder(); }
    boolean pending() { return journal.getBoolean("pending", false); }
    boolean busy() { return busy || binding; }
    boolean connected() { return service != null; }
    String message() { return message; }
    void observe(Runnable listener) { listeners.add(listener); listener.run(); }
    void removeObserver(Runnable listener) { listeners.remove(listener); }
    private void show(String value) { message = value; notifyListeners(); }
    private void notifyListeners() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
}
