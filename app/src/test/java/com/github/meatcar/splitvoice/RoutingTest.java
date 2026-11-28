package com.github.meatcar.splitvoice;

import static android.media.AudioDeviceInfo.*;
import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public class RoutingTest {
    static final Device PHONE_MIC = new Device(1, true, TYPE_BUILTIN_MIC, "bottom", "");
    static final Device SPEAKER = new Device(2, false, TYPE_BUILTIN_SPEAKER, "", "");
    static final Device JABRA_MIC = new Device(10, true, TYPE_BLE_HEADSET, "11:22", "Jabra Elite 10");
    static final Device JABRA = new Device(11, false, TYPE_BLE_HEADSET, "11:22", "Jabra Elite 10");
    static final Device DJI = new Device(20, true, TYPE_USB_DEVICE, "card=1;device=0", "DJI Mic");
    static final Device DJI_OUT = new Device(21, false, TYPE_USB_DEVICE, "card=1;device=0", "DJI Mic");
    static final Device WIRED = new Device(30, true, TYPE_WIRED_HEADSET, "", "Headset");
    static final Device WIRED_OUT = new Device(31, false, TYPE_WIRED_HEADSET, "", "Headset");

    private static Routing.Plan auto(Set<String> refused, Device... connected) {
        return Routing.plan(Settings.AUTO, null, List.of(connected), refused);
    }

    @Test public void automaticPairsTheNewestExternalMicWithTheNewestHeadphones() {
        assertEquals(new Routing.Plan(DJI, JABRA), auto(Set.of(), PHONE_MIC, SPEAKER, JABRA_MIC, JABRA, DJI, DJI_OUT));
        assertEquals(new Routing.Plan(WIRED, WIRED_OUT), auto(Set.of(), DJI, JABRA, WIRED, WIRED_OUT));
    }

    @Test public void automaticNeverTreatsBluetoothOrPhoneMicsAsExternal() {
        assertEquals(Routing.Plan.NONE, auto(Set.of(), PHONE_MIC, SPEAKER, JABRA_MIC, JABRA));
    }

    @Test public void automaticRoutesTheMicAloneWhenNoHeadphonesAreConnected() {
        assertEquals(new Routing.Plan(DJI, null), auto(Set.of(), PHONE_MIC, SPEAKER, DJI, DJI_OUT));
    }

    @Test public void automaticSkipsRefusedDevices() {
        assertEquals(new Routing.Plan(WIRED, JABRA), auto(Set.of(WIRED_OUT.key()), DJI, JABRA, WIRED, WIRED_OUT));
        assertEquals(new Routing.Plan(DJI, null), auto(Set.of(JABRA.key()), DJI, JABRA));
        assertEquals(Routing.Plan.NONE, auto(Set.of(DJI.key()), DJI, JABRA));
    }

    @Test public void profileUsesTheFirstConnectedDeviceOnEachSideIndependently() {
        Profile walking = new Profile("w", "Walking", List.of(saved(DJI), saved(JABRA_MIC), saved(PHONE_MIC)),
                List.of(saved(JABRA)));
        assertEquals(new Routing.Plan(DJI, JABRA), Routing.plan("w", walking, List.of(PHONE_MIC, JABRA_MIC, JABRA, DJI), Set.of()));
        assertEquals(new Routing.Plan(JABRA_MIC, JABRA), Routing.plan("w", walking, List.of(PHONE_MIC, JABRA_MIC, JABRA), Set.of()));
        assertEquals(new Routing.Plan(PHONE_MIC, null), Routing.plan("w", walking, List.of(PHONE_MIC, SPEAKER), Set.of()));
        assertEquals(new Routing.Plan(PHONE_MIC, JABRA),
                Routing.plan("w", walking, List.of(PHONE_MIC, JABRA_MIC, JABRA), Set.of(JABRA_MIC.key())));
    }

    @Test public void profileMatchesUsbByModelAndBluetoothByAddress() {
        Device replugged = new Device(40, true, TYPE_USB_DEVICE, "card=2;device=0", "DJI Mic");
        Device otherJabra = new Device(41, false, TYPE_BLE_HEADSET, "99:88", "Jabra Elite 10");
        Profile profile = new Profile("p", "Desk", List.of(saved(DJI)), List.of(saved(JABRA)));
        assertEquals(new Routing.Plan(replugged, null), Routing.plan("p", profile, List.of(replugged, otherJabra), Set.of()));
    }

    @Test public void offAndMissingProfilesLeaveBothSidesToAndroid() {
        assertEquals(Routing.Plan.NONE, Routing.plan(Settings.OFF, null, List.of(DJI, JABRA), Set.of()));
        assertEquals(Routing.Plan.NONE, Routing.plan("deleted", null, List.of(DJI, JABRA), Set.of()));
        Profile empty = new Profile("e", "Empty", List.of(), List.of());
        assertEquals(Routing.Plan.NONE, Routing.plan("e", empty, List.of(DJI, JABRA), Set.of()));
    }

    static Device saved(Device device) { return new Device(0, device.input(), device.type(), device.address(), device.name()); }
}
