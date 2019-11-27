package com.trongvq.smswebhub.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import com.trongvq.smswebhub.data.DataHandler;

public class SmsReceiver extends BroadcastReceiver {
    private final String TAG = SmsReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            if (intent.getAction().equals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)) {
                Log.i(TAG, "got " + Telephony.Sms.Intents.SMS_RECEIVED_ACTION);

                String smsSender = "";
                String smsBody = "";

                // extract Sms from intent
                for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                    smsSender = smsMessage.getDisplayOriginatingAddress();
                    smsBody += smsMessage.getMessageBody();
                }

                Log.i(TAG, "smsSender = " + smsSender);
                Log.i(TAG, "smsBody = " + smsBody);

                // do our work
                DataHandler.getInstance().forwardSMS(smsSender, smsBody);
            }
        }
    }
}
