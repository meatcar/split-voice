package com.github.meatcar.splitvoice;

import android.Manifest;
import android.app.Instrumentation;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Checks the hidden audio API on this Android version, from the shell user that Shizuku provides. */
@RunWith(AndroidJUnit4.class)
public class AudioServiceContractTest {
    @Test public void shellCanRouteAndClearThroughHiddenAudioApi() throws Exception {
        String output = probe("");
        assertTrue(output, output.contains(AudioServiceProbe.PASS));
    }

    @Test public void shellSeesWhichAppIsInAVoiceChat() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        String app = instrumentation.getTargetContext().getPackageName();
        instrumentation.getUiAutomation().grantRuntimePermission(app, Manifest.permission.RECORD_AUDIO);
        AudioRecord recording = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 16000);
        try {
            assertEquals("emulator cannot record", AudioRecord.STATE_INITIALIZED, recording.getState());
            recording.startRecording();
            String output = probe(app);
            assertTrue(output, output.contains(AudioServiceProbe.PASS));
        } finally {
            recording.release();
        }
    }

    private static String probe(String argument) throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        String classpath = instrumentation.getTargetContext().getPackageCodePath()
                + ":" + instrumentation.getContext().getPackageCodePath();
        return shell("CLASSPATH=" + classpath + " timeout 120 app_process /system/bin "
                + AudioServiceProbe.class.getName() + " " + argument + " 2>&1; echo exit=$?\n");
    }

    private static String shell(String script) throws Exception {
        ParcelFileDescriptor[] pipes = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommandRw("sh");
        try (OutputStream stdin = new ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])) {
            stdin.write(script.getBytes(StandardCharsets.UTF_8));
        }
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pipes[0])) {
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) != -1; ) stdout.write(buffer, 0, read);
        }
        return stdout.toString(StandardCharsets.UTF_8.name());
    }
}
