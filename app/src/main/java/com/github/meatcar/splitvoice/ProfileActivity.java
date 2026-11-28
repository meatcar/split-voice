package com.github.meatcar.splitvoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProfileActivity extends Activity {
    static final String ID = "id";

    private RouteController routes;
    private Settings settings;
    private String id;
    private LinearLayout name;
    private LinearLayout mics;
    private LinearLayout headphones;
    private final Runnable listener = this::render;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        routes = ((SplitVoiceApplication) getApplication()).routes();
        settings = routes.settings();
        id = getIntent().getStringExtra(ID);
        Ui.screen(this, "");
        name = Ui.section(this, R.string.profile_name, null);
        mics = Ui.section(this, R.string.mics, getString(R.string.mics_hint));
        headphones = Ui.section(this, R.string.headphones, getString(R.string.headphones_hint));
        Button delete = (Button) getLayoutInflater().inflate(R.layout.delete_button, findViewById(R.id.content), false);
        delete.setOnClickListener(view -> confirmDelete());
        ((LinearLayout) findViewById(R.id.content)).addView(delete);
    }

    @Override protected void onStart() {
        super.onStart();
        routes.observe(listener);
    }

    @Override protected void onStop() {
        routes.removeObserver(listener);
        super.onStop();
    }

    private Profile profile() { return settings.profile(id); }

    private void render() {
        Profile profile = profile();
        if (profile == null) {
            finish();
            return;
        }
        ((TextView) findViewById(R.id.screen_title)).setText(profile.name());
        name.removeAllViews();
        Ui.row(name, profile.name(), null, view -> Ui.askName(this, R.string.rename, profile.name(), R.string.save,
                value -> {
                    if (!value.isEmpty()) settings.save(profile().withName(value));
                    render();
                }));
        renderSide(mics, profile, true);
        renderSide(headphones, profile, false);
    }

    private void renderSide(LinearLayout card, Profile profile, boolean input) {
        card.removeAllViews();
        List<Device> devices = profile.side(input);
        for (int i = 0; i < devices.size(); i++) {
            Device device = devices.get(i);
            int index = i;
            String label = device.label(this);
            View row = Ui.row(card, label, routes.status() == null ? null
                    : getString(connected(device) ? R.string.connected : R.string.not_connected), null);
            show(row, R.id.row_up, getString(R.string.move_up_device, label), index > 0,
                    view -> move(input, index, index - 1));
            show(row, R.id.row_down, getString(R.string.move_down_device, label), index < devices.size() - 1,
                    view -> move(input, index, index + 1));
            show(row, R.id.row_remove, getString(R.string.remove_device, label), true, view -> {
                List<Device> changed = new ArrayList<>(profile().side(input));
                changed.remove(index);
                update(input, changed);
            });
        }
        Ui.action(card, input ? R.string.add_mic : R.string.add_headphones, view -> add(input));
    }

    private static void show(View row, int id, String description, boolean visible, View.OnClickListener onClick) {
        View button = row.findViewById(id);
        button.setContentDescription(description);
        button.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        button.setOnClickListener(onClick);
    }

    private boolean connected(Device device) {
        return routes.status().devices().stream().anyMatch(other -> other.key().equals(device.key()));
    }

    private void move(boolean input, int from, int to) {
        List<Device> changed = new ArrayList<>(profile().side(input));
        changed.add(to, changed.remove(from));
        update(input, changed);
    }

    private void update(boolean input, List<Device> devices) {
        settings.save(profile().withSide(input, devices));
        render();
    }

    private void add(boolean input) {
        RouteController.Status status = routes.status();
        if (status == null) {
            new AlertDialog.Builder(this).setMessage(R.string.connect_to_add)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        Map<String, Device> options = new LinkedHashMap<>();
        for (Device device : status.devices()) {
            if (device.input() == input && device.selectable()
                    && profile().side(input).stream().noneMatch(saved -> saved.key().equals(device.key()))) {
                options.putIfAbsent(device.key(), device);
            }
        }
        List<Device> choices = new ArrayList<>(options.values());
        if (choices.isEmpty()) {
            new AlertDialog.Builder(this).setMessage(input ? R.string.no_mics_to_add : R.string.no_headphones_to_add)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        String[] labels = choices.stream().map(device -> device.label(this)).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(input ? R.string.add_mic : R.string.add_headphones)
                .setItems(labels, (dialog, index) -> {
                    List<Device> changed = new ArrayList<>(profile().side(input));
                    Device chosen = choices.get(index);
                    changed.add(new Device(0, chosen.input(), chosen.type(), chosen.address(), chosen.name()));
                    update(input, changed);
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void confirmDelete() {
        Profile profile = profile();
        new AlertDialog.Builder(this).setTitle(getString(R.string.delete_title, profile.name()))
                .setMessage(R.string.delete_body)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    settings.delete(id);
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
