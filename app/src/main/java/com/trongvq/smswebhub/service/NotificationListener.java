package com.trongvq.smswebhub.service;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.trongvq.smswebhub.data.DataHandler;

public class NotificationListener extends NotificationListenerService {
    private final String TAG = NotificationListener.class.getSimpleName();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.i(TAG, "onNotificationPosted: " + sbn.getPackageName() + " " + sbn.getPostTime() + "\n" + sbn.getNotification().toString());

        // do our work
        DataHandler.getInstance().forwardNotification(sbn.getPackageName(), sbn.getPostTime(), sbn.getNotification());
    }
}
