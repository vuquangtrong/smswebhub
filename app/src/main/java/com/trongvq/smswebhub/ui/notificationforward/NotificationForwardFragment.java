package com.trongvq.smswebhub.ui.notificationforward;

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

public class NotificationForwardFragment extends Fragment {
    private TextView text_guide_preview_request = null;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_notification_forward, container, false);

        final EditText input_url = root.findViewById(R.id.input_url);
        input_url.setText(DataHandler.getInstance().getNotiRedirectURL());

        final EditText input_prefix_app = root.findViewById(R.id.input_prefix_app);
        input_prefix_app.setText(DataHandler.getInstance().getNotiPrefixApp());

        final EditText input_prefix_title = root.findViewById(R.id.input_prefix_title);
        input_prefix_title.setText(DataHandler.getInstance().getNotiPrefixTitle());

        final EditText input_prefix_content = root.findViewById(R.id.input_prefix_content);
        input_prefix_content.setText(DataHandler.getInstance().getNotiPrefixContent());

        final EditText input_prefix_time = root.findViewById(R.id.input_prefix_time);
        input_prefix_time.setText(DataHandler.getInstance().getNotiPrefixTime());

        final EditText input_prefix_token = root.findViewById(R.id.input_prefix_token);
        input_prefix_token.setText(DataHandler.getInstance().getNotiPrefixToken());

        final EditText input_token = root.findViewById(R.id.input_token);
        input_token.setText(DataHandler.getInstance().getNotiToken());

        final Button btn_set = root.findViewById(R.id.btn_set);
        btn_set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DataHandler.getInstance().setNotiRedirectURL(input_url.getText().toString());
                input_url.setText(DataHandler.getInstance().getNotiRedirectURL());

                DataHandler.getInstance().setNotiPrefixApp(input_prefix_app.getText().toString());
                DataHandler.getInstance().setNotiPrefixTitle(input_prefix_title.getText().toString());
                DataHandler.getInstance().setNotiPrefixContent(input_prefix_content.getText().toString());
                DataHandler.getInstance().setNotiPrefixTime(input_prefix_time.getText().toString());
                DataHandler.getInstance().setNotiPrefixToken(input_prefix_token.getText().toString());
                DataHandler.getInstance().setNotiToken(input_token.getText().toString());
                updatePreview();
            }
        });

        text_guide_preview_request = root.findViewById(R.id.text_guide_preview_request);
        updatePreview();

        final TextView text_last_data = root.findViewById(R.id.text_last_data);
        DataHandler.getInstance().getTextLastForwardedNotification().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                text_last_data.setText(s);
            }
        });

        return root;
    }

    private void updatePreview() {
        text_guide_preview_request.setText(
                String.format("%s/?%s={app}&%s={title}&%s={content}&%s={time}&%s=%s",
                        DataHandler.getInstance().getNotiRedirectURL(),
                        DataHandler.getInstance().getNotiPrefixApp(),
                        DataHandler.getInstance().getNotiPrefixTitle(),
                        DataHandler.getInstance().getNotiPrefixContent(),
                        DataHandler.getInstance().getNotiPrefixTime(),
                        DataHandler.getInstance().getNotiPrefixToken(),
                        DataHandler.getInstance().getNotiToken()
                )
        );
    }
}