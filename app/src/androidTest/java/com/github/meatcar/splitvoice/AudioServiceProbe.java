package com.github.meatcar.splitvoice;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;

import java.util.List;

/** Runs as the shell user through app_process, like Shizuku runs RouteService. Prints PASS or a stack trace. */
public final class AudioServiceProbe {
    static final String PASS = "AudioServiceProbe PASS";

    /** With an argument, checks only that the voice app lookup names that package. */
    public static void main(String[] args) {
        try {
            if (args.length > 0) voiceApp(args[0]);
            else route();
            System.out.println(PASS);
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
        }
        // NOTE: app_process waits for binder threads, which never stop; exit explicitly
        System.exit(0);
    }

    private static void voiceApp(String expected) throws Exception {
        String found = new AudioServiceBackend().voiceApp();
        RouteTrial.require(expected.equals(found), "voice app lookup returned " + found);
    }

    private static void route() throws Exception {
        AudioServiceBackend audio = new AudioServiceBackend();
        RouteTrial.require(audio.preferences(true).isEmpty() && audio.preferences(false).isEmpty(),
                "emulator already has routing preferences");
        Bundle state = new Bundle();
        RouteTrial.inspect(audio, state);
        List<Device> devices = Device.fromBundles(state, "devices");
        RouteTrial.require(state.getInt("mode") == AudioManager.MODE_NORMAL, "unexpected audio mode " + state.getInt("mode"));

        Device mic = attached(devices, true, AudioDeviceInfo.TYPE_BUILTIN_MIC);
        Device speaker = attached(devices, false, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        RouteTrial.Backend call = withMode(audio, AudioManager.MODE_IN_COMMUNICATION);
        try {
            for (boolean input : new boolean[]{true, false}) {
                Device wanted = input ? mic : speaker;
                RouteTrial.Side side = RouteTrial.route(call, input, List.of(), wanted);
                RouteTrial.require(!side.refused() && wanted.sameEndpoint(side.owned()), "route refused " + wanted);
                RouteTrial.require(RouteTrial.holds(audio.preferences(input), wanted), "readback differs for " + wanted);
            }
        } finally {
            RouteTrial.Backend ended = withMode(audio, AudioManager.MODE_NORMAL);
            RouteTrial.route(ended, true, List.of(mic), null);
            RouteTrial.route(ended, false, List.of(speaker), null);
        }
        RouteTrial.require(audio.preferences(true).isEmpty() && audio.preferences(false).isEmpty(),
                "preferences left after the call ended");
    }

    // NOTE: audio policy rejects devices that are not attached; emulators have no USB or LE Audio devices
    private static Device attached(List<Device> devices, boolean input, int type) {
        for (Device device : devices) if (device.input() == input && device.type() == type) return device;
        throw new IllegalStateException("emulator has no attached device of type " + type);
    }

    // NOTE: an emulator cannot start a real voice chat; the probe fakes only the mode check
    private static RouteTrial.Backend withMode(AudioServiceBackend backend, int mode) {
        return new RouteTrial.Backend() {
            @Override public int mode() { return mode; }
            @Override public boolean sco() throws Exception { return backend.sco(); }
            @Override public List<Device> devices() throws Exception { return backend.devices(); }
            @Override public String voiceApp() throws Exception { return backend.voiceApp(); }
            @Override public List<Device> preferences(boolean input) throws Exception { return backend.preferences(input); }
            @Override public void prefer(boolean input, Device device) throws Exception { backend.prefer(input, device); }
        };
    }
}
