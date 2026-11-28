package com.github.meatcar.splitvoice;

import java.util.List;
import java.util.Set;

final class Routing {
    record Plan(Device mic, Device headphones) {
        static final Plan NONE = new Plan(null, null);
        Device side(boolean input) { return input ? mic : headphones; }
    }

    private Routing() {}

    // NOTE: Android uses every preferred device at once, or none; so pick exactly one per side
    static Plan plan(String choice, Profile profile, List<Device> connected, Set<String> refused) {
        if (Settings.AUTO.equals(choice)) return automatic(connected, refused);
        if (profile == null) return Plan.NONE;
        return new Plan(first(profile.mics(), connected, refused), first(profile.headphones(), connected, refused));
    }

    private static Plan automatic(List<Device> connected, Set<String> refused) {
        Device mic = newest(connected, refused, true, Device.EXTERNAL_MICS);
        return mic == null ? Plan.NONE : new Plan(mic, newest(connected, refused, false, Device.HEADPHONES));
    }

    private static Device newest(List<Device> connected, Set<String> refused, boolean input, Set<Integer> types) {
        Device newest = null;
        for (Device device : connected) {
            if (device.input() == input && types.contains(device.type()) && !refused.contains(device.key())
                    && (newest == null || device.id() > newest.id())) newest = device;
        }
        return newest;
    }

    private static Device first(List<Device> wanted, List<Device> connected, Set<String> refused) {
        for (Device candidate : wanted) {
            if (refused.contains(candidate.key())) continue;
            for (Device device : connected) if (device.key().equals(candidate.key())) return device;
        }
        return null;
    }
}
