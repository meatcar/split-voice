package com.github.meatcar.splitvoice;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;

import java.util.List;

/** Runs an action on the main thread when the audio mode, the connected devices or the active recordings change. */
final class AudioWatch {
    private final Context context;
    private final AudioManager audio;
    private final Runnable action;
    private final AudioManager.OnModeChangedListener modeListener;
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices) { action.run(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) { action.run(); }
    };
    private final AudioManager.AudioRecordingCallback recordingCallback = new AudioManager.AudioRecordingCallback() {
        @Override public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) { action.run(); }
    };
    private boolean watching;

    AudioWatch(Context context, Runnable action) {
        this.context = context;
        this.audio = context.getSystemService(AudioManager.class);
        this.action = action;
        modeListener = mode -> action.run();
    }

    void start() {
        if (watching) return;
        watching = true;
        audio.addOnModeChangedListener(context.getMainExecutor(), modeListener);
        audio.registerAudioDeviceCallback(deviceCallback, null);
        audio.registerAudioRecordingCallback(recordingCallback, null);
    }

    void stop() {
        if (!watching) return;
        watching = false;
        audio.unregisterAudioRecordingCallback(recordingCallback);
        audio.unregisterAudioDeviceCallback(deviceCallback);
        audio.removeOnModeChangedListener(modeListener);
    }
}
