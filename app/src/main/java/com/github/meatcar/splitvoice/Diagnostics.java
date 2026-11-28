package com.github.meatcar.splitvoice;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.os.Build;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import rikka.shizuku.Shizuku;

// NOTE: bug-report summary; must never include device addresses, names or identifiers
final class Diagnostics {
    private static final Map<Integer, String> TYPES = Map.ofEntries(
            Map.entry(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, "built-in earpiece"),
            Map.entry(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "built-in speaker"),
            Map.entry(AudioDeviceInfo.TYPE_WIRED_HEADSET, "wired headset"),
            Map.entry(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "wired headphones"),
            Map.entry(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "bluetooth SCO"),
            Map.entry(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "bluetooth A2DP"),
            Map.entry(AudioDeviceInfo.TYPE_USB_DEVICE, "USB device"),
            Map.entry(AudioDeviceInfo.TYPE_USB_ACCESSORY, "USB accessory"),
            Map.entry(AudioDeviceInfo.TYPE_USB_HEADSET, "USB headset"),
            Map.entry(AudioDeviceInfo.TYPE_BUILTIN_MIC, "built-in mic"),
            Map.entry(AudioDeviceInfo.TYPE_HEARING_AID, "hearing aid"),
            Map.entry(AudioDeviceInfo.TYPE_BLE_HEADSET, "LE headset"),
            Map.entry(AudioDeviceInfo.TYPE_BLE_SPEAKER, "LE speaker"),
            Map.entry(AudioDeviceInfo.TYPE_BLE_BROADCAST, "LE broadcast"));

    static String report(Context context, RouteController routes) {
        StringBuilder out = new StringBuilder("Split Voice diagnostics\n");
        add(out, "App", version(context));
        add(out, "Android", Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        add(out, "Phone", Build.MANUFACTURER + " " + Build.MODEL);
        add(out, "Build", Build.DISPLAY);
        add(out, "Shizuku", shizuku());
        add(out, "State", routes.headline() + ": " + (routes.message() == null ? "" : routes.message()));
        add(out, "Recovery pending", String.valueOf(routes.pending()));
        RouteController.Status status = routes.status();
        if (status == null) add(out, "Routes", "not inspected");
        else {
            add(out, "Voice chat", String.valueOf(status.call()));
            add(out, "Voice app", status.app() == null ? "unknown" : "detected");
            add(out, "Using", routes.label(status.choice()) + (status.picked() ? " (this call)" : ""));
            add(out, "Mic", side(status.mic()));
            add(out, "Headphones", side(status.headphones()));
            add(out, "Refused", types(status.refused()));
            add(out, "Inputs", types(status.devices().stream().filter(Device::input).toList()));
            add(out, "Outputs", types(status.devices().stream().filter(device -> !device.input()).toList()));
        }
        return out.toString();
    }

    private static String side(RouteController.Side side) {
        String actual = types(side.actual());
        if (side.foreign()) return "another app chose " + actual;
        if (side.wanted() == null) return "Android's choice" + (side.actual().isEmpty() ? "" : ", set to " + actual);
        return (side.split() ? "set to " : "not kept: ") + type(side.wanted().type());
    }

    static String type(int value) { return TYPES.getOrDefault(value, "type " + value); }

    static String types(List<Device> devices) {
        if (devices.isEmpty()) return "none";
        StringJoiner joiner = new StringJoiner(", ");
        for (Device device : devices) joiner.add(type(device.type()));
        return joiner.toString();
    }

    private static void add(StringBuilder out, String name, String value) {
        out.append(name).append(": ").append(value).append('\n');
    }

    private static String version(Context context) {
        try {
            var info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    private static String shizuku() {
        try {
            if (!Shizuku.pingBinder()) return "not running";
            return String.format(Locale.ROOT, "running, version %d, uid %d, permission %s", Shizuku.getVersion(),
                    Shizuku.getUid(), Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                            ? "granted" : "not granted");
        } catch (Exception e) {
            return "unreadable (" + e.getClass().getSimpleName() + ")";
        }
    }
}
