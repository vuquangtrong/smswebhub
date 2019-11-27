package com.trongvq.smswebhub.ui.smsforward;

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

public class SmsForwardFragment extends Fragment {
    private TextView text_guide_preview_request = null;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_sms_forward, container, false);

        final EditText input_url = root.findViewById(R.id.input_url);
        input_url.setText(DataHandler.getInstance().getSmsRedirectURL());

        final EditText input_prefix_sender = root.findViewById(R.id.input_prefix_sender);
        input_prefix_sender.setText(DataHandler.getInstance().getPrefixSender());

        final EditText input_prefix_content = root.findViewById(R.id.input_prefix_content);
        input_prefix_content.setText(DataHandler.getInstance().getPrefixContent());

        final EditText input_prefix_time = root.findViewById(R.id.input_prefix_time);
        input_prefix_time.setText(DataHandler.getInstance().getPrefixTime());

        final EditText input_prefix_token = root.findViewById(R.id.input_prefix_token);
        input_prefix_token.setText(DataHandler.getInstance().getPrefixToken());

        final EditText input_token = root.findViewById(R.id.input_token);
        input_token.setText(DataHandler.getInstance().getSmsToken());

        final Button btn_set = root.findViewById(R.id.btn_set);
        btn_set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DataHandler.getInstance().setSmsRedirectURL(input_url.getText().toString());
                input_url.setText(DataHandler.getInstance().getSmsRedirectURL());

                DataHandler.getInstance().setPrefixSender(input_prefix_sender.getText().toString());
                DataHandler.getInstance().setPrefixContent(input_prefix_content.getText().toString());
                DataHandler.getInstance().setPrefixTime(input_prefix_time.getText().toString());
                DataHandler.getInstance().setPrefixToken(input_prefix_token.getText().toString());
                DataHandler.getInstance().setSmsToken(input_token.getText().toString());
                updatePreview();
            }
        });

        text_guide_preview_request = root.findViewById(R.id.text_guide_preview_request);
        updatePreview();

        final TextView text_last_data = root.findViewById(R.id.text_last_data);
        DataHandler.getInstance().getTextLastForwardedData().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                text_last_data.setText(s);
            }
        });

        return root;
    }

    private void updatePreview() {
        text_guide_preview_request.setText(
                String.format("%s/?%s={number}&%s={content}&%s={time}&%s=%s",
                        DataHandler.getInstance().getSmsRedirectURL(),
                        DataHandler.getInstance().getPrefixSender(),
                        DataHandler.getInstance().getPrefixContent(),
                        DataHandler.getInstance().getPrefixTime(),
                        DataHandler.getInstance().getPrefixToken(),
                        DataHandler.getInstance().getSmsToken()
                )
        );
    }
}