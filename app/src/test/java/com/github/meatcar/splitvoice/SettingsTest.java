package com.github.meatcar.splitvoice;

import static com.github.meatcar.splitvoice.RoutingTest.*;
import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 37)
public class SettingsTest {
    private final Settings settings = new Settings(RuntimeEnvironment.getApplication());

    @Test public void freshInstallIsAutomaticForEveryApp() {
        assertEquals(Settings.AUTO, settings.choiceFor("com.discord"));
        assertEquals(Settings.AUTO, settings.choiceFor(null));
        assertTrue(settings.anythingOn());
    }

    @Test public void profilesKeepTheirDeviceOrderAcrossReloads() {
        Profile walking = settings.create("Walking");
        settings.save(walking.withSide(true, List.of(saved(JABRA_MIC), saved(DJI))));
        Profile loaded = new Settings(RuntimeEnvironment.getApplication()).profile(walking.id());
        assertEquals("Walking", loaded.name());
        assertEquals(List.of(saved(JABRA_MIC), saved(DJI)), loaded.mics());
        assertEquals(List.of(), loaded.headphones());
    }

    @Test public void deletingAProfileSendsItsAppsAndAllAppsBackToDefaults() {
        Profile walking = settings.create("Walking");
        Profile desk = settings.create("Desk");
        settings.setAllApps(walking.id());
        settings.setOverride("com.whatsapp", walking.id());
        settings.setOverride("com.discord", desk.id());
        settings.setOverride("com.google.meet", Settings.OFF);
        settings.delete(walking.id());
        assertEquals(Settings.AUTO, settings.allApps());
        assertEquals(Settings.AUTO, settings.choiceFor("com.whatsapp"));
        assertEquals(Map.of("com.discord", desk.id(), "com.google.meet", Settings.OFF), settings.overrides());
    }

    @Test public void onlyEverythingOffStopsTheService() {
        settings.setAllApps(Settings.OFF);
        settings.setOverride("com.google.meet", Settings.OFF);
        assertFalse(settings.anythingOn());
        settings.setOverride("com.discord", Settings.AUTO);
        assertTrue(settings.anythingOn());
        assertEquals(Settings.OFF, settings.choiceFor("com.whatsapp"));
    }

    @Test public void staleIdsFallBackInsteadOfBreakingRouting() {
        RuntimeEnvironment.getApplication().getSharedPreferences(Settings.NAME, 0).edit()
                .putString("all", "gone").putString("overrides", "{\"com.discord\":\"gone\"}")
                .putString("profiles", "not json").commit();
        assertEquals(Settings.AUTO, settings.allApps());
        assertEquals(Settings.AUTO, settings.choiceFor("com.discord"));
        assertTrue(settings.profiles().isEmpty());
    }
}
