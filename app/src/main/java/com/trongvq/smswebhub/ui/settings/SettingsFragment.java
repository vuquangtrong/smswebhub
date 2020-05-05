package com.trongvq.smswebhub.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import com.trongvq.smswebhub.BuildConfig;
import com.trongvq.smswebhub.R;
import com.trongvq.smswebhub.data.DataHandler;
import com.trongvq.smswebhub.service.SmsWebService;

public class SettingsFragment extends Fragment {
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);

        final Button btn_notification_settings = root.findViewById(R.id.btn_notification_settings);
        btn_notification_settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                startActivity(intent);
            }
        });

        final Button btn_toggle_service = root.findViewById(R.id.btn_toggle_service);
        if (DataHandler.getInstance().isServiceStarted()) {
            btn_toggle_service.setText(R.string.stop_service);
        } else {
            btn_toggle_service.setText(R.string.start_service);
        }
        btn_toggle_service.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (DataHandler.getInstance().isServiceStarted()) {
                    DataHandler.getInstance().getAppContext().stopService(new Intent(DataHandler.getInstance().getAppContext(), SmsWebService.class));
                } else {
                    DataHandler.getInstance().getAppContext().startForegroundService(new Intent(DataHandler.getInstance().getAppContext(), SmsWebService.class));
                }
            }
        });

        DataHandler.getInstance().getTextServerStatus().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                btn_toggle_service.setText(s);
            }
        });

        final TextView log = root.findViewById(R.id.log);
        log.setText(DataHandler.getInstance().getLog());
        DataHandler.getInstance().getTextLog().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                log.setText(s);
            }
        });

        final Button btn_copy_log = root.findViewById(R.id.btn_copy_log);
        btn_copy_log.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ClipboardManager clipboardManager = (ClipboardManager) DataHandler.getInstance().getAppContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboardManager != null) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("SmsWebHub", DataHandler.getInstance().getLog()));
                }
            }
        });

        final Button btn_clear_log = root.findViewById(R.id.btn_clear_log);
        btn_clear_log.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DataHandler.getInstance().clearLog();
            }
        });

        final TextView text_version = root.findViewById(R.id.text_version);
        text_version.setText(String.format("%s: %s", getString(R.string.version), BuildConfig.VERSION_NAME));
        return root;
    }
}