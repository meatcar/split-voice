package com.github.meatcar.splitvoice;

import android.media.AudioManager;
import android.os.Bundle;

import java.util.List;

final class RouteTrial {
    interface Backend {
        int mode() throws Exception;
        boolean sco() throws Exception;
        List<Device> devices() throws Exception;
        String voiceApp() throws Exception;
        List<Device> preferences(boolean input) throws Exception;
        void prefer(boolean input, Device device) throws Exception;
    }

    static void inspect(Backend backend, Bundle result) throws Exception {
        result.putParcelableArrayList("devices", Device.bundles(backend.devices()));
        result.putParcelableArrayList("input", Device.bundles(backend.preferences(true)));
        result.putParcelableArrayList("output", Device.bundles(backend.preferences(false)));
        result.putInt("mode", backend.mode());
        result.putBoolean("sco", backend.sco());
        result.putString("app", backend.voiceApp());
    }

    static void route(Backend backend, Bundle request, Bundle result) throws Exception {
        for (String side : new String[]{"input", "output"}) {
            Side outcome = route(backend, side.equals("input"), Device.fromBundles(request, side + "Expected"),
                    Device.fromBundle(request.getBundle(side + "Wanted")));
            result.putBundle(side + "Owned", outcome.owned() == null ? null : outcome.owned().toBundle());
            result.putParcelableArrayList(side, Device.bundles(outcome.actual()));
            result.putBoolean(side + "Foreign", outcome.foreign());
            result.putBoolean(side + "Refused", outcome.refused());
        }
    }

    record Side(Device owned, List<Device> actual, boolean foreign, boolean refused) {}

    // NOTE: compare-and-set; a preference that is neither ours nor empty belongs to another app
    static Side route(Backend backend, boolean input, List<Device> expected, Device wanted) throws Exception {
        if (backend.mode() != AudioManager.MODE_IN_COMMUNICATION) wanted = null;
        List<Device> current = backend.preferences(input);
        boolean ours = expected.stream().anyMatch(device -> holds(current, device));
        if (holds(current, wanted)) return new Side(ours ? wanted : null, current, false, false);
        if (!current.isEmpty() && !ours) return new Side(null, current, true, false);
        try {
            backend.prefer(input, wanted);
        } catch (IllegalStateException refused) {
            // NOTE: audio policy rejects some devices outright; the readback below decides
        }
        List<Device> after = backend.preferences(input);
        Device owned = after.size() == 1 && (after.get(0).sameEndpoint(wanted)
                || expected.stream().anyMatch(after.get(0)::sameEndpoint)) ? after.get(0) : null;
        return new Side(owned, after, false, !holds(after, wanted));
    }

    static boolean holds(List<Device> preferences, Device device) {
        return device == null ? preferences.isEmpty() : preferences.size() == 1 && preferences.get(0).sameEndpoint(device);
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
