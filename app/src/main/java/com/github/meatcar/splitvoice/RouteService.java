package com.github.meatcar.splitvoice;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

import java.util.ArrayList;
import java.util.List;

public final class RouteService extends IRouteService.Stub {
    static final int SHELL_UID = 2000;
    private final RouteTrial.Backend backend;

    public RouteService() throws Exception {
        RouteTrial.require(Process.myUid() == SHELL_UID, "Shell UID 2000 required; root is not supported.");
        backend = new AudioServiceBackend();
    }

    private AudioDeviceInfo[] devices() throws Exception {
        return (AudioDeviceInfo[]) AudioManager.class.getMethod("getDevicesStatic", int.class)
                .invoke(null, AudioManager.GET_DEVICES_ALL);
    }

    private Bundle inspectRoutes(String receiver, String earbuds) throws Exception {
        Bundle result = new Bundle();
        List<AudioDeviceInfo> inputs = new ArrayList<>();
        List<AudioDeviceInfo> outputs = new ArrayList<>();
        List<Integer> inputTypes = new ArrayList<>();
        List<Integer> outputTypes = new ArrayList<>();
        for (AudioDeviceInfo device : devices()) {
            if (device.isSource()) inputTypes.add(device.getType());
            if (device.isSink()) outputTypes.add(device.getType());
            if (device.isSource() && device.getType() == AudioDeviceInfo.TYPE_USB_DEVICE) inputs.add(device);
            if (device.isSink() && device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET) outputs.add(device);
        }
        result.putIntArray("inputTypes", inputTypes.stream().mapToInt(Integer::intValue).sorted().toArray());
        result.putIntArray("outputTypes", outputTypes.stream().mapToInt(Integer::intValue).sorted().toArray());
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
                && RouteTrial.matches(backend, RouteTrial.Device.receiver(receiver),
                RouteTrial.Device.headphones(earbuds)));
        result.putBoolean("ok", true);
        return result;
    }

    private synchronized Bundle execute(String operation, String receiver, String earbuds) {
        long identity = Binder.clearCallingIdentity();
        try {
            if (operation.equals("inspect")) return inspectRoutes(receiver, earbuds);
            RouteTrial.require(receiver != null && earbuds != null, "Missing recovery device identities. Preserve app data.");
            RouteTrial.Device input = RouteTrial.Device.receiver(receiver);
            RouteTrial.Device output = RouteTrial.Device.headphones(earbuds);
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
