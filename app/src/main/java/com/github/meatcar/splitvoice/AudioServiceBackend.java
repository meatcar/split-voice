package com.github.meatcar.splitvoice;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.IBinder;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

// NOTE: hidden IAudioService API; AudioServiceContractTest checks it on each emulator in the Instrumented workflow
final class AudioServiceBackend implements RouteTrial.Backend {
    private static final int CAPTURE_PRESET_VOICE_COMMUNICATION = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
    private static final int ROLE_INPUT = 1;
    private static final int ROLE_OUTPUT = 2;
    private static final Class<?>[] TARGET = {int.class};
    private static final Class<?>[] TARGET_DEVICES = {int.class, List.class};

    private final Class<?> api;
    private final Object audio;
    private final int voiceStrategy;

    AudioServiceBackend() throws Exception {
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "audio");
        api = Class.forName("android.media.IAudioService");
        audio = Class.forName("android.media.IAudioService$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
        voiceStrategy = voiceStrategy();
    }

    // NOTE: strategy IDs come from the device audio policy; emulators use 0 or 15, not 1
    private int voiceStrategy() throws Exception {
        AudioAttributes voice = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build();
        for (Object strategy : strategies()) {
            Class<?> type = strategy.getClass();
            if ((Boolean) type.getMethod("supportsAudioAttributes", AudioAttributes.class).invoke(strategy, voice)) {
                return (Integer) type.getMethod("getId").invoke(strategy);
            }
        }
        throw new IllegalStateException("No audio strategy serves voice chat on this phone.");
    }

    // NOTE: Android 17 adds filterInternal; true matches the public SDK
    private List<?> strategies() throws Exception {
        try {
            return (List<?>) call("getAudioProductStrategies", new Class<?>[]{boolean.class}, true);
        } catch (NoSuchMethodException e) {
            return (List<?>) call("getAudioProductStrategies", new Class<?>[0]);
        }
    }

    @Override public int mode() throws Exception { return (Integer) call("getMode", new Class<?>[0]); }
    @Override public boolean sco() throws Exception { return (Boolean) call("isBluetoothScoOn", new Class<?>[0]); }

    @Override public List<Device> devices() throws Exception {
        List<Device> result = new ArrayList<>();
        for (AudioDeviceInfo device : (AudioDeviceInfo[]) AudioManager.class.getMethod("getDevicesStatic", int.class)
                .invoke(null, AudioManager.GET_DEVICES_ALL)) result.add(Device.of(device));
        return result;
    }

    // NOTE: hidden getter; callers without MODIFY_AUDIO_ROUTING get empty names
    @Override public String voiceApp() throws Exception {
        String any = null;
        for (Object config : (List<?>) call("getActiveRecordingConfigurations", new Class<?>[0])) {
            AudioRecordingConfiguration recording = (AudioRecordingConfiguration) config;
            String app = (String) AudioRecordingConfiguration.class.getMethod("getClientPackageName").invoke(recording);
            if (app == null || app.isEmpty()) continue;
            if (recording.getClientAudioSource() == MediaRecorder.AudioSource.VOICE_COMMUNICATION) return app;
            if (any == null) any = app;
        }
        return any;
    }

    private Object call(String method, Class<?>[] types, Object... args) throws Exception {
        try {
            return api.getMethod(method, types).invoke(audio, args);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("AudioService rejected " + method, e.getCause());
        }
    }

    private int target(boolean input) { return input ? CAPTURE_PRESET_VOICE_COMMUNICATION : voiceStrategy; }

    @Override public List<Device> preferences(boolean input) throws Exception {
        List<?> raw = (List<?>) call(input ? "getPreferredDevicesForCapturePreset" : "getPreferredDevicesForStrategy",
                TARGET, target(input));
        List<Device> result = new ArrayList<>();
        for (Object device : raw) {
            Class<?> cls = device.getClass();
            result.add(new Device(0, (Integer) cls.getMethod("getRole").invoke(device) == ROLE_INPUT,
                    (Integer) cls.getMethod("getType").invoke(device),
                    (String) cls.getMethod("getAddress").invoke(device), ""));
        }
        return result;
    }

    @Override public void prefer(boolean input, Device device) throws Exception {
        String method;
        int result;
        if (device == null) {
            if (preferences(input).isEmpty()) return;
            method = input ? "clearPreferredDevicesForCapturePreset" : "removePreferredDevicesForStrategy";
            result = (Integer) call(method, TARGET, target(input));
        } else {
            Object attributes = Class.forName("android.media.AudioDeviceAttributes")
                    .getConstructor(int.class, int.class, String.class)
                    .newInstance(input ? ROLE_INPUT : ROLE_OUTPUT, device.type(), device.address());
            method = input ? "setPreferredDevicesForCapturePreset" : "setPreferredDevicesForStrategy";
            result = (Integer) call(method, TARGET_DEVICES, target(input), List.of(attributes));
        }
        RouteTrial.require(result == 0, method + " returned " + result);
    }
}
