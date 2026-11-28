package com.github.meatcar.splitvoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AdvancedActivity extends Activity {
    private RouteController routes;
    private Settings settings;
    private LinearLayout allApps;
    private LinearLayout profiles;
    private LinearLayout apps;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        routes = ((SplitVoiceApplication) getApplication()).routes();
        settings = routes.settings();
        Ui.screen(this, getString(R.string.advanced));
        allApps = Ui.section(this, R.string.all_apps, null);
        profiles = Ui.section(this, R.string.profiles, getString(R.string.profiles_hint));
        apps = Ui.section(this, R.string.app_overrides, getString(R.string.app_overrides_hint));
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        allApps.removeAllViews();
        String all = settings.allApps();
        Ui.row(allApps, routes.label(all), describe(all), view -> choose(getString(R.string.all_apps), all, false,
                choice -> settings.setAllApps(choice)));

        profiles.removeAllViews();
        for (Profile profile : settings.profiles()) {
            Ui.row(profiles, profile.name(), summary(profile), view -> open(profile));
        }
        Ui.action(profiles, R.string.new_profile, view -> createProfile());

        apps.removeAllViews();
        Map<String, String> overrides = settings.overrides();
        Set<String> listed = new LinkedHashSet<>(overrides.keySet());
        listed.addAll(settings.seen());
        for (String app : listed) {
            String choice = overrides.get(app);
            Ui.row(apps, routes.appLabel(app), choice == null ? getString(R.string.uses_all_apps) : routes.label(choice),
                    view -> chooseFor(app));
        }
        Ui.action(apps, R.string.add_app, view -> addApp(listed));
    }

    private String describe(String choice) {
        if (Settings.AUTO.equals(choice)) return getString(R.string.auto_summary);
        if (Settings.OFF.equals(choice)) return getString(R.string.off_summary);
        return summary(settings.profile(choice));
    }

    private String summary(Profile profile) {
        List<String> parts = new ArrayList<>();
        if (!profile.mics().isEmpty()) parts.add(getString(R.string.profile_in, names(profile.mics())));
        if (!profile.headphones().isEmpty()) parts.add(getString(R.string.profile_out, names(profile.headphones())));
        return parts.isEmpty() ? getString(R.string.profile_empty) : String.join(" ", parts);
    }

    private String names(List<Device> devices) {
        List<String> names = new ArrayList<>();
        for (Device device : devices) names.add(device.label(this));
        return String.join(getString(R.string.then), names);
    }

    private interface Choice { void chosen(String choice); }

    private void choose(String title, String current, boolean allowDefault, Choice onChosen) {
        List<String> choices = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (allowDefault) {
            choices.add(null);
            labels.add(getString(R.string.uses_all_apps));
        }
        for (String choice : routes.choices()) {
            choices.add(choice);
            labels.add(routes.label(choice));
        }
        new AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(labels.toArray(new String[0]), choices.indexOf(current), (dialog, index) -> {
                    onChosen.chosen(choices.get(index));
                    dialog.dismiss();
                    AutoRouteService.sync(this, routes);
                    render();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void chooseFor(String app) {
        choose(routes.appLabel(app), settings.overrides().get(app), true, choice -> settings.setOverride(app, choice));
    }

    private void addApp(Set<String> listed) {
        List<ResolveInfo> launchers = getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL);
        Set<String> packages = new LinkedHashSet<>();
        for (ResolveInfo info : launchers) {
            String app = info.activityInfo.packageName;
            if (!app.equals(getPackageName()) && !listed.contains(app)) packages.add(app);
        }
        List<String> sorted = new ArrayList<>(packages);
        sorted.sort(Comparator.comparing(app -> routes.appLabel(app).toLowerCase(Locale.ROOT)));
        String[] labels = sorted.stream().map(routes::appLabel).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(R.string.add_app)
                .setItems(labels, (dialog, index) -> chooseFor(sorted.get(index)))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void createProfile() {
        Ui.askName(this, R.string.new_profile, "", R.string.create, name -> open(settings.create(name.isEmpty()
                ? getString(R.string.profile_default_name, settings.profiles().size() + 1) : name)));
    }

    private void open(Profile profile) {
        startActivity(new Intent(this, ProfileActivity.class).putExtra(ProfileActivity.ID, profile.id()));
    }
}
