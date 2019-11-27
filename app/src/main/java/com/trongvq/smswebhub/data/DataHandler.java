package com.trongvq.smswebhub.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.telephony.SmsManager;
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
import com.trongvq.smswebhub.R;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
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

    private void loadData() {
        Log.d(TAG, "load saved settings");
        loadWebHubURL();
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

    // WEB HUB URL //
    private static final String PREF_WEB_HUB_URL = PREF + "webhub_url";
    private String webHubURL = "ws://192.168.137.1:8080";

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

    // WEB HUB SOCKET //
    private WebSocketClient wsClient = null;
    private final CountDownTimer wsRetryTimer = new CountDownTimer(30000, 1000) {
        @Override
        public void onTick(long l) {
            setTextWebHubStatus("Connecting in " + (int) (l / 1000) + "s ...");
        }

        @Override
        public void onFinish() {
            setTextWebHubStatus("Connecting ...");
            connectWebHub();
        }
    };

    public void connectWebHub() {
        String url = DataHandler.getInstance().getWebHubURL();

        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception | Error ex) {
            ex.printStackTrace();
            return;
        }

        if (wsClient != null) {
            if (wsClient.isOpen()) {
                Log.d(TAG, "already connected to " + url);
                return;
            }
            if (wsClient.isClosed()) {
                Log.d(TAG, "old connection to " + url + " is closed");
            }
            if (wsClient.isClosing()) {
                Log.d(TAG, "old connection to " + url + " is closing");
            }
        }

        Log.d(TAG, "try to connect to: " + url);
        wsClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                Log.d(TAG, handshakedata.toString());

                DataHandler.getInstance().setTextWebHubStatus("Connected!");
            }

            @Override
            public void onMessage(String message) {
                if (!isActivated()) {
                    setTextWebHubLastCommand(appContext.getString(R.string.not_activated));
                    return;
                }
                Log.d(TAG, "message = " + message);
                setTextWebHubLastCommand(message);

                if (message.startsWith("/ussd")) {
                    // parse the params
                    Uri uri = Uri.parse("http://example.com" + message);

                    String from = uri.getQueryParameter("from");
                    String cmd = uri.getQueryParameter("cmd");

                    Log.d(TAG, "from = " + from);
                    Log.d(TAG, "cmd = " + cmd);

                    try {
                        requestUSSD(from, "*" + cmd + "#");
                    } catch (Exception | Error ex) {
                        ex.printStackTrace();
                    }

                } else {
                    // parse the params
                    Uri uri = Uri.parse("http://example.com" + message);

                    String from = uri.getQueryParameter("from");
                    String number = uri.getQueryParameter("number");
                    String content = uri.getQueryParameter("content");

                    Log.d(TAG, "from = " + from);
                    Log.d(TAG, "number = " + number);
                    Log.d(TAG, "content = " + content);

                    try {
                        sendSMS(from, number, content);
                    } catch (Exception | Error ex) {
                        ex.printStackTrace();
                    }
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.d(TAG, "code = " + code + " reason = " + reason + " remote = " + remote);

                try {
                    wsClient.close();
                } catch (Exception | Error ex) {
                    ex.printStackTrace();
                }
                wsClient = null;
                wsRetryTimer.cancel(); // must cancel the old one
                wsRetryTimer.start();
                DataHandler.getInstance().setTextWebHubStatus("Connection lost! Retry soon!");
            }

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();

                try {
                    wsClient.close();
                } catch (Exception | Error ex2) {
                    ex2.printStackTrace();
                }
                wsClient = null;
                wsRetryTimer.cancel(); // must cancel the old one
                wsRetryTimer.start();
                DataHandler.getInstance().setTextWebHubStatus("Connection lost! Retry soon!");
            }
        };

        wsClient.connect();
    }

    public void disconnectWebHub() {
        // close web hub
        if (wsClient != null) {
            if (wsClient.isOpen()) {
                try {
                    wsClient.close();
                } catch (Exception | Error ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    // WEB HUB STATUS //
    private final MutableLiveData<String> textWebHubStatus = new MutableLiveData<>();

    public LiveData<String> getTextWebHubStatus() {
        return textWebHubStatus;
    }

    public void setTextWebHubStatus(String text) {
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
    private String SmsRedirectURL = "http://192.168.137.1:8000";
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
            if (from != null && from.equals("sim2")) {
                Log.d(TAG, "not implemented");
            } else { /* by default, mobile uses SIM 1 */
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(number, null, content, null, null);
            }
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
            setTextLastForwardedData(appContext.getString(R.string.not_activated));
            return;
        }

        final Date date = Calendar.getInstance().getTime();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault());

        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(appContext);

        // Request a string response from the provided UrlForRedirect.
        StringRequest stringRequest = new StringRequest(
                Request.Method.POST,
                SmsRedirectURL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.i(TAG, "Message forwarded! " + response);
                        setTextLastForwardedData(
                                PrefixSender + "=" + sender + "&" +
                                        PrefixContent + "=" + content + "&" +
                                        PrefixTime + "=" + dateFormat.format(date) + "&" +
                                        PrefixToken + "=" + SmsToken
                        );
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Cannot forward SMS content! " + error.toString());
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
                        handleUssdRequest.invoke(iTelephony, SubscriptionManager.getDefaultSubscriptionId(), ussdCode, resultReceiver);
                    }
                }
            }
        } catch (Exception | Error e) {
            e.printStackTrace();
        }
    }

    // ACTIVATION //

    private static final String PREF_INSTALL_DATE = PREF + "install_date";

    public boolean isActivated() {
        String info = sharedPref.getString(PREF_INSTALL_DATE, null);
        if (info == null) {
            Log.i(TAG, "NO INSTALLATION DATE FOUND!");
            setInstallDate();
            return true;
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US);
            try {
                Date installDate = sdf.parse(info);
                if (installDate != null) {
                    Log.i(TAG, "INSTALLATION DATE: " + installDate.toString());
                    return daysBetween(installDate, Calendar.getInstance().getTime()) <= 1;
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
        sharedPref.edit().putString(
                PREF_INSTALL_DATE,
                sdf.format(Calendar.getInstance().getTime())
        ).apply();
    }

    private Calendar getDatePart(Date date) {
        Calendar cal = Calendar.getInstance();       // get calendar instance
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);            // set hour to midnight
        cal.set(Calendar.MINUTE, 0);                 // set minute in hour
        cal.set(Calendar.SECOND, 0);                 // set second in minute
        cal.set(Calendar.MILLISECOND, 0);            // set millisecond in second

        return cal;                                  // return the date part
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

        Log.i(TAG, "daysBetween = " + daysBetween);
        return daysBetween;
    }
}
