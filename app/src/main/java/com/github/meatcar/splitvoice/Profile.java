package com.github.meatcar.splitvoice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

record Profile(String id, String name, List<Device> mics, List<Device> headphones) {
    Profile {
        mics = List.copyOf(mics);
        headphones = List.copyOf(headphones);
    }

    List<Device> side(boolean input) { return input ? mics : headphones; }

    Profile withName(String value) { return new Profile(id, value, mics, headphones); }

    Profile withSide(boolean input, List<Device> devices) {
        return input ? new Profile(id, name, devices, headphones) : new Profile(id, name, mics, devices);
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("name", name).put("mics", json(mics)).put("headphones", json(headphones));
    }

    static Profile fromJson(JSONObject json) {
        return new Profile(json.optString("id"), json.optString("name"), devices(json.optJSONArray("mics")),
                devices(json.optJSONArray("headphones")));
    }

    private static JSONArray json(List<Device> devices) throws JSONException {
        JSONArray array = new JSONArray();
        for (Device device : devices) array.put(device.toJson());
        return array;
    }

    private static List<Device> devices(JSONArray array) {
        List<Device> result = new ArrayList<>();
        if (array != null) for (int i = 0; i < array.length(); i++) result.add(Device.fromJson(array.optJSONObject(i)));
        return result;
    }
}
