package com.github.meatcar.splitvoice;

import android.app.Instrumentation;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

/** Checks the hidden audio API on this Android version, from the shell user that Shizuku provides. */
@RunWith(AndroidJUnit4.class)
public class AudioServiceContractTest {
    @Test public void shellCanInspectSplitAndRestoreThroughHiddenAudioApi() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        String classpath = instrumentation.getTargetContext().getPackageCodePath()
                + ":" + instrumentation.getContext().getPackageCodePath();
        String output = shell("CLASSPATH=" + classpath + " timeout 120 app_process /system/bin "
                + AudioServiceProbe.class.getName() + " 2>&1; echo exit=$?\n");
        assertTrue(output, output.contains(AudioServiceProbe.PASS));
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
