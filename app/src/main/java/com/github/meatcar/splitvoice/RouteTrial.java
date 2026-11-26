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
        require(backend.mode() == AudioManager.MODE_IN_COMMUNICATION, "Start Voice before applying.");
        require(!backend.sco(), "SCO is active. Connect the physical receiver instead of Bluetooth mic input.");
        require(backend.input().isEmpty() && backend.output().isEmpty(),
                "Existing preferences found. Only an empty baseline is supported.");
        backend.input(List.of(receiver));
        require(backend.input().equals(List.of(receiver)), "Input readback failed. Stop Voice and restore.");
        backend.output(List.of(earbuds));
        require(matches(backend, receiver, earbuds),
                "Combined readback failed. Stop Voice and restore.");
    }

    static boolean matches(Backend backend, Device receiver, Device earbuds) throws Exception {
        return backend.input().equals(List.of(receiver)) && backend.output().equals(List.of(earbuds));
    }

    static void restore(Backend backend, Device receiver, Device earbuds) throws Exception {
        require(backend.mode() == AudioManager.MODE_NORMAL, "Stop Voice before restoring.");
        require(compatible(backend.input(), receiver) && compatible(backend.output(), earbuds),
                "Preferences changed outside Split Voice. Refusing to overwrite them.");
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
        require(backend.input().isEmpty() && backend.output().isEmpty(), "Restoration readback failed.");
    }

    private static boolean compatible(List<Device> actual, Device expected) {
        return actual.isEmpty() || actual.equals(List.of(expected));
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
