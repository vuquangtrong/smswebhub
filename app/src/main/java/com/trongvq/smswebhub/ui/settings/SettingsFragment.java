package com.trongvq.smswebhub.ui.settings;

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

import com.trongvq.smswebhub.R;
import com.trongvq.smswebhub.data.DataHandler;
import com.trongvq.smswebhub.service.SmsWebService;

public class SettingsFragment extends Fragment {
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);

        final TextView text_guide = root.findViewById(R.id.text_guide);
        DataHandler.getInstance().getTextActivationStatus().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                text_guide.setText(s);
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

        DataHandler.getInstance().getTextServerStatus().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                btn_toggle_service.setText(s);
            }
        });

        // check activation
        if (DataHandler.getInstance().isActivated()) {
            DataHandler.getInstance().setTextActivationStatus(getString(R.string.license_activated));
        } else {
            DataHandler.getInstance().setTextActivationStatus(getString(R.string.license_not_activated));
        }

        return root;
    }
}