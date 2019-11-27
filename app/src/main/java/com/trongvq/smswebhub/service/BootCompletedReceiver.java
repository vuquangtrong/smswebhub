package com.trongvq.smswebhub.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootCompletedReceiver extends BroadcastReceiver {
    private final String TAG = SmsReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                Log.i(TAG, "got " + Intent.ACTION_BOOT_COMPLETED + ". Application will starts its background service");
            }
        }
    }
}
