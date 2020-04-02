package com.trongvq.smswebhub.data;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
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
    public static final String[] wantedPermissions = {
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.INTERNET,
//            Manifest.permission.READ_EXTERNAL_STORAGE,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

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

    // APP CONTEXT //
    private Context appContext = null;

    public Context getAppContext() {
        return appContext;
    }

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

    private void loadData() {
        Log.d(TAG, "load saved settings");

        loadWebHubURL();
        loadWebHubToken();

        loadSmsRedirectURL();
        loadSmsPrefixSender();
        loadSmsPrefixContent();
        loadSmsPrefixTime();
        loadSmsPrefixToken();
        loadSmsToken();


        loadNotiRedirectURL();
        loadNotiPrefixApp();
        loadNotiPrefixTitle();
        loadNotiPrefixContent();
        loadNotiPrefixTime();
        loadNotiPrefixToken();
        loadNotiToken();
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

    // SMS FORWARD CONFIGS //
    private static final String PREF_SMS_REDIRECT_URL = PREF + "smsredirect_url";
    private static final String PREF_SMS_PREFIX_SENDER = PREF + "prefix_sender";
    private static final String PREF_SMS_PREFIX_CONTENT = PREF + "prefix_content";
    private static final String PREF_SMS_PREFIX_TIME = PREF + "prefix_time";
    private static final String PREF_SMS_PREFIX_TOKEN = PREF + "prefix_token";
    private static final String PREF_SMS_TOKEN = PREF + "sms_token";
    private String SmsRedirectURL = "http://103.104.119.189:8000";
    private String SmsPrefixSender = "sender";
    private String SmsPrefixContent = "content";
    private String SmsPrefixTime = "time";
    private String SmsPrefixToken = "token";
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
    public String getSmsPrefixSender() {
        return SmsPrefixSender;
    }

    public void setSmsPrefixSender(String info) {
        SmsPrefixSender = info;
        saveSmsPrefixSender();
    }

    private void saveSmsPrefixSender() {
        sharedPref.edit().putString(
                PREF_SMS_PREFIX_SENDER,
                SmsPrefixSender
        ).apply();
    }

    private void loadSmsPrefixSender() {
        String info = sharedPref.getString(PREF_SMS_PREFIX_SENDER, null);
        if (info != null) {
            SmsPrefixSender = info;
        }
        Log.d(TAG, "PrefixSender = " + SmsPrefixSender);
    }

    //
    public String getSmsPrefixContent() {
        return SmsPrefixContent;
    }

    public void setSmsPrefixContent(String info) {
        SmsPrefixContent = info;
        saveSmsPrefixContent();
    }

    private void saveSmsPrefixContent() {
        sharedPref.edit().putString(
                PREF_SMS_PREFIX_CONTENT,
                SmsPrefixContent
        ).apply();
    }

    private void loadSmsPrefixContent() {
        String info = sharedPref.getString(PREF_SMS_PREFIX_CONTENT, null);
        if (info != null) {
            SmsPrefixContent = info;
        }
        Log.d(TAG, "PrefixContent = " + SmsPrefixContent);
    }

    //
    public String getSmsPrefixTime() {
        return SmsPrefixTime;
    }

    public void setSmsPrefixTime(String info) {
        SmsPrefixTime = info;
        saveSmsPrefixTime();
    }

    private void saveSmsPrefixTime() {
        sharedPref.edit().putString(
                PREF_SMS_PREFIX_TIME,
                SmsPrefixTime
        ).apply();
    }

    private void loadSmsPrefixTime() {
        String info = sharedPref.getString(PREF_SMS_PREFIX_TIME, null);
        if (info != null) {
            SmsPrefixTime = info;
        }
        Log.d(TAG, "PrefixTime = " + SmsPrefixTime);
    }

    //
    public String getSmsPrefixToken() {
        return SmsPrefixToken;
    }

    public void setSmsPrefixToken(String info) {
        SmsPrefixToken = info;
        saveSmsPrefixToken();
    }

    private void saveSmsPrefixToken() {
        sharedPref.edit().putString(
                PREF_SMS_PREFIX_TOKEN,
                SmsPrefixToken
        ).apply();
    }

    private void loadSmsPrefixToken() {
        String info = sharedPref.getString(PREF_SMS_PREFIX_TOKEN, null);
        if (info != null) {
            SmsPrefixToken = info;
        }
        Log.d(TAG, "PrefixToken = " + SmsPrefixToken);
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

    // SMS FORWARD //
    private final MutableLiveData<String> textLastForwardedData = new MutableLiveData<>();

    public LiveData<String> getTextLastForwardedData() {
        return textLastForwardedData;
    }

    private void setTextLastForwardedData(String text) {
        textLastForwardedData.postValue(text);
    }

    public void forwardSMS(final String sender, final String content) {
        final Date date = Calendar.getInstance().getTime();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault());
        final String message = SmsPrefixSender + "=" + sender + "&" +
                SmsPrefixContent + "=" + content + "&" +
                SmsPrefixTime + "=" + dateFormat.format(date) + "&" +
                SmsPrefixToken + "=" + SmsToken;

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
                params.put(SmsPrefixSender, sender);
                params.put(SmsPrefixContent, content);
                params.put(SmsPrefixTime, dateFormat.format(date));
                params.put(SmsPrefixToken, SmsToken);
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

    // NOTI FORWARD CONFIGS //
    private static final String PREF_NOTI_REDIRECT_URL = PREF + "noti_redirect_url";
    private static final String PREF_NOTI_PREFIX_APP = PREF + "noti_prefix_app";
    private static final String PREF_NOTI_PREFIX_TITLE = PREF + "noti_prefix_title";
    private static final String PREF_NOTI_PREFIX_CONTENT = PREF + "noti_prefix_content";
    private static final String PREF_NOTI_PREFIX_TIME = PREF + "noti_prefix_time";
    private static final String PREF_NOTI_PREFIX_TOKEN = PREF + "noti_prefix_token";
    private static final String PREF_NOTI_TOKEN = PREF + "noti_token";
    private String NotiRedirectURL = "http://103.104.119.189:8000";
    private String NotiPrefixApp = "app";
    private String NotiPrefixTitle = "title";
    private String NotiPrefixContent = "content";
    private String NotiPrefixTime = "time";
    private String NotiPrefixToken = "token";
    private String NotiToken = "secret";

    //
    public String getNotiRedirectURL() {
        return NotiRedirectURL;
    }

    public void setNotiRedirectURL(String url) {
        if (!url.startsWith("http://")) {
            NotiRedirectURL = "http://" + url;
        } else {
            NotiRedirectURL = url;
        }
        saveNotiRedirectURL();
    }

    private void saveNotiRedirectURL() {
        sharedPref.edit().putString(
                PREF_NOTI_REDIRECT_URL,
                NotiRedirectURL
        ).apply();
    }

    private void loadNotiRedirectURL() {
        String info = sharedPref.getString(PREF_NOTI_REDIRECT_URL, null);
        if (info != null) {
            NotiRedirectURL = info;
        }
        Log.d(TAG, "NotiRedirectURL = " + NotiRedirectURL);
    }

    //
    public String getNotiPrefixApp() {
        return NotiPrefixApp;
    }

    public void setNotiPrefixApp(String info) {
        NotiPrefixApp = info;
        saveNotiPrefixApp();
    }

    private void saveNotiPrefixApp() {
        sharedPref.edit().putString(
                PREF_NOTI_PREFIX_APP,
                NotiPrefixApp
        ).apply();
    }

    private void loadNotiPrefixApp() {
        String info = sharedPref.getString(PREF_NOTI_PREFIX_APP, null);
        if (info != null) {
            NotiPrefixApp = info;
        }
        Log.d(TAG, "NotiPrefixApp = " + NotiPrefixApp);
    }

    //
    public String getNotiPrefixTitle() {
        return NotiPrefixTitle;
    }

    public void setNotiPrefixTitle(String info) {
        NotiPrefixTitle = info;
        saveNotiPrefixTitle();
    }

    private void saveNotiPrefixTitle() {
        sharedPref.edit().putString(
                PREF_NOTI_PREFIX_TITLE,
                NotiPrefixTitle
        ).apply();
    }

    private void loadNotiPrefixTitle() {
        String info = sharedPref.getString(PREF_NOTI_PREFIX_TITLE, null);
        if (info != null) {
            NotiPrefixTitle = info;
        }
        Log.d(TAG, "NotiPrefixTitle = " + NotiPrefixTitle);
    }

    //
    public String getNotiPrefixContent() {
        return NotiPrefixContent;
    }

    public void setNotiPrefixContent(String info) {
        NotiPrefixContent = info;
        saveNotiPrefixContent();
    }

    private void saveNotiPrefixContent() {
        sharedPref.edit().putString(
                PREF_NOTI_PREFIX_CONTENT,
                NotiPrefixContent
        ).apply();
    }

    private void loadNotiPrefixContent() {
        String info = sharedPref.getString(PREF_NOTI_PREFIX_CONTENT, null);
        if (info != null) {
            NotiPrefixContent = info;
        }
        Log.d(TAG, "NotiPrefixContent = " + NotiPrefixContent);
    }

    //
    public String getNotiPrefixTime() {
        return NotiPrefixTime;
    }

    public void setNotiPrefixTime(String info) {
        NotiPrefixTime = info;
        saveNotiPrefixTime();
    }

    private void saveNotiPrefixTime() {
        sharedPref.edit().putString(
                PREF_NOTI_PREFIX_TIME,
                NotiPrefixTime
        ).apply();
    }

    private void loadNotiPrefixTime() {
        String info = sharedPref.getString(PREF_NOTI_PREFIX_TIME, null);
        if (info != null) {
            NotiPrefixTime = info;
        }
        Log.d(TAG, "NotiPrefixTime = " + NotiPrefixTime);
    }

    //
    public String getNotiPrefixToken() {
        return NotiPrefixToken;
    }

    public void setNotiPrefixToken(String info) {
        NotiPrefixToken = info;
        saveNotiPrefixToken();
    }

    private void saveNotiPrefixToken() {
        sharedPref.edit().putString(
                PREF_NOTI_PREFIX_TOKEN,
                NotiPrefixToken
        ).apply();
    }

    private void loadNotiPrefixToken() {
        String info = sharedPref.getString(PREF_NOTI_PREFIX_TOKEN, null);
        if (info != null) {
            NotiPrefixToken = info;
        }
        Log.d(TAG, "NotiPrefixToken = " + NotiPrefixToken);
    }

    //
    public String getNotiToken() {
        return NotiToken;
    }

    public void setNotiToken(String info) {
        NotiToken = info;
        saveNotiToken();
    }

    private void saveNotiToken() {
        sharedPref.edit().putString(
                PREF_NOTI_TOKEN,
                NotiToken
        ).apply();
    }

    private void loadNotiToken() {
        String info = sharedPref.getString(PREF_NOTI_TOKEN, null);
        if (info != null) {
            NotiToken = info;
        }
        Log.d(TAG, "NotiToken = " + NotiToken);
    }

    // NOTIFICATION FORWARD //
    public void forwardNotification(final String packageName, long postTime, Notification notification) {
        final Date date = new Date(postTime);
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault());

        Log.i(TAG, notification.extras.toString());
        appendLog(notification.extras.toString());

        final String app = getApplicationName(packageName);

        CharSequence charSequence;

        charSequence = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        final String title = charSequence != null ? String.valueOf(charSequence) : "none";

        charSequence = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        final String content = charSequence != null ? String.valueOf(charSequence) : "none";

        final String message = NotiPrefixApp + "=" + app + "&" +
                NotiPrefixTitle + "=" + title + "&" +
                NotiPrefixContent + "=" + content + "&" +
                NotiPrefixTime + "=" + dateFormat.format(date) + "&" +
                NotiPrefixToken + "=" + NotiToken;



        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(appContext);

        // Request a string response from the provided UrlForRedirect.
        StringRequest stringRequest = new StringRequest(
                Request.Method.POST,
                NotiRedirectURL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.i(TAG, "Notification forwarded! " + response + " " + message);
                        responseWebHub("Notification forwarded! " + response + " " + message);
                        setTextLastForwardedData(message);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Cannot forward Notification content! " + error.toString() + " " + message);
                        responseWebHub("Cannot forward Notification content! " + error.toString() + " " + message);
                    }
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put(NotiPrefixApp, app);
                params.put(NotiPrefixTitle, title);
                params.put(NotiPrefixContent, content);
                params.put(NotiPrefixTime, dateFormat.format(date));
                params.put(NotiPrefixToken, NotiToken);
                return params;
            }
        };

        // Add the request to the RequestQueue.
        Log.d(TAG, "insert Notification to queue");
        queue.add(stringRequest);
    }

    // HELPER //
    private final MutableLiveData<String> textLog = new MutableLiveData<>();

    public LiveData<String> getTextLog() {
        return textLog;
    }

    private void setTextLog(String text) {
        textLog.postValue(text);
    }

    private static String log = "";

    public void clearLog() {
        log = "";
        setTextLog(log);
    }

    public String getLog() {
        return log;
    }

    private void appendLog(String s) {
        log += s + "\n\n";
        if (log.length() > 24000) {
            log = log.substring(log.length() - 24000);
        }
        setTextLog(log);
    }

    private String getApplicationName(String packageName) {
        PackageManager packageManager = appContext.getPackageManager();
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName, 0);
        } catch (Exception ignore) {
        }
        return (String) ((applicationInfo != null) ? packageManager.getApplicationLabel(applicationInfo) : "Unknown");
    }
}
