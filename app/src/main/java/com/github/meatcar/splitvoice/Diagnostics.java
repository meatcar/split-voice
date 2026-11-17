package com.github.meatcar.splitvoice;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.os.Build;
import android.os.Bundle;

import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import rikka.shizuku.Shizuku;

/** Builds a bug-report summary. Never includes device addresses, names or other identifiers. */
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
        add(out, "State", routes.title() + ": " + routes.message());
        add(out, "Recovery pending", String.valueOf(routes.pending()));
        Bundle inspection = routes.inspection();
        if (inspection == null) add(out, "Routes", "not inspected");
        else {
            add(out, "Audio mode", String.valueOf(inspection.getInt("mode")));
            add(out, "Bluetooth SCO", String.valueOf(inspection.getBoolean("sco")));
            add(out, "No preferences set", String.valueOf(inspection.getBoolean("empty")));
            add(out, "One receiver and one LE headset", String.valueOf(inspection.getBoolean("devicesReady")));
            add(out, "Split matches", String.valueOf(inspection.getBoolean("matches")));
            add(out, "Input types", types(inspection.getIntArray("inputTypes")));
            add(out, "Output types", types(inspection.getIntArray("outputTypes")));
        }
        return out.toString();
    }

    static String types(int[] values) {
        if (values == null || values.length == 0) return "none";
        StringJoiner joiner = new StringJoiner(", ");
        for (int value : values) joiner.add(TYPES.getOrDefault(value, "type " + value));
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
