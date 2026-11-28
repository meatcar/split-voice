package com.github.meatcar.splitvoice;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

final class RouteController {
    enum State { SETUP, WORKING, RECOVERY, ERROR, READY, CALL }

    record Side(Device wanted, List<Device> actual, boolean foreign) {
        boolean split() { return wanted != null && RouteTrial.holds(actual, wanted); }
    }

    record Status(boolean call, List<Device> devices, String app, String choice, boolean picked,
                  Side mic, Side headphones, List<Device> refused) {
        Side side(boolean input) { return input ? mic : headphones; }
        boolean split() { return mic.split() || headphones.split(); }
    }

    private final Context context;
    private final Settings settings;
    private final SharedPreferences journal;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Runnable> listeners = new ArrayList<>();
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsChanged = (prefs, key) -> refresh();
    private final Runnable connectionTimeout = this::connectionTimedOut;
    private final Shizuku.UserServiceArgs arguments = new Shizuku.UserServiceArgs(
            new ComponentName("com.github.meatcar.splitvoice", RouteService.class.getName()))
            .daemon(false).processNameSuffix("routes").debuggable(false).version(2);
    private IRouteService service;
    private boolean busy;
    private boolean dirty;
    private boolean binding;
    private Status status;
    private String message;
    private volatile String pick;
    private final Map<String, Device> refused = new LinkedHashMap<>();
    private String app;

    RouteController(Context context) {
        this.context = context.getApplicationContext();
        settings = new Settings(this.context);
        message = string(R.string.start_shizuku);
        journal = this.context.getSharedPreferences("routing-recovery", Context.MODE_PRIVATE);
        migrateJournal();
        this.context.getSharedPreferences(Settings.NAME, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(settingsChanged);
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
                message = null;
                refresh();
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) { main.post(RouteController.this::disconnected); }
    };

    private void disconnected() {
        main.removeCallbacks(connectionTimeout);
        service = null;
        binding = false;
        status = null;
        show(string(pending() ? R.string.start_shizuku_restore : R.string.start_shizuku));
    }

