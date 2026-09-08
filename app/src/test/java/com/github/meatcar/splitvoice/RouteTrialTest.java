package com.github.meatcar.splitvoice;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class RouteTrialTest {
    private final RouteTrial.Device receiver = new RouteTrial.Device(1, 11, "receiver");
    private final RouteTrial.Device earbuds = new RouteTrial.Device(2, 26, "earbuds");

    private static final class MemoryBackend implements RouteTrial.Backend {
        int mode = 3;
        boolean sco;
        boolean failOutput;
        boolean ignoreInput;
        List<RouteTrial.Device> input = List.of();
        List<RouteTrial.Device> output = List.of();
        final List<String> writes = new ArrayList<>();
        public int mode() { return mode; }
        public boolean sco() { return sco; }
        public List<RouteTrial.Device> input() { return input; }
        public List<RouteTrial.Device> output() { return output; }
        public void input(List<RouteTrial.Device> devices) {
            writes.add("input");
            if (!ignoreInput) input = devices;
        }
        public void output(List<RouteTrial.Device> devices) {
            writes.add("output");
            if (failOutput) throw new IllegalStateException("injected transport failure");
            output = devices;
        }
    }

    @Test public void applyAndRestoreHaveVerifiedOrder() throws Exception {
        MemoryBackend backend = new MemoryBackend();
        RouteTrial.apply(backend, receiver, earbuds);
        assertEquals(List.of(receiver), backend.input);
        assertEquals(List.of(earbuds), backend.output);
        backend.mode = 0;
        RouteTrial.restore(backend, receiver, earbuds);
        assertEquals(List.of("input", "output", "output", "input"), backend.writes);
        assertTrue(backend.input.isEmpty());
        assertTrue(backend.output.isEmpty());
    }

    @Test public void nonemptyOriginalRolesAreNeverOverwritten() {
        for (boolean input : List.of(true, false)) {
            MemoryBackend backend = new MemoryBackend();
            if (input) backend.input = List.of(receiver); else backend.output = List.of(earbuds);
            assertThrows(IllegalStateException.class, () -> RouteTrial.apply(backend, receiver, earbuds));
            assertTrue(backend.writes.isEmpty());
        }
    }

    @Test public void requiresVoiceAndNoScoBeforeApply() {
        MemoryBackend backend = new MemoryBackend();
        backend.mode = 0;
        assertThrows(IllegalStateException.class, () -> RouteTrial.apply(backend, receiver, earbuds));
        backend.mode = 3;
        backend.sco = true;
        assertThrows(IllegalStateException.class, () -> RouteTrial.apply(backend, receiver, earbuds));
        assertTrue(backend.writes.isEmpty());
    }

    @Test public void restoreWaitsForVoiceAndRejectsForeignRoles() {
        MemoryBackend backend = new MemoryBackend();
        assertThrows(IllegalStateException.class, () -> RouteTrial.restore(backend, receiver, earbuds));
        backend.mode = 0;
        backend.input = List.of(new RouteTrial.Device(1, 11, "other"));
        assertThrows(IllegalStateException.class, () -> RouteTrial.restore(backend, receiver, earbuds));
        assertTrue(backend.writes.isEmpty());
    }

    @Test public void inputReadbackFailureStopsBeforeOutput() {
        MemoryBackend backend = new MemoryBackend();
        backend.ignoreInput = true;
        assertThrows(IllegalStateException.class, () -> RouteTrial.apply(backend, receiver, earbuds));
        assertEquals(List.of("input"), backend.writes);
    }

    @Test public void partialApplyCanBeRestoredAfterStoppingVoice() throws Exception {
        MemoryBackend backend = new MemoryBackend();
        backend.failOutput = true;
        assertThrows(IllegalStateException.class, () -> RouteTrial.apply(backend, receiver, earbuds));
        assertEquals(List.of(receiver), backend.input);
        backend.failOutput = false;
        backend.mode = 0;
        RouteTrial.restore(backend, receiver, earbuds);
        assertTrue(backend.input.isEmpty());
        assertTrue(backend.output.isEmpty());
    }

    @Test public void outputRestoreFailureStillAttemptsInputAndIsReported() throws Exception {
        MemoryBackend backend = new MemoryBackend();
        RouteTrial.apply(backend, receiver, earbuds);
        backend.mode = 0;
        backend.failOutput = true;
        assertThrows(IllegalStateException.class, () -> RouteTrial.restore(backend, receiver, earbuds));
        assertTrue(backend.input.isEmpty());
        assertEquals(List.of(earbuds), backend.output);
    }

    @Test public void alreadyEmptyAfterRestartCanBeVerified() throws Exception {
        MemoryBackend backend = new MemoryBackend();
        backend.mode = 0;
        RouteTrial.restore(backend, receiver, earbuds);
        assertTrue(backend.input.isEmpty());
        assertTrue(backend.output.isEmpty());
    }

    @Test public void checkedStateRequiresExactPairNotJustCounts() throws Exception {
        MemoryBackend backend = new MemoryBackend();
        RouteTrial.apply(backend, receiver, earbuds);
        assertTrue(RouteTrial.matches(backend, receiver, earbuds));
        backend.output = List.of(new RouteTrial.Device(2, 26, "foreign"));
        assertFalse(RouteTrial.matches(backend, receiver, earbuds));
        backend.output = List.of(earbuds);
        backend.input = List.of(new RouteTrial.Device(1, 11, "other"));
        assertFalse(RouteTrial.matches(backend, receiver, earbuds));
    }
}
