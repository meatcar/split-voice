package com.github.meatcar.splitvoice;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RecoveryNotificationTest {
    @Test public void reminderIsOptInAndHidingItDoesNotClearRecovery() throws Exception {
        var app = (SplitVoiceApplication) RuntimeEnvironment.getApplication();
        var journal = app.getSharedPreferences("routing-recovery", Context.MODE_PRIVATE);
        assertTrue(journal.edit().putBoolean("pending", true).commit());
        var manager = shadowOf(app.getSystemService(NotificationManager.class));
        RecoveryNotification.update(app, app.routes());
        assertEquals(0, manager.size());
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        RecoveryNotification.setEnabled(app, true, app.routes());
        Notification reminder = manager.getNotification(1);
        assertNotNull(reminder);
        assertNotEquals(0, reminder.flags & Notification.FLAG_ONGOING_EVENT);
        assertEquals("Stop Voice, then open controls to restore.", reminder.extras.getString(Notification.EXTRA_TEXT));
        reminder.contentIntent.send();
        assertEquals(MainActivity.class.getName(), shadowOf(app).getNextStartedActivity().getComponent().getClassName());
        RecoveryNotification.setEnabled(app, false, app.routes());
        assertEquals(0, manager.size());
        assertTrue(journal.getBoolean("pending", false));
    }

    @Test public void reminderDisappearsOnlyWhenRecoveryRecordIsCleared() {
        var app = (SplitVoiceApplication) RuntimeEnvironment.getApplication();
        var journal = app.getSharedPreferences("routing-recovery", Context.MODE_PRIVATE);
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        assertTrue(journal.edit().putBoolean("pending", true).commit());
        RecoveryNotification.setEnabled(app, true, app.routes());
        var manager = shadowOf(app.getSystemService(NotificationManager.class));
        assertEquals(1, manager.size());
        assertTrue(journal.edit().clear().commit());
        RecoveryNotification.update(app, app.routes());
        assertEquals(0, manager.size());
    }
}
