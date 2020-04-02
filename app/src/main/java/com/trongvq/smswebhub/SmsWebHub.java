package com.trongvq.smswebhub;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import com.trongvq.smswebhub.data.DataHandler;
import com.trongvq.smswebhub.service.SmsWebService;

import pub.devrel.easypermissions.EasyPermissions;

public class SmsWebHub extends Application {
    private final String TAG = SmsWebHub.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "SmsWebHub starts");

        // init data provider
        DataHandler.getInstance().init(getApplicationContext());

        // check permission
        if (EasyPermissions.hasPermissions(this, DataHandler.wantedPermissions)) {
            // start background service
            Log.i(TAG, "request foreground service");
            startForegroundService(new Intent(getApplicationContext(), SmsWebService.class));
        }
    }
}
