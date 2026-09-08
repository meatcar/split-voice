package com.github.meatcar.splitvoice;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public final class RouteService extends IRouteService.Stub {
    private final Class<?> api;
    private final Object audio;
    private final RouteTrial.Backend backend;

    public RouteService() throws Exception {
        RouteTrial.require(Process.myUid() == 2000, "Shell UID 2000 required; root is not supported.");
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "audio");
        api = Class.forName("android.media.IAudioService");
        audio = Class.forName("android.media.IAudioService$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
        backend = new RouteTrial.Backend() {
            public int mode() throws Exception { return (Integer) call("getMode", new Class<?>[0]); }
            public boolean sco() throws Exception { return (Boolean) call("isBluetoothScoOn", new Class<?>[0]); }
            public List<RouteTrial.Device> input() throws Exception { return preferences(true); }
            public List<RouteTrial.Device> output() throws Exception { return preferences(false); }
            public void input(List<RouteTrial.Device> devices) throws Exception { set(true, devices); }
            public void output(List<RouteTrial.Device> devices) throws Exception { set(false, devices); }
        };
    }

    private Object call(String method, Class<?>[] types, Object... args) throws Exception {
        try {
            return api.getMethod(method, types).invoke(audio, args);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("AudioService rejected " + method, e.getCause());
        }
    }

    private List<RouteTrial.Device> preferences(boolean input) throws Exception {
        List<?> raw = (List<?>) call(input ? "getPreferredDevicesForCapturePreset" : "getPreferredDevicesForStrategy",
                new Class<?>[]{int.class}, input ? 7 : 1);
        List<RouteTrial.Device> result = new ArrayList<>();
        for (Object device : raw) {
            Class<?> cls = device.getClass();
            result.add(new RouteTrial.Device((Integer) cls.getMethod("getRole").invoke(device),
                    (Integer) cls.getMethod("getType").invoke(device),
                    (String) cls.getMethod("getAddress").invoke(device)));
        }
        return result;
    }

    private void set(boolean input, List<RouteTrial.Device> devices) throws Exception {
        if (devices.isEmpty() && preferences(input).isEmpty()) return;
        String method;
        int result;
        if (devices.isEmpty()) {
            method = input ? "clearPreferredDevicesForCapturePreset" : "removePreferredDevicesForStrategy";
            result = (Integer) call(method, new Class<?>[]{int.class}, input ? 7 : 1);
        } else {
            List<Object> attributes = new ArrayList<>();
            for (RouteTrial.Device device : devices) {
                attributes.add(Class.forName("android.media.AudioDeviceAttributes")
                        .getConstructor(int.class, int.class, String.class)
                        .newInstance(device.role(), device.type(), device.address()));
            }
            method = input ? "setPreferredDevicesForCapturePreset" : "setPreferredDevicesForStrategy";
            result = (Integer) call(method, new Class<?>[]{int.class, List.class}, input ? 7 : 1, attributes);
        }
        RouteTrial.require(result == 0, method + " returned " + result);
    }

    private AudioDeviceInfo[] devices() throws Exception {
        return (AudioDeviceInfo[]) AudioManager.class.getMethod("getDevicesStatic", int.class)
                .invoke(null, AudioManager.GET_DEVICES_ALL);
    }

    private Bundle inspectRoutes(String receiver, String earbuds) throws Exception {
        Bundle result = new Bundle();
        List<AudioDeviceInfo> inputs = new ArrayList<>();
        List<AudioDeviceInfo> outputs = new ArrayList<>();
        for (AudioDeviceInfo device : devices()) {
            if (device.isSource() && device.getType() == AudioDeviceInfo.TYPE_USB_DEVICE) inputs.add(device);
            if (device.isSink() && device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET) outputs.add(device);
        }
        result.putInt("mode", backend.mode());
        result.putBoolean("sco", backend.sco());
        result.putBoolean("empty", backend.input().isEmpty() && backend.output().isEmpty());
        result.putBoolean("devicesReady", inputs.size() == 1 && outputs.size() == 1);
        if (result.getBoolean("devicesReady")) {
            result.putString("receiver", inputs.get(0).getAddress());
            result.putString("earbuds", outputs.get(0).getAddress());
        }
        boolean sameDevices = receiver != null && earbuds != null && result.getBoolean("devicesReady")
                && receiver.equals(result.getString("receiver")) && earbuds.equals(result.getString("earbuds"));
        result.putBoolean("matches", sameDevices && !result.getBoolean("sco")
                && RouteTrial.matches(backend, new RouteTrial.Device(1, 11, receiver),
                new RouteTrial.Device(2, 26, earbuds)));
        result.putBoolean("ok", true);
        return result;
    }

    private synchronized Bundle execute(String operation, String receiver, String earbuds) {
        long identity = Binder.clearCallingIdentity();
        try {
            if (operation.equals("inspect")) return inspectRoutes(receiver, earbuds);
            RouteTrial.require(receiver != null && earbuds != null, "Missing recovery device identities. Preserve app data.");
            RouteTrial.Device input = new RouteTrial.Device(1, 11, receiver);
            RouteTrial.Device output = new RouteTrial.Device(2, 26, earbuds);
            if (operation.equals("apply")) {
                Bundle current = inspectRoutes(receiver, earbuds);
                RouteTrial.require(current.getBoolean("devicesReady") && receiver.equals(current.getString("receiver"))
                        && earbuds.equals(current.getString("earbuds")), "Devices changed. Check again.");
                RouteTrial.apply(backend, input, output);
            } else {
                RouteTrial.restore(backend, input, output);
            }
            Bundle result = new Bundle();
            result.putBoolean("ok", true);
            result.putString("message", operation.equals("apply")
                    ? "Split selected."
                    : "Restored.");
            return result;
        } catch (Exception e) {
            Bundle result = new Bundle();
            result.putBoolean("ok", false);
            result.putString("message", e instanceof IllegalStateException ? e.getMessage()
                    : "Unsupported or unavailable API: " + e.getClass().getSimpleName());
            return result;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override public Bundle inspect(String receiver, String earbuds) { return execute("inspect", receiver, earbuds); }
    @Override public Bundle apply(String receiver, String earbuds) { return execute("apply", receiver, earbuds); }
    @Override public Bundle restore(String receiver, String earbuds) { return execute("restore", receiver, earbuds); }
    @Override public void destroy() { System.exit(0); }
}