    void connect(boolean allowPermissionPrompt) {
        if (binding || service != null) return;
        try {
            if (!Shizuku.pingBinder()) {
                show(string(pending() ? R.string.start_shizuku_restore : R.string.start_shizuku));
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (allowPermissionPrompt) Shizuku.requestPermission(1);
                else show(string(R.string.allow_shizuku));
                return;
            }
            if (Shizuku.getUid() != RouteService.SHELL_UID) {
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
        else reconcile();
    }

    void pickForCall(String choice) {
        pick = status != null && choice.equals(settings.choiceFor(status.app())) ? null : choice;
        refresh();
    }

    private void reconcile() {
        if (binding || service == null) return;
        if (busy) {
            dirty = true;
            return;
        }
        busy = true;
        dirty = false;
        IRouteService remote = service;
        if (status == null) notifyListeners();
        worker.execute(() -> {
            Status result = null;
            String failure = null;
            boolean lost = false;
            try {
                result = reconcile(remote);
            } catch (RemoteException e) {
                lost = true;
            } catch (IllegalStateException e) {
                failure = e.getMessage();
            } catch (Exception e) {
                failure = string(R.string.connection_failed);
            }
            Status completed = result;
            String error = failure;
            boolean disconnected = lost;
            main.post(() -> {
                busy = false;
                if (remote != service) return;
                if (disconnected) {
                    disconnected();
                    return;
                }
                if (completed != null && !completed.call()) pick = null;
                status = completed;
                message = error;
                notifyListeners();
                if (dirty) reconcile();
            });
        });
    }

    private Status reconcile(IRouteService remote) throws Exception {
        Bundle inspected = require(remote.inspect());
        boolean call = inspected.getInt("mode") == AudioManager.MODE_IN_COMMUNICATION;
        List<Device> devices = Device.fromBundles(inspected, "devices");
        String picked = call ? pick : null;
        if (!call) {
            refused.clear();
            app = null;
        } else if (inspected.getString("app") != null) {
            app = inspected.getString("app");
            settings.markSeen(app);
        }
        String choice = call ? (picked != null && settings.valid(picked) ? picked : settings.choiceFor(app)) : Settings.OFF;
        while (true) {
            Routing.Plan plan = Routing.plan(choice, settings.profile(choice), devices, refused.keySet());
            List<Device> mics = recorded("input");
            List<Device> outs = recorded("output");
            record(with(mics, plan.mic()), with(outs, plan.headphones()));
            Bundle request = new Bundle();
            request.putParcelableArrayList("inputExpected", Device.bundles(mics));
            request.putParcelableArrayList("outputExpected", Device.bundles(outs));
            if (plan.mic() != null) request.putBundle("inputWanted", plan.mic().toBundle());
            if (plan.headphones() != null) request.putBundle("outputWanted", plan.headphones().toBundle());
            Bundle routed = require(remote.route(request));
            record(owned(routed, "input"), owned(routed, "output"));
            boolean retry = false;
            for (boolean input : new boolean[]{true, false}) {
                Device wanted = plan.side(input);
                if (wanted != null && routed.getBoolean((input ? "input" : "output") + "Refused")) {
                    refused.put(wanted.key(), wanted);
                    retry = true;
                }
            }
            if (!retry) {
                return new Status(call, devices, app, choice, picked != null && picked.equals(choice),
                        side(plan.mic(), routed, "input"), side(plan.headphones(), routed, "output"),
                        List.copyOf(refused.values()));
            }
        }
    }

    private static Side side(Device wanted, Bundle routed, String key) {
        return new Side(wanted, Device.fromBundles(routed, key), routed.getBoolean(key + "Foreign"));
    }

    private static List<Device> with(List<Device> devices, Device device) {
        List<Device> result = new ArrayList<>(devices);
        if (device != null && devices.stream().noneMatch(device::sameEndpoint)) result.add(device);
        return result;
    }

    private static List<Device> owned(Bundle routed, String side) {
        Device device = Device.fromBundle(routed.getBundle(side + "Owned"));
        return device == null ? List.of() : List.of(device);
    }

    private List<Device> recorded(String side) {
        List<Device> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(journal.getString(side, "[]"));
            for (int i = 0; i < array.length(); i++) result.add(Device.fromJson(array.getJSONObject(i)));
        } catch (JSONException e) {
            // NOTE: unreadable record; the next route call rewrites it
        }
        return result;
    }

    private void record(List<Device> inputs, List<Device> outputs) throws JSONException {
        RouteTrial.require(journal.edit().putString("input", json(inputs)).putString("output", json(outputs)).commit(),
                string(R.string.cannot_save_recovery));
    }

    private static String json(List<Device> devices) throws JSONException {
        JSONArray array = new JSONArray();
        for (Device device : devices) array.put(device.toJson());
        return array.toString();
    }

    // NOTE: 0.1.35 and earlier recorded one USB receiver and one LE Audio headset
    private void migrateJournal() {
        if (!journal.getBoolean("pending", false)) return;
        String receiver = journal.getString("receiver", null);
        String earbuds = journal.getString("earbuds", null);
        try {
            SharedPreferences.Editor editor = journal.edit().clear();
            if (receiver != null) editor.putString("input",
                    json(List.of(new Device(0, true, android.media.AudioDeviceInfo.TYPE_USB_DEVICE, receiver, ""))));
            if (earbuds != null) editor.putString("output",
                    json(List.of(new Device(0, false, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET, earbuds, ""))));
            editor.apply();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private Bundle require(Bundle result) {
        RouteTrial.require(result != null && result.getBoolean("ok"),
                result == null ? string(R.string.no_result) : result.getString("message", string(R.string.check_failed)));
        return result;
    }

    private String string(int id, Object... args) { return context.getString(id, args); }

    State state() {
        if (binding || (service != null && status == null && busy)) return State.WORKING;
        if (service == null) return pending() ? State.RECOVERY : State.SETUP;
        if (status == null) return State.ERROR;
        return status.call() ? State.CALL : State.READY;
    }

    List<String> choices() {
        List<String> result = new ArrayList<>();
        result.add(Settings.AUTO);
        for (Profile profile : settings.profiles()) result.add(profile.id());
        result.add(Settings.OFF);
        return result;
    }

    String nextChoice() {
        List<String> choices = choices();
        int index = choices.indexOf(status == null ? Settings.AUTO : status.choice());
        for (int step = 1; step <= choices.size(); step++) {
            String choice = choices.get((index + step) % choices.size());
            Profile profile = settings.profile(choice);
            if (profile == null || status == null) return choice;
            Routing.Plan plan = Routing.plan(choice, profile, status.devices(), Set.of());
            if (plan.mic() != null || plan.headphones() != null) return choice;
        }
        return Settings.OFF;
    }

    record Line(String device, String why) {}

    String headline() {
        return switch (state()) {
            case SETUP -> string(R.string.title_setup);
            case WORKING -> string(R.string.title_working);
            case RECOVERY -> string(R.string.title_recovery);
            case ERROR -> string(R.string.title_error);
            case READY -> string(settings.anythingOn() ? R.string.title_ready : R.string.title_off);
            case CALL -> string(Settings.OFF.equals(status.choice()) ? R.string.title_off
                    : status.split() ? R.string.title_split : R.string.title_not_split);
        };
    }

    String detail() {
        State state = state();
        if (state == State.CALL) {
            String detail = string(R.string.call_detail, appLabel(status.app()), label(status.choice()));
            return status.picked() ? string(R.string.call_detail_picked, detail) : detail;
        }
        if (message != null) return message;
        if (state == State.READY) return string(settings.anythingOn() ? R.string.ready_detail : R.string.off_detail);
        return "";
    }

    Line line(boolean input) {
        Side side = status.side(input);
        List<String> refusedHere = new ArrayList<>();
        for (Device device : status.refused()) if (device.input() == input) refusedHere.add(device.label(context));
        if (side.foreign()) {
            Device chosen = side.actual().isEmpty() ? null : withName(side.actual().get(0));
            return new Line(chosen == null ? string(R.string.android_choice) : chosen.label(context),
                    string(R.string.why_foreign));
        }
        if (side.wanted() != null) return new Line(side.wanted().label(context), refused(refusedHere));
        String why;
        if (!refusedHere.isEmpty()) why = refused(refusedHere);
        else if (Settings.AUTO.equals(status.choice())) {
            why = input ? string(R.string.why_no_external_mic)
                    : status.mic().wanted() != null ? string(R.string.why_no_headphones) : null;
        } else {
            Profile profile = settings.profile(status.choice());
            why = profile == null || profile.side(input).isEmpty() ? null
                    : string(input ? R.string.why_no_profile_mic : R.string.why_no_profile_headphones);
        }
        return new Line(string(R.string.android_choice), why);
    }

    private String refused(List<String> devices) {
        return devices.isEmpty() ? null : string(R.string.why_refused, String.join(", ", devices));
    }

    String label(String choice) {
        if (Settings.AUTO.equals(choice)) return string(R.string.choice_auto);
        Profile profile = settings.profile(choice);
        return profile == null ? string(R.string.choice_off) : profile.name();
    }

    String appLabel(String packageName) {
        if (packageName == null) return string(R.string.unknown_app);
        PackageManager packages = context.getPackageManager();
        try { return packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString(); }
        catch (PackageManager.NameNotFoundException e) { return packageName; }
    }

    Device withName(Device device) {
        if (device == null || status == null) return device;
        for (Device connected : status.devices()) if (connected.sameEndpoint(device)) return connected;
        return device;
    }

    Settings settings() { return settings; }
    boolean shizukuRunning() { return Shizuku.pingBinder(); }
    boolean pending() { return !recorded("input").isEmpty() || !recorded("output").isEmpty(); }
    boolean busy() { return busy || binding; }
    boolean connected() { return service != null; }
    String message() { return message; }
    Status status() { return status; }
    void observe(Runnable listener) { listeners.add(listener); listener.run(); }
    void removeObserver(Runnable listener) { listeners.remove(listener); }
    private void show(String value) { message = value; notifyListeners(); }
    private void notifyListeners() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
}
