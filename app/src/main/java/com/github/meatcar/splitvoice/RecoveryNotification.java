package com.github.meatcar.splitvoice;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

final class RecoveryNotification {
    private static final String CHANNEL = "recovery";
    private static final int ID = 1;

    static boolean enabled(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("notifications", false)
                && (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED);
    }

    static void setEnabled(Context context, boolean enabled, RouteController routes) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("notifications", enabled).apply();
        update(context, routes);
    }

    static void update(Context context, RouteController routes) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!routes.pending() || !enabled(context)) {
            manager.cancel(ID);
            return;
        }
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent controls = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_split_voice)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.notification_body))
                .setStyle(new Notification.BigTextStyle().bigText(context.getString(R.string.notification_body)))
                .setContentIntent(controls).setOngoing(true).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setShowWhen(false)
                .addAction(new Notification.Action.Builder(null, context.getString(R.string.controls), controls).build())
                .build();
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) manager.notify(ID, notification);
    }
}
