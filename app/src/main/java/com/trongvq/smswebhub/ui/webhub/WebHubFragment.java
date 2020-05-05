package com.trongvq.smswebhub.ui.webhub;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import com.trongvq.smswebhub.R;
import com.trongvq.smswebhub.data.DataHandler;

public class WebHubFragment extends Fragment {

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_web_hub, container, false);

        final EditText input_url = root.findViewById(R.id.input_url);
        input_url.setText(DataHandler.getInstance().getWebHubURL());

        final Button btn_connect = root.findViewById(R.id.btn_connect);
        btn_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // if URL changes
                if (!DataHandler.getInstance().getWebHubURL().equals(input_url.getText().toString())) {
                    DataHandler.getInstance().setWebHubURL(input_url.getText().toString());
                    input_url.setText(DataHandler.getInstance().getWebHubURL());
                } else {
                    Toast.makeText(DataHandler.getInstance().getAppContext(), "URL is not changed! If service is not started, turn it on in Settings tab!", Toast.LENGTH_LONG).show();
                }
                // re-connect
                if (DataHandler.getInstance().isServiceStarted()) {
                    DataHandler.getInstance().disconnectWebHub();
                    DataHandler.getInstance().connectWebHub();
                } else {
                    Toast.makeText(DataHandler.getInstance().getAppContext(), "Service is not started. Turn it on in Settings tab!", Toast.LENGTH_LONG).show();
                }
            }
        });

        final EditText input_token = root.findViewById(R.id.input_token);
        input_token.setText((DataHandler.getInstance().getWebHubToken()));

        final Button btn_set_token = root.findViewById(R.id.btn_set_token);
        btn_set_token.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!DataHandler.getInstance().getWebHubToken().equals(input_token.getText().toString())) {
                    DataHandler.getInstance().setWebHubToken(input_token.getText().toString());
                    Toast.makeText(DataHandler.getInstance().getAppContext(), "New token is applied on your mobile!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(DataHandler.getInstance().getAppContext(), "Token is not changed!", Toast.LENGTH_LONG).show();
                }
            }
        });

        final TextView text_webHubStatus = root.findViewById(R.id.text_webHubStatus);
        DataHandler.getInstance().getTextWebHubStatus().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                text_webHubStatus.setText(s);
            }
        });

        final TextView text_webHubLastCommand = root.findViewById(R.id.text_webHubLastCommand);
        DataHandler.getInstance().getTextWebHubLastCommand().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                text_webHubLastCommand.setText(s);
            }
        });

        return root;
    }
}