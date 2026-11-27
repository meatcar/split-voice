package com.github.meatcar.splitvoice;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;

import java.util.List;

/** Runs as the shell user through app_process, like Shizuku runs RouteService. Prints PASS or a stack trace. */
public final class AudioServiceProbe {
    static final String PASS = "AudioServiceProbe PASS";

    public static void main(String[] args) {
        try {
            run();
            System.out.println(PASS);
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
        }
        // NOTE: app_process waits for binder threads, which never stop; exit explicitly
        System.exit(0);
    }

    private static void run() throws Exception {
        AudioServiceBackend audio = new AudioServiceBackend();
        RouteTrial.require(audio.input().isEmpty() && audio.output().isEmpty(), "emulator already has routing preferences");
        Bundle state = new RouteService().inspect(null, null);
        RouteTrial.require(state.getBoolean("ok"), "inspect failed: " + state.getString("message"));
        RouteTrial.require(state.getIntArray("inputTypes").length > 0, "inspect listed no input devices");
        RouteTrial.require(state.getInt("mode") == AudioManager.MODE_NORMAL, "unexpected audio mode " + state.getInt("mode"));

        RouteTrial.Device receiver = attached(RouteTrial.ROLE_INPUT, AudioDeviceInfo.TYPE_BUILTIN_MIC);
        RouteTrial.Device headphones = attached(RouteTrial.ROLE_OUTPUT, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        try {
            RouteTrial.apply(withMode(audio, AudioManager.MODE_IN_COMMUNICATION), receiver, headphones);
            RouteTrial.require(RouteTrial.matches(audio, receiver, headphones), "readback after apply differs");
        } finally {
            RouteTrial.restore(withMode(audio, AudioManager.MODE_NORMAL), receiver, headphones);
        }
        RouteTrial.require(audio.input().isEmpty() && audio.output().isEmpty(), "preferences left after restore");
    }

    // NOTE: audio policy rejects devices that are not attached; emulators have no USB or LE Audio devices
    private static RouteTrial.Device attached(int role, int type) throws Exception {
        for (AudioDeviceInfo device : RouteService.devices()) {
            if (device.getType() == type && (role == RouteTrial.ROLE_INPUT ? device.isSource() : device.isSink())) {
                return new RouteTrial.Device(role, type, device.getAddress());
            }
        }
        throw new IllegalStateException("emulator has no attached device of type " + type);
    }

    // NOTE: an emulator cannot start a real voice chat; the probe fakes only the mode check
    private static RouteTrial.Backend withMode(RouteTrial.Backend backend, int mode) {
        return new RouteTrial.Backend() {
            @Override public int mode() { return mode; }
            @Override public boolean sco() throws Exception { return backend.sco(); }
            @Override public List<RouteTrial.Device> input() throws Exception { return backend.input(); }
            @Override public List<RouteTrial.Device> output() throws Exception { return backend.output(); }
            @Override public void input(List<RouteTrial.Device> devices) throws Exception { backend.input(devices); }
            @Override public void output(List<RouteTrial.Device> devices) throws Exception { backend.output(devices); }
        };
    }
}
