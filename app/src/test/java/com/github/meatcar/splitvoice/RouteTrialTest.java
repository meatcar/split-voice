package com.github.meatcar.splitvoice;

import static com.github.meatcar.splitvoice.RoutingTest.*;
import static org.junit.Assert.*;

import android.media.AudioManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RouteTrialTest {
    static final class MemoryBackend implements RouteTrial.Backend {
        int mode = AudioManager.MODE_IN_COMMUNICATION;
        final List<Device> devices = new ArrayList<>();
        String app;
        final List<Device> input = new ArrayList<>();
        final List<Device> output = new ArrayList<>();
        Set<String> refuses = Set.of();
        Set<String> rejects = Set.of();
        int writes;

        @Override public int mode() { return mode; }
        @Override public boolean sco() { return false; }
        @Override public List<Device> devices() { return devices; }
        @Override public String voiceApp() { return app; }
        @Override public List<Device> preferences(boolean input) {
            List<Device> result = new ArrayList<>();
            for (Device device : input ? this.input : output) {
                result.add(new Device(0, device.input(), device.type(), device.address(), ""));
            }
            return result;
        }
        @Override public void prefer(boolean input, Device device) {
            writes++;
            List<Device> side = input ? this.input : output;
            if (device != null && rejects.contains(device.key())) throw new IllegalStateException("rejected");
            side.clear();
            if (device != null && !refuses.contains(device.key())) side.add(device);
        }
    }

    @Test public void setsWhenEmptyAndOwnsWhatAndroidKept() throws Exception {
        MemoryBackend audio = new MemoryBackend();
        RouteTrial.Side side = RouteTrial.route(audio, true, List.of(), DJI);
        assertTrue(DJI.sameEndpoint(side.owned()));
        assertFalse(side.foreign());
        assertFalse(side.refused());
        assertTrue(RouteTrial.holds(audio.input, DJI));
    }

    @Test public void replacesOnlyItsOwnPreference() throws Exception {
        MemoryBackend audio = new MemoryBackend();
        audio.input.add(WIRED);
        RouteTrial.Side ours = RouteTrial.route(audio, true, List.of(WIRED), DJI);
        assertTrue(RouteTrial.holds(audio.input, DJI));
        assertTrue(DJI.sameEndpoint(ours.owned()));

        audio.output.add(SPEAKER);
        RouteTrial.Side theirs = RouteTrial.route(audio, false, List.of(JABRA), JABRA);
        assertTrue(theirs.foreign());
        assertNull(theirs.owned());
        assertTrue(RouteTrial.holds(audio.output, SPEAKER));
        assertTrue(RouteTrial.holds(theirs.actual(), SPEAKER));
    }

    @Test public void aPreferenceThatAlreadyMatchesIsNotClaimed() throws Exception {
        MemoryBackend audio = new MemoryBackend();
        audio.output.add(JABRA);
        RouteTrial.Side side = RouteTrial.route(audio, false, List.of(), JABRA);
        assertNull(side.owned());
        assertFalse(side.foreign());
        assertEquals(0, audio.writes);
    }

    @Test public void refusalIsReportedWhetherAndroidThrowsOrDropsTheDevice() throws Exception {
        MemoryBackend audio = new MemoryBackend();
        audio.refuses = Set.of(JABRA.key());
        RouteTrial.Side dropped = RouteTrial.route(audio, false, List.of(), JABRA);
        assertTrue(dropped.refused());
        assertNull(dropped.owned());

        audio.rejects = Set.of(DJI.key());
        RouteTrial.Side thrown = RouteTrial.route(audio, true, List.of(), DJI);
        assertTrue(thrown.refused());
        assertNull(thrown.owned());
        assertTrue(audio.input.isEmpty());
    }

    @Test public void outsideAVoiceChatItOnlyClearsItsOwnPreference() throws Exception {
        MemoryBackend audio = new MemoryBackend();
        audio.input.add(DJI);
        audio.output.add(SPEAKER);
        audio.mode = AudioManager.MODE_NORMAL;
        RouteTrial.Side input = RouteTrial.route(audio, true, List.of(DJI), DJI);
        RouteTrial.Side output = RouteTrial.route(audio, false, List.of(JABRA), null);
        assertTrue(audio.input.isEmpty());
        assertNull(input.owned());
        assertFalse(input.refused());
        assertTrue(output.foreign());
        assertTrue(RouteTrial.holds(audio.output, SPEAKER));
    }

    @Test public void aRecordOfTwoDevicesCoversACrashBetweenRecordAndWrite() throws Exception {
        MemoryBackend audio = new MemoryBackend();
        audio.input.add(WIRED);
        RouteTrial.Side side = RouteTrial.route(audio, true, List.of(DJI, WIRED), PHONE_MIC);
        assertFalse(side.foreign());
        assertTrue(RouteTrial.holds(audio.input, PHONE_MIC));
    }
}
