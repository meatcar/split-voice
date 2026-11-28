package com.github.meatcar.splitvoice;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

record Device(int id, boolean input, int type, String address, String name) {
    static final Set<Integer> MICS = Set.of(AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL);
    static final Set<Integer> EXTERNAL_MICS = Set.of(AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADSET);
    static final Set<Integer> HEADPHONES = Set.of(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
    static final Set<Integer> OUTPUTS = Set.of(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
    private static final Set<Integer> BUILT_IN = Set.of(AudioDeviceInfo.TYPE_BUILTIN_MIC,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
    private static final Set<Integer> BLUETOOTH = Set.of(AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_HEARING_AID, AudioDeviceInfo.TYPE_BLUETOOTH_SCO);

    Device {
        address = address == null ? "" : address;
        name = name == null ? "" : name;
    }

    static Device of(AudioDeviceInfo info) {
        return new Device(info.getId(), info.isSource(), info.getType(), info.getAddress(),
                String.valueOf(info.getProductName()));
    }

    String label(Context context) {
        return switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC -> context.getString(R.string.phone_mic);
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> context.getString(R.string.phone_speaker);
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> context.getString(R.string.phone_earpiece);
            default -> name.isBlank() ? Diagnostics.type(type) : name;
        };
    }

    boolean selectable() { return input ? MICS.contains(type) : OUTPUTS.contains(type); }

    // NOTE: USB addresses change between plugs; model name finds the same receiver again
    String key() {
        String prefix = (input ? "in:" : "out:") + type;
        if (BUILT_IN.contains(type)) return prefix;
        return prefix + ":" + (BLUETOOTH.contains(type) ? address : name);
    }

    boolean sameEndpoint(Device other) {
        return other != null && input == other.input && type == other.type && address.equals(other.address);
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt("id", id);
        bundle.putBoolean("input", input);
        bundle.putInt("type", type);
        bundle.putString("address", address);
        bundle.putString("name", name);
        return bundle;
    }

    static Device fromBundle(Bundle bundle) {
        if (bundle == null) return null;
        return new Device(bundle.getInt("id"), bundle.getBoolean("input"), bundle.getInt("type"),
                bundle.getString("address"), bundle.getString("name"));
    }

    static ArrayList<Bundle> bundles(List<Device> devices) {
        ArrayList<Bundle> result = new ArrayList<>();
        for (Device device : devices) result.add(device.toBundle());
        return result;
    }

    static List<Device> fromBundles(Bundle bundle, String key) {
        List<Device> result = new ArrayList<>();
        ArrayList<Bundle> list = bundle.getParcelableArrayList(key, Bundle.class);
        if (list != null) for (Bundle item : list) result.add(fromBundle(item));
        return result;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("input", input).put("type", type).put("address", address).put("name", name);
    }

    static Device fromJson(JSONObject json) {
        return new Device(0, json.optBoolean("input"), json.optInt("type"), json.optString("address"),
                json.optString("name"));
    }
}
