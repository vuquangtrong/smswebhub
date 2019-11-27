package com.trongvq.smswebhub.ui.webhub;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

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

                    // re-connect
                    DataHandler.getInstance().disconnectWebHub();
                    DataHandler.getInstance().connectWebHub();
                }
            }
        });

        final TextView text_webHubStatus = root.findViewById(R.id.text_webHubStatus);
        DataHandler.getInstance().getTextWebHubStatus().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                text_webHubStatus.setText(s);
            }
        });

        final TextView text_webHubLastCommand = root.findViewById(R.id.text_webHubLastCommand);
        DataHandler.getInstance().getTextWebHubLastCommand().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                text_webHubLastCommand.setText(s);
            }
        });

        return root;
    }
}