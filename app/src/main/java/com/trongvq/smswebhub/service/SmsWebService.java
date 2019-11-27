package com.trongvq.smswebhub.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.trongvq.smswebhub.R;
import com.trongvq.smswebhub.data.DataHandler;

public class SmsWebService extends Service {
    private final String TAG = SmsWebService.class.getSimpleName();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // no binding
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // from Android O, if you want to maintain service running in background even the activity is removed,
        // you have to set service as Foreground service with Notification informing user about its activities
        NotificationCompat.Builder notificationBuilder = getNotificationBuilder(
                getApplicationContext(),
                SmsWebService.class.getCanonicalName(),
                NotificationManagerCompat.IMPORTANCE_LOW //Low importance prevents visual appearance for this notification channel on top
        );

        // set notification attributes
        notificationBuilder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setCategory(Notification.CATEGORY_SERVICE);

        Log.i(TAG, "create service");
        startForeground(0xD34DB33F, notificationBuilder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "start service via intent = " + intent.toString());

        DataHandler.getInstance().connectWebHub();

        // remain service
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "stop service");

        DataHandler.getInstance().disconnectWebHub();

        super.onDestroy();
    }

    public static NotificationCompat.Builder getNotificationBuilder(Context context, String channelId, int importance) {
        prepareChannel(context, channelId, importance);
        return new NotificationCompat.Builder(context, channelId);
    }

    @TargetApi(26)
    private static void prepareChannel(Context context, String id, int importance) {
        String appName = SmsWebService.class.getSimpleName();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Service.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            NotificationChannel nChannel = notificationManager.getNotificationChannel(id);
            if (nChannel == null) {
                nChannel = new NotificationChannel(id, appName, importance);
                notificationManager.createNotificationChannel(nChannel);
            }
        }
    }
}
