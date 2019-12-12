package com.trongvq.smswebhub.data;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.trongvq.smswebhub.BuildConfig;
import com.trongvq.smswebhub.R;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.lang.reflect.Method;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DataHandler {
    private final String TAG = DataHandler.class.getSimpleName();

    // SINGLETON //
    private DataHandler() {
        if (ourInstance != null) {
            throw new RuntimeException("Please use getInstance() method to get the single instance of DataHandler class!");
        }
    }

    private static final DataHandler ourInstance = new DataHandler();

    public static DataHandler getInstance() {
        return ourInstance;
    }

    private Context appContext = null;

    // INITIAL //
    public void init(Context context) {
        if (context == null) {
            throw new RuntimeException("Please use Application context to initialize an instance of DataHandler class!");
        }

        if (appContext == null) {
            appContext = context;
        }

        // init pref
        sharedPref = appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE);

        // load saved data
        loadData();
    }

    public Context getAppContext() {
        return appContext;
    }

    private void loadData() {
        Log.d(TAG, "load saved settings");
        loadWebHubURL();
        loadWebHubToken();
        loadSmsRedirectURL();
        loadPrefixSender();
        loadPrefixContent();
        loadPrefixTime();
        loadPrefixToken();
        loadSmsToken();
    }

    // SHARED PREFERENCE //
    private SharedPreferences sharedPref = null;
    private static final String PREF = "pref_smswebhub_";

    // SERVICE STATUS //
    private boolean isServiceStarted = false;

    public void setServiceStarted(boolean status) {
        isServiceStarted = status;
        if (isServiceStarted) {
            setTextServerStatus(appContext.getString(R.string.stop_service));
            setTextWebHubStatus("Service started!");
        } else {
            setTextServerStatus(appContext.getString(R.string.start_service));
            setTextWebHubStatus("Service stopped!");
        }
    }

    public boolean isServiceStarted() {
        return isServiceStarted;
    }

    private final MutableLiveData<String> textServerStatus = new MutableLiveData<>();

    public LiveData<String> getTextServerStatus() {
        return textServerStatus;
    }

    private void setTextServerStatus(String text) {
        textServerStatus.postValue(text);
    }

    // WEB HUB URL //
    private static final String PREF_WEB_HUB_URL = PREF + "webhub_url";
    private String webHubURL = "ws://103.104.119.189:8080";

    public String getWebHubURL() {
        return webHubURL;
    }

    public void setWebHubURL(String url) {
        if (!url.startsWith("ws://")) {
            webHubURL = "ws://" + url;
        } else {
            webHubURL = url;
        }
        saveWebHubURL();
    }

    private void saveWebHubURL() {
        sharedPref.edit().putString(
                PREF_WEB_HUB_URL,
                webHubURL
        ).apply();
    }

    private void loadWebHubURL() {
        String info = sharedPref.getString(PREF_WEB_HUB_URL, null);
        if (info != null) {
            webHubURL = info;
        }
        Log.d(TAG, "webHubURL = " + webHubURL);
    }

    // WEB HUB TOKEN //
    private static final String PREF_WEB_HUB_TOKEN = PREF + "webhub_token";
    private String webHubToken = "secret";

    public String getWebHubToken() {
        return webHubToken;
    }

    public void setWebHubToken(String token) {
        webHubToken = token;
        saveWebHubToken();
    }

    private void saveWebHubToken() {
        sharedPref.edit().putString(
                PREF_WEB_HUB_TOKEN,
                webHubToken
        ).apply();
    }

    private void loadWebHubToken() {
        String info = sharedPref.getString(PREF_WEB_HUB_TOKEN, null);
        if (info != null) {
            webHubToken = info;
        }
        Log.d(TAG, "webHubToken = " + webHubToken);
    }

    // WEB HUB SOCKET //
    private WebSocketClient wsClient = null;
    private boolean isConnected = false;
    private Handler connectHandler = new Handler(Looper.getMainLooper());
    private Runnable connectRunner = new Runnable() {
        @Override
        public void run() {
            synchronized (this) {
                if (!isConnected && isServiceStarted) {
                    connectWebHub();
                }
            }
        }
    };

    final class WebHubWebSocket extends WebSocketClient {

        WebHubWebSocket(java.net.URI uri) {
            super(uri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            Log.i(TAG, "WebHubWebSocket: onOpen " + handshakedata.toString());
            setTextWebHubStatus("Connected!");
            send(" version: " + BuildConfig.VERSION_NAME);
            isConnected = true;
        }

        @Override
        public void onMessage(String message) {
            handleWebSocketMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            Log.e(TAG, "WebHubWebSocket: onClose " + code + " " + reason + " " + remote);
            setTextWebHubStatus("Connection is close! Reconnect soon.");
            isConnected = false;
            connectHandler.removeCallbacks(connectRunner);
            connectHandler.postDelayed(connectRunner, 5000);
        }

        @Override
        public void onError(Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "WebHubWebSocket: onError ");
            setTextWebHubStatus("Error in connection! Reconnect soon.");
            isConnected = false;
            connectHandler.removeCallbacks(connectRunner);
            connectHandler.postDelayed(connectRunner, 5000);
        }
    }

    public void connectWebHub() {
        String url = getWebHubURL();

        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception | Error ex) {
            ex.printStackTrace();
            return;
        }

        Log.i(TAG, "connectWebHub");
        wsClient = new WebHubWebSocket(uri);
        Log.i(TAG, "connectWebHub: wsClient.connect()");
        wsClient.connect();
    }

    public void disconnectWebHub() {
        Log.i(TAG, "disconnectWebHub");
        try {
            if (wsClient != null) {
                Log.i(TAG, "disconnectWebHub: wsClient.close()");
                wsClient.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        wsClient = null;
    }

    private void handleWebSocketMessage(String message) {
        if (!isActivated()) {
            setTextWebHubLastCommand(appContext.getString(R.string.license_not_activated));
            setTextActivationStatus(appContext.getString(R.string.license_not_activated));
            return;
        }

        Log.d(TAG, "message = " + message);
        setTextWebHubLastCommand(message);
        responseWebHub("GOT " + message);
        if (message.startsWith("/sms")) {
            // parse the params
            Uri uri = Uri.parse("http://example.com" + message);

            String from = uri.getQueryParameter("from");
            String number = uri.getQueryParameter("number");
            String content = uri.getQueryParameter("content");
            String token = uri.getQueryParameter("token");

            Log.d(TAG, "from = " + from);
            Log.d(TAG, "number = " + number);
            Log.d(TAG, "content = " + content);
            Log.d(TAG, "token = " + token);

            if (token != null && token.equals(webHubToken)) {
                try {
                    sendSMS(from, number, content);
                } catch (Exception | Error ex) {
                    ex.printStackTrace();
                }
            }
        } else if (message.startsWith("/ussd")) {
            // parse the params
            Uri uri = Uri.parse("http://example.com" + message);

            String from = uri.getQueryParameter("from");
            String cmd = uri.getQueryParameter("cmd");
            String token = uri.getQueryParameter("token");

            Log.d(TAG, "from = " + from);
            Log.d(TAG, "cmd = " + cmd);
            Log.d(TAG, "token = " + token);

            if (token != null && token.equals(webHubToken)) {
                try {
                    requestUSSD(from, "*" + cmd + "#");
                } catch (Exception | Error ex) {
                    ex.printStackTrace();
                }
            }

        } else if (message.startsWith("/call")) {
            // parse the params
            Uri uri = Uri.parse("http://example.com" + message);
            String from = uri.getQueryParameter("from");
            String number = uri.getQueryParameter("number");
            String time = uri.getQueryParameter("time");
            String token = uri.getQueryParameter("token");
            String end = uri.getQueryParameter("end");

            Log.d(TAG, "from = " + from);
            Log.d(TAG, "number = " + number);
            Log.d(TAG, "time = " + time);
            Log.d(TAG, "token = " + token);
            Log.d(TAG, "end = " + end);

            if (token != null && token.equals(webHubToken)) {
                if (end != null && end.equals("y")) {
                    endCall();
                } else {
                    try {
                        startCall(from, number);

                        int duration = 5;
                        try {
                            if (time != null) {
                                duration += Integer.parseInt(time);
                            }
                        } catch (Exception | Error ex) {
                            ex.printStackTrace();
                        }

                        // schedule to end call
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                endCall();
                            }
                        }, duration * 1000);

                    } catch (Exception | Error ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    private void responseWebHub(String message) {
        try {
            if (wsClient != null && wsClient.isOpen()) {
                wsClient.send(message);
            }
        } catch (Exception | Error ex) {
            ex.printStackTrace();
        }
    }

    // WEB HUB STATUS //
    private final MutableLiveData<String> textWebHubStatus = new MutableLiveData<>();

    public LiveData<String> getTextWebHubStatus() {
        return textWebHubStatus;
    }

    private void setTextWebHubStatus(String text) {
        textWebHubStatus.postValue(text);
    }

    private final MutableLiveData<String> textWebHubLastCommand = new MutableLiveData<>();

    public LiveData<String> getTextWebHubLastCommand() {
        return textWebHubLastCommand;
    }

    private void setTextWebHubLastCommand(String text) {
        textWebHubLastCommand.postValue(text);
    }

    // SMS FORWARD CONFIGS //
    private static final String PREF_SMS_REDIRECT_URL = PREF + "smsredirect_url";
    private static final String PREF_PREFIX_SENDER = PREF + "prefix_sender";
    private static final String PREF_PREFIX_CONTENT = PREF + "prefix_content";
    private static final String PREF_PREFIX_TIME = PREF + "prefix_time";
    private static final String PREF_PREFIX_TOKEN = PREF + "prefix_token";
    private static final String PREF_SMS_TOKEN = PREF + "sms_token";
    private String SmsRedirectURL = "http://103.104.119.189:8000";
    private String PrefixSender = "sender";
    private String PrefixContent = "content";
    private String PrefixTime = "time";
    private String PrefixToken = "token";
    private String SmsToken = "secret";

    //
    public String getSmsRedirectURL() {
        return SmsRedirectURL;
    }

    public void setSmsRedirectURL(String url) {
        if (!url.startsWith("http://")) {
            SmsRedirectURL = "http://" + url;
        } else {
            SmsRedirectURL = url;
        }
        saveSmsRedirectURL();
    }

    private void saveSmsRedirectURL() {
        sharedPref.edit().putString(
                PREF_SMS_REDIRECT_URL,
                SmsRedirectURL
        ).apply();
    }

    private void loadSmsRedirectURL() {
        String info = sharedPref.getString(PREF_SMS_REDIRECT_URL, null);
        if (info != null) {
            SmsRedirectURL = info;
        }
        Log.d(TAG, "SmsRedirectURL = " + SmsRedirectURL);
    }

    //
    public String getPrefixSender() {
        return PrefixSender;
    }

    public void setPrefixSender(String info) {
        PrefixSender = info;
        savePrefixSender();
    }

    private void savePrefixSender() {
        sharedPref.edit().putString(
                PREF_PREFIX_SENDER,
                PrefixSender
        ).apply();
    }

    private void loadPrefixSender() {
        String info = sharedPref.getString(PREF_PREFIX_SENDER, null);
        if (info != null) {
            PrefixSender = info;
        }
        Log.d(TAG, "PrefixSender = " + PrefixSender);
    }

    //
    public String getPrefixContent() {
        return PrefixContent;
    }

    public void setPrefixContent(String info) {
        PrefixContent = info;
        savePrefixContent();
    }

    private void savePrefixContent() {
        sharedPref.edit().putString(
                PREF_PREFIX_CONTENT,
                PrefixContent
        ).apply();
    }

    private void loadPrefixContent() {
        String info = sharedPref.getString(PREF_PREFIX_CONTENT, null);
        if (info != null) {
            PrefixContent = info;
        }
        Log.d(TAG, "PrefixContent = " + PrefixContent);
    }

    //
    public String getPrefixTime() {
        return PrefixTime;
    }

    public void setPrefixTime(String info) {
        PrefixTime = info;
        savePrefixTime();
    }

    private void savePrefixTime() {
        sharedPref.edit().putString(
                PREF_PREFIX_TIME,
                PrefixTime
        ).apply();
    }

    private void loadPrefixTime() {
        String info = sharedPref.getString(PREF_PREFIX_TIME, null);
        if (info != null) {
            PrefixTime = info;
        }
        Log.d(TAG, "PrefixTime = " + PrefixTime);
    }

    //
    public String getPrefixToken() {
        return PrefixToken;
    }

    public void setPrefixToken(String info) {
        PrefixToken = info;
        savePrefixToken();
    }

    private void savePrefixToken() {
        sharedPref.edit().putString(
                PREF_PREFIX_TOKEN,
                PrefixToken
        ).apply();
    }

    private void loadPrefixToken() {
        String info = sharedPref.getString(PREF_PREFIX_TOKEN, null);
        if (info != null) {
            PrefixToken = info;
        }
        Log.d(TAG, "PrefixToken = " + PrefixToken);
    }

    //
    public String getSmsToken() {
        return SmsToken;
    }

    public void setSmsToken(String info) {
        SmsToken = info;
        saveSmsToken();
    }

    private void saveSmsToken() {
        sharedPref.edit().putString(
                PREF_SMS_TOKEN,
                SmsToken
        ).apply();
    }

    private void loadSmsToken() {
        String info = sharedPref.getString(PREF_SMS_TOKEN, null);
        if (info != null) {
            SmsToken = info;
        }
        Log.d(TAG, "SmsToken = " + SmsToken);
    }

    // SMS SEND //
    private void sendSMS(final String from, final String number, final String content) {
        if (number != null && content != null) {
            try {
                if (appContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    SubscriptionManager localSubscriptionManager = (SubscriptionManager) appContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                    if (localSubscriptionManager != null && localSubscriptionManager.getActiveSubscriptionInfoCount() > 1) {
                        /* if there are 2 SIM available */

                        List<SubscriptionInfo> localList = localSubscriptionManager.getActiveSubscriptionInfoList();
                        SubscriptionInfo simInfo1 = localList.get(0);
                        SubscriptionInfo simInfo2 = localList.get(1);

                        if (from != null && from.equals("sim2")) {
                            Log.d(TAG, "SMS via SIM 2:\n" + number + "\n" + content);
                            responseWebHub("SMS via SIM 2:\n" + number + "\n" + content);
                            SmsManager.getSmsManagerForSubscriptionId(simInfo2.getSubscriptionId()).sendTextMessage(number, null, content, null, null);
                        } else {
                            Log.d(TAG, "SMS via SIM 1:\n" + number + "\n" + content);
                            responseWebHub("SMS via SIM 1:\n" + number + "\n" + content);
                            SmsManager.getSmsManagerForSubscriptionId(simInfo1.getSubscriptionId()).sendTextMessage(number, null, content, null, null);
                        }
                        return;
                    }
                }
            } catch (Exception | Error ex) {
                ex.printStackTrace();
            }

            /* send by default sim */
            Log.d(TAG, "SMS via default SIM:\n" + number + "\n" + content);
            responseWebHub("SMS via default SIM:\n" + number + "\n" + content);
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(number, null, content, null, null);
        }
    }

    // SMS FORWARD //
    private final MutableLiveData<String> textLastForwardedData = new MutableLiveData<>();

    public LiveData<String> getTextLastForwardedData() {
        return textLastForwardedData;
    }

    private void setTextLastForwardedData(String text) {
        textLastForwardedData.postValue(text);
    }

    public void forwardSMS(final String sender, final String content) {
        if (!isActivated()) {
            setTextLastForwardedData(appContext.getString(R.string.license_not_activated));
            setTextActivationStatus(appContext.getString(R.string.license_not_activated));
            return;
        }

        final Date date = Calendar.getInstance().getTime();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault());
        final String message = PrefixSender + "=" + sender + "&" +
                PrefixContent + "=" + content + "&" +
                PrefixTime + "=" + dateFormat.format(date) + "&" +
                PrefixToken + "=" + SmsToken;

        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(appContext);

        // Request a string response from the provided UrlForRedirect.
        StringRequest stringRequest = new StringRequest(
                Request.Method.POST,
                SmsRedirectURL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.i(TAG, "Message forwarded! " + response + " " + message);
                        responseWebHub("Message forwarded! " + response + " " + message);
                        setTextLastForwardedData(message);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Cannot forward SMS content! " + error.toString() + " " + message);
                        responseWebHub("Cannot forward SMS content! " + error.toString() + " " + message);
                    }
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put(PrefixSender, sender);
                params.put(PrefixContent, content);
                params.put(PrefixTime, dateFormat.format(date));
                params.put(PrefixToken, SmsToken);
                return params;
            }
        };

        // Add the request to the RequestQueue.
        Log.d(TAG, "insert SMS to queue");
        queue.add(stringRequest);
    }

    // USSD REQUEST //
    @SuppressWarnings("unchecked")
    private void requestUSSD(final String from, final String ussdCode) {

        // action when get ussd response
        ResultReceiver resultReceiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle ussdResponse) {
                // Usually you should the getParcelable() response to some Parcel child class but that's not possible here,
                // since the "UssdResponse" class isn't in the SDK so we need to reflect again to get the result of getReturnMessage()
                // and finally return that!
                try {
                    android.os.Parcelable ussdResponseObject = ussdResponse.getParcelable("USSD_RESPONSE");
                    CharSequence message = null;

                    if (ussdResponseObject != null) {
                        java.lang.reflect.Method getReturnMessage;

                        try {
                            getReturnMessage = ussdResponseObject.getClass().getMethod("getReturnMessage");
                            message = (CharSequence) getReturnMessage.invoke(ussdResponseObject);
                        } catch (Exception | Error e) {
                            e.printStackTrace();
                        }

                        if (message != null) {
                            Log.i(TAG, "USSD response = " + message);
                            responseWebHub("USSD response = " + message);
                            forwardSMS(ussdCode, message.toString());
                        }
                    }
                } catch (Exception | Error e) {
                    e.printStackTrace();
                }
            }
        };

        // Native TelephonyManager.sendUssdRequest() does not handle multiple session USSD, when carrier sends back a question, that function returns USSD_FAILED
        // We use reflection to get raw message from USSD response
        final TelephonyManager manager = (TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE);
        try {
            if (manager != null) {
                // list class methods
                Class telephonyManagerClass = Class.forName(manager.getClass().getName());
                java.lang.reflect.Method getITelephony = telephonyManagerClass.getDeclaredMethod("getITelephony");
                getITelephony.setAccessible(true);

                // Get the internal ITelephony object
                Object iTelephony = getITelephony.invoke(manager);
                if (iTelephony != null) {
                    java.lang.reflect.Method[] methodList = iTelephony.getClass().getMethods();
                    java.lang.reflect.Method handleUssdRequest = null;

                    // Somehow, the method wouldn't come up if I simply used: iTelephony.getClass().getMethod('handleUssdRequest')
                    for (java.lang.reflect.Method method : methodList)
                        if (method.getName().equals("handleUssdRequest")) {
                            handleUssdRequest = method;
                            break;
                        }

                    // Now the real reflection magic happens
                    if (handleUssdRequest != null) {
                        handleUssdRequest.setAccessible(true);

                        try {
                            if (appContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                                SubscriptionManager localSubscriptionManager = (SubscriptionManager) appContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                                if (localSubscriptionManager != null && localSubscriptionManager.getActiveSubscriptionInfoCount() > 1) {
                                    /* if there are 2 SIM available */
                                    List<SubscriptionInfo> localList = localSubscriptionManager.getActiveSubscriptionInfoList();
                                    SubscriptionInfo simInfo1 = localList.get(0);
                                    SubscriptionInfo simInfo2 = localList.get(1);

                                    if (from != null && from.equals("sim2")) {
                                        Log.d(TAG, "USSD on SIM 2: " + ussdCode);
                                        responseWebHub("USSD on SIM 2: " + ussdCode);
                                        handleUssdRequest.invoke(iTelephony, simInfo2.getSubscriptionId(), ussdCode, resultReceiver);
                                    } else {
                                        Log.d(TAG, "USSD on SIM 1: " + ussdCode);
                                        responseWebHub("USSD on SIM 1: " + ussdCode);
                                        handleUssdRequest.invoke(iTelephony, simInfo1.getSubscriptionId(), ussdCode, resultReceiver);
                                    }
                                } else {
                                    /* request on default SIM */
                                    Log.d(TAG, "USSD on default SIM: " + ussdCode);
                                    responseWebHub("USSD on default SIM: " + ussdCode);
                                    handleUssdRequest.invoke(iTelephony, SubscriptionManager.getDefaultSubscriptionId(), ussdCode, resultReceiver);
                                }
                                return;
                            }
                        } catch (Exception | Error ex) {
                            ex.printStackTrace();
                        }

                        /* request on default SIM */
                        Log.d(TAG, "USSD on default SIM: " + ussdCode);
                        responseWebHub("USSD on default SIM: " + ussdCode);
                        handleUssdRequest.invoke(iTelephony, SubscriptionManager.getDefaultSubscriptionId(), ussdCode, resultReceiver);
                    }
                }
            }
        } catch (Exception | Error e) {
            e.printStackTrace();
        }
    }

    // CALL //
    private int lastState = TelephonyManager.CALL_STATE_IDLE;
    private boolean isIncoming = false;
    private final PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            handleCallStateChanged(state, phoneNumber);
        }
    };

    // Incoming call-  goes from IDLE to RINGING when it rings, to OFFHOOK when it's answered, to IDLE when its hung up
    // Outgoing call-  goes from IDLE to OFFHOOK when it dials out, to IDLE when hung up
    private void handleCallStateChanged(int state, String number) {
        if (lastState == state) {
            // No change, debounce extras
            return;
        }
        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                isIncoming = true;
                Log.i(TAG, "Incoming call ringing " + number);
                forwardSMS(number, "Incoming call ringing");
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                // Transition of ringing->offhook are pickups of incoming calls.  Nothing done on them
                if (lastState != TelephonyManager.CALL_STATE_RINGING) {
                    isIncoming = false;
                    Log.i(TAG, "Outgoing call start " + number);
                    forwardSMS(number, "Outgoing call start");
                } else {
                    isIncoming = true;
                    Log.i(TAG, "Incoming call answered " + number);
                    forwardSMS(number, "Incoming call answered");
                }

                break;
            case TelephonyManager.CALL_STATE_IDLE:
                // Went to idle-  this is the end of a call.  What type depends on previous state(s)
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    // Ring but no pickup-  a miss
                    Log.i(TAG, "Missed call " + number);
                    forwardSMS(number, "Missed call");
                } else if (isIncoming) {
                    Log.i(TAG, "Incoming call ended " + number);
                    forwardSMS(number, "Incoming call ended");
                } else {
                    Log.i(TAG, "Outgoing call ended " + number);
                    forwardSMS(number, "Outgoing call ended");
                }
                break;
        }
        lastState = state;
    }

    public void listenPhoneState() {
        TelephonyManager telephonyManager = (TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    public void dontListenPhoneState() {
        TelephonyManager telephonyManager = (TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    private void startCall(String from, String number) {
        if (appContext.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(Intent.ACTION_CALL).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse("tel:" + number));

            if (appContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                SubscriptionManager localSubscriptionManager = (SubscriptionManager) appContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (localSubscriptionManager != null && localSubscriptionManager.getActiveSubscriptionInfoCount() > 1) {
                    if (from != null && from.equals("sim2")) {
                        Log.d(TAG, "CALL on SIM 2: " + number);
                        responseWebHub("CALL on SIM 2: " + number);
                        intent.putExtra("com.android.phone.extra.slot", 1);
                        intent.putExtra("simSlot", 1);
                    } else {
                        Log.d(TAG, "CALL on SIM 1: " + number);
                        responseWebHub("CALL on SIM 1: " + number);
                        intent.putExtra("com.android.phone.extra.slot", 0);
                        intent.putExtra("simSlot", 0);
                    }
                }
            } else {
                Log.d(TAG, "CALL on default SIM: " + number);
                responseWebHub("CALL on default SIM: " + number);
            }
            DataHandler.getInstance().getAppContext().startActivity(intent);
        }
    }

    @SuppressWarnings("unchecked")
    private void endCall() {
        TelephonyManager telephonyManager = (TelephonyManager) DataHandler.getInstance().getAppContext().getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            try {
                // get ITelephony class
                Class clazz = Class.forName(telephonyManager.getClass().getName());
                Method method = clazz.getDeclaredMethod("getITelephony");
                method.setAccessible(true);

                // do reflection
                Object telephonyService = method.invoke(telephonyManager); // Get the internal ITelephony object
                if (telephonyService != null) {
                    clazz = Class.forName(telephonyService.getClass().getName()); // Get its class
                    method = clazz.getDeclaredMethod("endCall"); // Get the "endCall()" method
                    method.setAccessible(true); // Make it accessible
                    method.invoke(telephonyService); // invoke endCall()
                    Log.d(TAG, "END CALL");
                    responseWebHub("END CALL");
                }
            } catch (Exception | Error ex) {
                ex.printStackTrace();
                Log.d(TAG, "END CALL");
                responseWebHub("END CALL");
            }
        }
    }

    // ACTIVATION //
    private static final String PREF_INSTALL_DATE = PREF + "install_date";

    private final MutableLiveData<String> textActivationStatus = new MutableLiveData<>();

    public LiveData<String> getTextActivationStatus() {
        return textActivationStatus;
    }

    public void setTextActivationStatus(String text) {
        textActivationStatus.postValue(text);
    }

    public boolean isActivated() {
        return isActivatedByPref();
    }

    private boolean isActivatedByPref() {
        String info = sharedPref.getString(PREF_INSTALL_DATE, null);
        Log.d(TAG, "pref info = " + info);
        if (info == null) {
            setInstallDate();
            return true;
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US);
            try {
                Date installDate = sdf.parse(info);
                if (installDate != null) {
                    return daysBetween(installDate, Calendar.getInstance().getTime()) <= 2;
                } else {
                    setInstallDate();
                    return true;
                }
            } catch (Exception | Error ex) {
                ex.printStackTrace();
            }
        }
        return false;
    }

    private void setInstallDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US);
        String installDate = sdf.format(Calendar.getInstance().getTime());
        sharedPref.edit().putString(
                PREF_INSTALL_DATE,
                installDate
        ).apply();
    }

    private Calendar getDatePart(Date date) {
        Calendar calendar = Calendar.getInstance();       // get calendar instance
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);            // set hour to midnight
        calendar.set(Calendar.MINUTE, 0);                 // set minute in hour
        calendar.set(Calendar.SECOND, 0);                 // set second in minute
        calendar.set(Calendar.MILLISECOND, 0);            // set millisecond in second

        return calendar;                                  // return the date part
    }

    private long daysBetween(Date startDate, Date endDate) {
        Calendar sDate = getDatePart(startDate);
        Calendar eDate = getDatePart(endDate);
        long daysBetween = 0;

        if (sDate.after(eDate)) {
            daysBetween = 365;
        }

        while (sDate.before(eDate)) {
            sDate.add(Calendar.DAY_OF_MONTH, 1);
            daysBetween++;
        }
        return daysBetween;
    }
}
