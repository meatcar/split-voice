package com.github.meatcar.splitvoice;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import java.util.List;

final class RouteTrial {
    private static final int ROLE_INPUT = 1;
    private static final int ROLE_OUTPUT = 2;

    record Device(int role, int type, String address) {
        static Device receiver(String address) { return new Device(ROLE_INPUT, AudioDeviceInfo.TYPE_USB_DEVICE, address); }
        static Device headphones(String address) { return new Device(ROLE_OUTPUT, AudioDeviceInfo.TYPE_BLE_HEADSET, address); }
    }

    interface Backend {
        int mode() throws Exception;
        boolean sco() throws Exception;
        List<Device> input() throws Exception;
        List<Device> output() throws Exception;
        void input(List<Device> devices) throws Exception;
        void output(List<Device> devices) throws Exception;
    }

    static void apply(Backend backend, Device receiver, Device earbuds) throws Exception {
        require(backend.mode() == AudioManager.MODE_IN_COMMUNICATION, "Start a voice chat first. Nothing changed.");
        require(!backend.sco(), "Your headphones' own mic is in use. Nothing changed.");
        require(backend.input().isEmpty() && backend.output().isEmpty(),
                "Another app already chose audio devices. Nothing changed.");
        backend.input(List.of(receiver));
        require(backend.input().equals(List.of(receiver)), "Android did not keep the mic choice. End the voice chat, then tap Restore.");
        backend.output(List.of(earbuds));
        require(matches(backend, receiver, earbuds),
                "Android did not keep the headphone choice. End the voice chat, then tap Restore.");
    }

    static boolean matches(Backend backend, Device receiver, Device earbuds) throws Exception {
        return backend.input().equals(List.of(receiver)) && backend.output().equals(List.of(earbuds));
    }

    static void restore(Backend backend, Device receiver, Device earbuds) throws Exception {
        require(backend.mode() == AudioManager.MODE_NORMAL, "End the voice chat first. Nothing changed.");
        require(compatible(backend.input(), receiver) && compatible(backend.output(), earbuds),
                "Another app changed audio devices after the split. Split Voice will not override it.");
        Exception failure = null;
        try {
            backend.output(List.of());
        } catch (Exception e) {
            failure = e;
        }
        try {
            backend.input(List.of());
        } catch (Exception e) {
            if (failure == null) failure = e;
            else failure.addSuppressed(e);
        }
        if (failure != null) throw failure;
        require(backend.input().isEmpty() && backend.output().isEmpty(), "Android did not confirm the restore. Tap Restore again.");
    }

    private static boolean compatible(List<Device> actual, Device expected) {
        return actual.isEmpty() || actual.equals(List.of(expected));
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
