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

// NOTE: keeps the process alive while auto split is on; audio changes only wake the controller
public final class AutoRouteService extends Service {
    private static final String CHANNEL = "automatic";
    private static final int ID = 2;

    private AutoRoute auto;
    private AudioWatch watch;
    private boolean watching;

    static void sync(Context context) {
        Intent intent = new Intent(context, AutoRouteService.class);
        if (AutoRoute.enabled(context)) context.startForegroundService(intent);
        else context.stopService(intent);
    }

    @Override public void onCreate() {
        super.onCreate();
        RouteController routes = ((SplitVoiceApplication) getApplication()).routes();
        auto = new AutoRoute(routes);
        watch = new AudioWatch(this, routes::refresh);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        if (!AutoRoute.enabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!watching) {
            watching = true;
            watch.start();
            auto.start();
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        if (watching) {
            auto.stop();
            watch.stop();
            watching = false;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private Notification notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                getString(R.string.automatic_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent controls = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_split_voice)
                .setContentTitle(getString(R.string.automatic_title))
                .setContentText(getString(R.string.automatic_body))
                .setContentIntent(controls).setOngoing(true).setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PRIVATE).build();
    }
}
