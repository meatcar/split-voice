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

    private final Context context;
    private final SharedPreferences journal;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Runnable> listeners = new ArrayList<>();
    private IRouteService service;
    private boolean busy;
    private boolean binding;
    private final Runnable connectionTimeout = this::connectionTimedOut;
    private Bundle inspection;
    private String message;
    private final Shizuku.UserServiceArgs arguments = new Shizuku.UserServiceArgs(
            new ComponentName("com.github.meatcar.splitvoice", RouteService.class.getName()))
            .daemon(false).processNameSuffix("routes").debuggable(false).version(1);

    RouteController(Context context) {
        this.context = context.getApplicationContext();
        message = string(R.string.start_shizuku);
        journal = context.getSharedPreferences("routing-recovery", Context.MODE_PRIVATE);
        Shizuku.addBinderReceivedListenerSticky(() -> main.post(this::refresh));
        Shizuku.addBinderDeadListener(() -> main.post(this::disconnected));
        Shizuku.addRequestPermissionResultListener((request, result) -> {
            if (request == 1) main.post(() -> {
                if (result == PackageManager.PERMISSION_GRANTED) connect(false);
                else show(string(R.string.permission_denied));
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
        show(pending() ? string(R.string.start_shizuku_restore) : string(R.string.start_shizuku));
    }

    void connect(boolean allowPermissionPrompt) {
        if (busy || binding || service != null) return;
        try {
            if (!Shizuku.pingBinder()) {
                show(pending() ? string(R.string.start_shizuku_restore) : string(R.string.start_shizuku));
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (allowPermissionPrompt) Shizuku.requestPermission(1);
                else show(string(R.string.allow_shizuku));
                return;
            }
            if (Shizuku.getUid() != 2000) {
                show(string(R.string.not_root));
                return;
            }
            binding = true;
            Shizuku.bindUserService(arguments, connection);
            show(string(R.string.connecting));
            main.postDelayed(connectionTimeout, 15000);
        } catch (Exception e) {
            main.removeCallbacks(connectionTimeout);
            binding = false;
            show(string(R.string.cannot_connect, e.getClass().getSimpleName()));
        }
    }

    private void connectionTimedOut() {
        if (!binding) return;
        try { Shizuku.unbindUserService(arguments, connection, false); }
        catch (Exception ignored) { /* NOTE: A stopped binder cannot be unbound. */ }
        disconnected();
        show(string(R.string.timed_out));
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
        if (change) show(restoring ? string(R.string.restoring) : string(R.string.applying));
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
                        RouteTrial.require(journal.edit().clear().commit(), string(R.string.record_not_cleared));
                    } else {
                        Bundle selection = remote.inspect(null, null);
                        requireSuccess(selection);
                        RouteTrial.require(selection.getBoolean("empty"), string(R.string.existing_preferences));
                        RouteTrial.require(selection.getBoolean("devicesReady"), string(R.string.connect_exactly_one));
                        RouteTrial.require(selection.getInt("mode") == 3, string(R.string.start_voice));
                        RouteTrial.require(!selection.getBoolean("sco"), string(R.string.sco_active));
                        String receiver = selection.getString("receiver");
                        String earbuds = selection.getString("earbuds");
                        RouteTrial.require(receiver != null && earbuds != null, string(R.string.identities_unavailable));
                        RouteTrial.require(journal.edit().putBoolean("pending", true)
                                .putString("receiver", receiver).putString("earbuds", earbuds).commit(),
                                string(R.string.cannot_save_recovery));
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
                        : string(R.string.connection_failed);
            }
            Bundle completed = checked;
            String feedback = outcome;
            boolean disconnected = transportLost;
            main.post(() -> {
                busy = false;
                if (disconnected && remote == service) disconnected();
                inspection = remote == service ? completed : null;
                if (remote != service) show(pending() ? string(R.string.reconnect_restore) : string(R.string.reconnect));
                else show(feedback == null ? guidance() : feedback);
            });
        });
    }

    private String string(int id, Object... args) { return context.getString(id, args); }

    private void requireSuccess(Bundle result) {
        RouteTrial.require(result != null && result.getBoolean("ok"),
                result == null ? string(R.string.no_result) : result.getString("message", string(R.string.check_failed)));
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
            case UNAVAILABLE -> string(R.string.title_unavailable);
            case WORKING -> string(R.string.title_working);
            case READY -> string(R.string.title_ready);
            case ACTIVE -> string(R.string.title_active);
            case RECOVERY -> string(R.string.title_recovery);
            case BLOCKED -> string(R.string.title_blocked);
        };
    }

    private String guidance() {
        if (pending()) return string(R.string.stop_voice);
        if (inspection == null) return string(R.string.reconnect);
        if (!inspection.getBoolean("empty")) return string(R.string.other_app_preferences);
        if (!inspection.getBoolean("devicesReady")) return string(R.string.connect_one);
        if (inspection.getBoolean("sco")) return string(R.string.bluetooth_mic);
        return string(R.string.start_voice_tap_tile);
    }

    boolean shizukuRunning() { return Shizuku.pingBinder(); }
    boolean pending() { return journal.getBoolean("pending", false); }
    boolean busy() { return busy || binding; }
    boolean connected() { return service != null; }
    String message() { return message; }
    Bundle inspection() { return inspection; }
    void observe(Runnable listener) { listeners.add(listener); listener.run(); }
    void removeObserver(Runnable listener) { listeners.remove(listener); }
    private void show(String value) { message = value; notifyListeners(); }
    private void notifyListeners() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
}
