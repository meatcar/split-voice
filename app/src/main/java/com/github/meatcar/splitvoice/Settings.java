package com.github.meatcar.splitvoice;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class Settings {
    static final String AUTO = "auto";
    static final String OFF = "off";
    static final String NAME = "settings";

    private final SharedPreferences prefs;

    Settings(Context context) { prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE); }

    List<Profile> profiles() {
        List<Profile> result = new ArrayList<>();
        JSONArray array = array("profiles");
        for (int i = 0; i < array.length(); i++) result.add(Profile.fromJson(array.optJSONObject(i)));
        return result;
    }

    Profile profile(String id) {
        for (Profile profile : profiles()) if (profile.id().equals(id)) return profile;
        return null;
    }

    boolean valid(String choice) { return AUTO.equals(choice) || OFF.equals(choice) || profile(choice) != null; }

    String allApps() {
        String choice = prefs.getString("all", AUTO);
        return valid(choice) ? choice : AUTO;
    }

    void setAllApps(String choice) { prefs.edit().putString("all", choice).apply(); }

    Map<String, String> overrides() {
        Map<String, String> result = new LinkedHashMap<>();
        JSONObject json = object("overrides");
        for (Iterator<String> apps = json.keys(); apps.hasNext(); ) {
            String app = apps.next();
            String choice = json.optString(app);
            if (valid(choice)) result.put(app, choice);
        }
        return result;
    }

    void setOverride(String app, String choice) {
        JSONObject json = object("overrides");
        try {
            if (choice == null) json.remove(app);
            else json.put(app, choice);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        prefs.edit().putString("overrides", json.toString()).apply();
    }

    String choiceFor(String app) {
        String override = app == null ? null : overrides().get(app);
        return override != null ? override : allApps();
    }

    boolean anythingOn() {
        return !OFF.equals(allApps()) || overrides().values().stream().anyMatch(choice -> !OFF.equals(choice));
    }

    List<String> seen() {
        List<String> result = new ArrayList<>();
        JSONArray array = array("seen");
        for (int i = 0; i < array.length(); i++) result.add(array.optString(i));
        return result;
    }

    void markSeen(String app) {
        if (app == null || seen().contains(app)) return;
        prefs.edit().putString("seen", array("seen").put(app).toString()).apply();
    }

    Profile create(String name) {
        Profile profile = new Profile(UUID.randomUUID().toString(), name, List.of(), List.of());
        save(profile);
        return profile;
    }

    void save(Profile profile) {
        List<Profile> profiles = profiles();
        boolean replaced = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id().equals(profile.id())) {
                profiles.set(i, profile);
                replaced = true;
            }
        }
        if (!replaced) profiles.add(profile);
        write(profiles);
    }

    void delete(String id) {
        List<Profile> profiles = profiles();
        profiles.removeIf(profile -> profile.id().equals(id));
        write(profiles);
        JSONObject overrides = object("overrides");
        for (Iterator<String> apps = overrides.keys(); apps.hasNext(); ) {
            if (id.equals(overrides.optString(apps.next()))) apps.remove();
        }
        SharedPreferences.Editor editor = prefs.edit().putString("overrides", overrides.toString());
        if (id.equals(prefs.getString("all", AUTO))) editor.remove("all");
        editor.apply();
    }

    private void write(List<Profile> profiles) {
        JSONArray array = new JSONArray();
        try {
            for (Profile profile : profiles) array.put(profile.toJson());
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        prefs.edit().putString("profiles", array.toString()).apply();
    }

    private JSONArray array(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    private JSONObject object(String key) {
        try { return new JSONObject(prefs.getString(key, "{}")); }
        catch (JSONException e) { return new JSONObject(); }
    }
}
