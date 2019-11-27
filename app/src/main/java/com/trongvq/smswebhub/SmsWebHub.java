package com.trongvq.smswebhub;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.util.Log;

import com.trongvq.smswebhub.data.DataHandler;
import com.trongvq.smswebhub.service.SmsWebService;

import pub.devrel.easypermissions.EasyPermissions;

public class SmsWebHub extends Application {
    private final String TAG = SmsWebHub.class.getSimpleName();
    private final String[] wantedPermissions = {
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.INTERNET
    };

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "SmsWebHub starts");

        // init data provider
        DataHandler.getInstance().init(getApplicationContext());

        // check permission
        if (EasyPermissions.hasPermissions(this, wantedPermissions)) {
            // start background service
            Log.i(TAG, "request foreground service");
            startForegroundService(new Intent(getApplicationContext(), SmsWebService.class));
        }
    }
}
