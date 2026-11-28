package com.github.meatcar.splitvoice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.List;

// NOTE: keeps the process alive so routing follows every voice chat; audio events wake the controller
public final class AutoRouteService extends Service {
    private static final String CHANNEL = "automatic";
    private static final String PICK = "com.github.meatcar.splitvoice.PICK";
    private static final int ID = 2;

    private RouteController routes;
    private AudioWatch watch;
    private final Runnable listener = this::update;
    private boolean watching;

    static boolean wanted(Context context, RouteController routes) {
        return new Settings(context).anythingOn() || routes.pending();
    }

    static void sync(Context context, RouteController routes) {
        Intent intent = new Intent(context, AutoRouteService.class);
        if (wanted(context, routes)) context.startForegroundService(intent);
        else context.stopService(intent);
    }

    @Override public void onCreate() {
        super.onCreate();
        routes = ((SplitVoiceApplication) getApplication()).routes();
        watch = new AudioWatch(this, routes::refresh);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,
                getString(R.string.automatic_channel), NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        if (intent != null && PICK.equals(intent.getAction())) routes.pickForCall(intent.getStringExtra("choice"));
        if (!wanted(this, routes)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!watching) {
            watching = true;
            watch.start();
            routes.observe(listener);
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        if (watching) {
            routes.removeObserver(listener);
            watch.stop();
            watching = false;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void update() {
        getSystemService(NotificationManager.class).notify(ID, notification());
        if (!wanted(this, routes)) stopSelf();
    }

    Notification notification() {
        PendingIntent controls = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_split_voice)
                .setContentTitle(routes.headline())
                .setContentText(routes.detail())
                .setContentIntent(controls).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        if (routes.state() == RouteController.State.CALL) {
            int request = 1;
            for (String choice : actions(routes.choices(), routes.status().choice())) {
                Intent pick = new Intent(this, AutoRouteService.class).setAction(PICK).putExtra("choice", choice);
                builder.addAction(new Notification.Action.Builder(null, routes.label(choice), PendingIntent.getService(
                        this, request++, pick, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
            }
        }
        return builder.build();
    }

    static List<String> actions(List<String> choices, String current) {
        List<String> others = new ArrayList<>(choices);
        others.remove(current);
        boolean off = others.remove(Settings.OFF);
        List<String> result = new ArrayList<>(others.subList(0, Math.min(others.size(), off ? 2 : 3)));
        if (off) result.add(Settings.OFF);
        return result;
    }
}
