package com.nordicid.rfiddemo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Locale;

import com.nordicid.apptemplate.AppTemplate;
import com.nordicid.apptemplate.SubAppList;
import com.nordicid.nidulib.NiduLib;
import com.nordicid.nurapi.BleScanner;
import com.nordicid.nurapi.NurApiAndroid;
import com.nordicid.nurapi.*;

import android.Manifest;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import nordicid.com.nurupdate.NurDeviceUpdate;


public class Main extends AppTemplate {

    // Check whether this string is found in the given filename.
    private final String NUR_AUTH_IDENT_STR = "nur_auth_keyset";

    final private String TAG = "MAIN";
    // Authentication app requirements.
    public static final int AUTH_REQUIRED_VERSION = 0x050500 + ('A' & 0xFF);
    public static final String AUTH_REQUIRED_VERSTRING = "5.5-A";
    private static SharedPreferences mApplicationPrefences = null;

    private NfcApp mNfcApp;
    /**
     * Requesting file for key reading.
     */
    public static final int REQ_FILE_OPEN = 4242;

    final Handler timerHandler = new Handler();
    boolean mEnableTimerPing = false;

    private NurApiAutoConnectTransport mAcTr;
    private static boolean mIsApplicationMode = true;
    private static boolean mBarcodeOnly;
    private static boolean mIsSensors;
    private static  boolean mIsImager;

    public static final String KEYFILE_PREFNAME = "TAM1_KEYFILE";
    public static final String KEYNUMBER_PREFNAME = "TAM1_KEYNUMBER";

    private static Main gInstance;

    private boolean mShowSwipeHint = false;

    private boolean mDoNotDisconnectOnStop = false;

    private static final int REQUEST_BLE_CODE = 2;

    public NurApiAutoConnectTransport getAutoConnectTransport()
    {
        return mAcTr;
    }


    public void disposeTrasport() {
        if (mAcTr != null) {
            Log.d(TAG,"Dispose transport");
            mAcTr.dispose();
        }
        mAcTr = null;
    }

    public void toggleScreenRotation(boolean enable) {
        setRequestedOrientation((enable) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR : ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
    }

    public static SharedPreferences getApplicationPrefences() { return mApplicationPrefences; }

    public NurApiAutoConnectTransport getNurAutoConnect() { return mAcTr;}

    public void setDoNotDisconnectOnStop(boolean val)
    {
        mDoNotDisconnectOnStop = val;
    }

    public boolean getDoNotDisconnectOnStop()
    {
        return mDoNotDisconnectOnStop;
    }

    public static Main getInstance() {
        return gInstance;
    }
    public static boolean isImager() {return mIsImager;}
    public static boolean isBarcodeOnly() {return mBarcodeOnly;}
    public static boolean isSensors() {return mIsSensors;}
    public static boolean isApplicationMode() {return mIsApplicationMode;}

    public void handleKeyFile() {
        Intent intent;
        Intent chooser;

        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        chooser = Intent.createChooser(intent, "Select file");

        try {
            startActivityForResult(chooser, REQ_FILE_OPEN);
        } catch (Exception ex) {
            String strErr = ex.getMessage();
            Toast.makeText(this, "Error:\n" + strErr, Toast.LENGTH_SHORT).show();
        }
    }

    void saveSettings(NurDeviceSpec connSpec) {
        SharedPreferences pref = getSharedPreferences("DemoApp", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        if (mAcTr == null) {
            editor.putString("specStr", "");
        } else {
            editor.putString("specStr", connSpec.getSpec());
        }
        editor.apply();

        updateStatus();
    }

    void saveHintStatus() {
        SharedPreferences pref = getSharedPreferences("DemoApp", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        editor.putBoolean("SwipeHint", mShowSwipeHint);
        editor.apply();
        updateStatus();
    }

    void loadHintStatus()
    {
        mShowSwipeHint = mApplicationPrefences.getBoolean("SwipeHint", true);
    }

    public void saveKeyFilename(String fileName) {
        SharedPreferences.Editor editor = mApplicationPrefences.edit();
        editor.putString(KEYFILE_PREFNAME, fileName);
        editor.apply();
    }

    public String getKeyFileName() {
        return mApplicationPrefences.getString(KEYFILE_PREFNAME, "");
    }

    public void saveUsedKeyNumber(int keyNumber) {
        SharedPreferences.Editor editor = mApplicationPrefences.edit();

        editor.putInt(KEYNUMBER_PREFNAME, keyNumber);

        editor.apply();
    }

    public int getUsedKeyNumber() {
        return mApplicationPrefences.getInt(KEYNUMBER_PREFNAME, -1);
    }

    //public boolean checkUpdatesEnabled() { return mApplicationPrefences.getBoolean("CheckUpdate",false); }

    public void loadSettings() {
        String type = mApplicationPrefences.getString("connType", "");

        /* Get rotation setting enable / disable rotation sensors */
        toggleScreenRotation(mApplicationPrefences.getBoolean("Rotation", false));
        Beeper.setEnabled(mApplicationPrefences.getBoolean("Sounds", true));

        String specStr = mApplicationPrefences.getString("specStr", "");
        if (specStr.length() == 0) {
            String manufacturer = Build.MANUFACTURER.toLowerCase(Locale.ENGLISH);
            if (manufacturer.contains("nordicid") || manufacturer.contains("nordic id")) {
                // Defaults to integrated reader
                specStr = "type=INT;addr=integrated_reader";
            }
        }

        if (specStr.length() > 0)
        {
            NurDeviceSpec spec = new NurDeviceSpec(specStr);

            if (mAcTr != null) {
                System.out.println("Dispose transport");
                mAcTr.dispose();
            }

            try {
                String strAddress;
                mAcTr = NurDeviceSpec.createAutoConnectTransport(this, getNurApi(), spec);
                strAddress = spec.getAddress();

                mAcTr.setAddress(strAddress);
            } catch (NurApiException e) {
                e.printStackTrace();
            }
        }

        NurSmartPairSupport.setSettingsString(mApplicationPrefences.getString("SmartPairSettings", "{}"));

        updateStatus();
    }

    String mConnectedName = "";

    void updateStatus()
    {
        //Log.w(TAG, "updateStatus() " + mConnectedName + "; " + mApi.isConnected() + " :" + mAcTr.getDetails());

        int nextUpdate = 2000;

        if (mAcTr != null)
        {
            if (mEnableTimerPing && mApi.isConnected())
            {
                try {
                    Log.w(TAG, "updateStatus() Keep device alive");
                    mApi.ping();
                } catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }

            if (mApi.isConnected() && mConnectedName.length() == 0)
            {
                //Need to get fresh info from device is accessories supported
                AppTemplate.getAppTemplate().mAccessorySupported = AppTemplate.getAppTemplate().getAccessoryApi().isSupported();

                if (AppTemplate.getAppTemplate().getAccessorySupported())
                {
                    // Attempt to get name from accessory device config
                    try {
                        mConnectedName = AppTemplate.getAppTemplate().getAccessoryApi().getConfig().name;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // Attempt to get name from connection spec string
                    if (mConnectedName.length() == 0) {
                        String specStr = mApplicationPrefences.getString("specStr", "");
                        NurDeviceSpec spec = new NurDeviceSpec(specStr);
                        if (spec.hasPart("name")) {
                            mConnectedName = spec.getPart("name", "N/A");
                        }
                    }
                }

                NurRespReaderInfo readerInfo = null;
                try {
                    readerInfo = mApi.getReaderInfo();

                    // If no name set yet, get it from NUR reader info
                    if (mConnectedName.length() == 0) {
                        mConnectedName = readerInfo.name;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Add serial to connection status string
                if (readerInfo != null) {
                    String serial = "";
                    if (readerInfo.altSerial.length() > 0 && !readerInfo.altSerial.startsWith("0"))
                        serial += readerInfo.altSerial;
                    else if (readerInfo.serial.length() > 0)
                        serial += readerInfo.serial;

                    if (serial.length() > 0 && !mConnectedName.contains(serial))
                    {
                        mConnectedName += " (" + serial + ")";
                    }
                }

                mConnectedName = "Connected to " + mConnectedName;
            }
            else if (!mApi.isConnected()) {
                // Not connected, check updates in 500ms
                mConnectedName = "";
                nextUpdate = 500;
            }

            if (mConnectedName.length() == 0) {
                setStatusText(mAcTr.getDetails());
            } else {
                setStatusText(mConnectedName);
            }
        } else {
            mConnectedName = "";
            setStatusText("No connection defined");
        }

        timerHandler.removeCallbacks(mUpdateStatusRunnable);

        if (!Main.getAppTemplate().isApplicationPaused()) {
            try {
               timerHandler.postDelayed(mUpdateStatusRunnable, nextUpdate);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    Runnable mUpdateStatusRunnable = new Runnable() {
        @Override
        public void run() {
            updateStatus();
        }
    };

    static boolean mShowingSmartPair = false;

    boolean showSmartPairUI()
    {
        if (mApi.isConnected())
            return false;

        SharedPreferences pref = Main.getApplicationPrefences();
        if (!pref.getBoolean("SmartPairShowUi", true))
            return false;

        try {
            Log.d(TAG, "showSmartPairUI()");
            Intent startIntent = new Intent(this, Class.forName ("com.nordicid.smartpair.SmartPairConnActivity"));
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startIntent);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause() mDoNotDisconnectOnStop " + mDoNotDisconnectOnStop + "; mShowingSmartPair " + mShowingSmartPair);
        super.onPause();

        timerHandler.removeCallbacks(mUpdateStatusRunnable);

        if (mAcTr != null && !mDoNotDisconnectOnStop) {
            mAcTr.onPause();
        }

        if (mShowingSmartPair) {
            // Keep main ui refreshing
            mApplicationPaused = false;
            updateStatus();
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume() mDoNotDisconnectOnStop " + mDoNotDisconnectOnStop + "; mShowingSmartPair " + mShowingSmartPair);
        super.onResume();
        Beeper.init();

        if (mAcTr == null)
            loadSettings();

        if (mAcTr != null) {
            mAcTr.onResume();
        }

        // Reset flag
        mDoNotDisconnectOnStop = false;

        if (!mShowingSmartPair && mAcTr != null) {
            String clsName = mAcTr.getClass().getSimpleName();
            if (clsName.equals("NurApiSmartPairAutoConnect")) {
                mShowingSmartPair = showSmartPairUI();
            }
        } else {
            mShowingSmartPair = false;
        }

        updateStatus();
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        updateStatus();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop() mDoNotDisconnectOnStop " + mDoNotDisconnectOnStop + "; mShowingSmartPair " + mShowingSmartPair);
        super.onStop();

        mShowingSmartPair = false;
        timerHandler.removeCallbacksAndMessages(null);

        if (mAcTr != null && !mDoNotDisconnectOnStop) {
            mAcTr.onStop();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();

        if (mAcTr != null) {
            mAcTr.onDestroy();
        }
    }


    // Visible app choices / not.
    public void syncViewContents() {
        super.onResume();
    }

    void beginHint()
    {
        (new Handler(getMainLooper())).postDelayed(new Runnable() {
            @Override
            public void run() {
                handleEula(false);
            }
        }, 500);
    }

    void doHint()
    {
        if (!mShowSwipeHint)
            return;

        final Dialog dlg = new Dialog(this);
        final Button okBtn;
        final Button gotItBtn;

        dlg.setTitle(R.string.hint_title);
        dlg.setContentView(R.layout.layout_swipe_note);

        okBtn = (Button) dlg.findViewById(R.id.btn_hint_ok);
        okBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        gotItBtn = (Button) dlg.findViewById(R.id.btn_hint_dont_show);
        gotItBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);

        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.getId() == R.id.btn_hint_dont_show) {
                    mShowSwipeHint = false;
                    saveHintStatus();
                }
                dlg.dismiss();
            }
        };

        okBtn.setOnClickListener(onClickListener);
        gotItBtn.setOnClickListener(onClickListener);

        dlg.show();
    }

    @Override
    public void onNfcRead(String hex, String dec, String tec) {
        mNfcApp.addNFCItem(hex,dec,tec);
    }

    @Override
    public void onCreateSubApps(final SubAppList subAppList) {
        gInstance = this;
        BleScanner.init(this);

        /* Set update sources */
        mApplicationPrefences = getSharedPreferences("DemoApp", Context.MODE_PRIVATE);
        loadHintStatus();

        copyAuthenticationFilesToDevice();

        if (AppTemplate.LARGE_SCREEN) {
            subAppList.addSubApp(new InventoryApp());
        } else {
            subAppList.addSubApp(new InventoryAppTabbed());
        }
		
		/* Tag trace application. */
        subAppList.addSubApp(new TraceApp());
		
		/* Tag write application. */
        subAppList.addSubApp(new WriteApp());

        NfcManager manager = (NfcManager) getApplicationContext().getSystemService(Context.NFC_SERVICE);
        NfcAdapter adapter = manager.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            //Yes NFC available
            mNfcApp = new NfcApp();
            subAppList.addSubApp(mNfcApp);
        }


		/* Barcode application. */
        subAppList.addSubApp(new BarcodeApp());

		/* Authentication application. */
        subAppList.addSubApp(new AuthenticationAppTabbed());
        getSubAppList().getApp("Authentication").setIsVisibleInMenu(false);

		/* Test mode application. */
        subAppList.addSubApp(new TestModeApp());

        /* Reader settings application. */
        subAppList.addSubApp(new SettingsAppTabbed());

        setAppListener(new NurApiListener() {
            @Override
            public void disconnectedEvent() {
                timerHandler.removeCallbacks(mSmartPairDisconRunnable);
                mSmartPairDisconButtons[0] = mSmartPairDisconButtons[1] = 0;

                Log.d(TAG, "disconnectedEvent() " + mConnectedName + "; " + mApi.isConnected());

                if (exitingApplication())
                    return;

                updateStatus();
                /*mConnectedName = "";
                if (mAcTr != null)
                    setStatusText(mAcTr.getDetails());
                else
                    setStatusText("No connection defined");*/

                if(mAcTr.getAddress().equals("integrated_reader")==false) {

                    if(mUpdatePending == false)
                        Toast.makeText(Main.this, getString(R.string.reader_disconnected), Toast.LENGTH_SHORT).show();
                }

                getSubAppList().getApp("Barcode").setIsVisibleInMenu(false);
                getSubAppList().getApp("Authentication").setIsVisibleInMenu(false);

                if (!isApplicationPaused()) {
                    // If current app not available anymore, return to main menu
                    if (getSubAppList().getCurrentOpenSubApp() == null)
                        setApp(null);

                    // Show smart pair ui
                    if (!mShowingSmartPair && mAcTr != null) {
                        String clsName = mAcTr.getClass().getSimpleName();
                        if (clsName.equals("NurApiSmartPairAutoConnect")) {
                            mShowingSmartPair = showSmartPairUI();
                        }
                    } else {
                        mShowingSmartPair = false;
                    }
                }
            }

            @Override
            public void connectedEvent() {

                mIsSensors=false;
                mBarcodeOnly = false;
                mIsImager = false;

                timerHandler.removeCallbacks(mSmartPairDisconRunnable);
                mSmartPairDisconButtons[0] = mSmartPairDisconButtons[1] = 0;

                Log.d(TAG, "connectedEvent() " + mConnectedName + "; " + mApi.isConnected());

                try {
                    updateStatus();
                    mIsApplicationMode = mApi.getMode().equalsIgnoreCase("A");

                    //if(checkUpdatesEnabled())
                    //   checkDeviceUpdates();

                    if (!mIsApplicationMode)
                        Toast.makeText(Main.this, getString(R.string.device_boot_mode), Toast.LENGTH_LONG).show();
                    else {
                        //Do not show this message when Integrated reader.

                        if(mAcTr.getAddress().equals("integrated_reader")==false)
                            Toast.makeText(Main.this, getString(R.string.reader_connected), Toast.LENGTH_SHORT).show();
                    }

                    // Authentication always hidden for now
                    getSubAppList().getApp("Authentication").setIsVisibleInMenu(/*fwVer >= AUTH_REQUIRED_VERSION*/ false);

                    // Show barcode app only for accessory devices w/ barcode reader

                    if (getAccessorySupported() && getAccessoryApi().getConfig().hasImagerScanner()) {
                        getSubAppList().getApp("Barcode").setIsVisibleInMenu(true);
                        mIsImager = true;
                    } else {
                        getSubAppList().getApp("Barcode").setIsVisibleInMenu(false);
                        mIsImager = false;
                    }


                    NurRespDevCaps caps = getNurApi().getDeviceCaps();
                    Log.w(TAG, "moduleType=" + caps.getModuleType());
                    Log.w(TAG, "hasInventoryRead=" + caps.hasInventoryRead());
                    Log.w(TAG, "hasLf256k=" + caps.hasLf256k());

                    if(caps.hasLf256k()==false && caps.hasInventoryRead()==false) {
                        //No RFID stuff
                        mBarcodeOnly = true;
                    }

                    getSubAppList().getApp("Write").setIsVisibleInMenu(mBarcodeOnly ? false:true);
                    getSubAppList().getApp("Inventory").setIsVisibleInMenu(mBarcodeOnly ? false:true);
                    getSubAppList().getApp("Locate").setIsVisibleInMenu(mBarcodeOnly ? false:true);


                    if(getAccessorySupported()) {
                        ArrayList<AccSensorConfig> sensorList = getAccessoryApi().accSensorEnumerate();
                        if(sensorList.size() > 0) mIsSensors=true;
                    }


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void triggeredReadEvent(NurEventTriggeredRead event) {
            }

            @Override
            public void traceTagEvent(NurEventTraceTag event) {
            }

            @Override
            public void programmingProgressEvent(NurEventProgrammingProgress eventProgramming) {
            }

            @Override
            public void nxpEasAlarmEvent(NurEventNxpAlarm event) {
            }

            @Override
            public void logEvent(int level, String txt) {
            }

            @Override
            public void inventoryStreamEvent(NurEventInventory event) {
            }

            @Override
            public void inventoryExtendedStreamEvent(NurEventInventory event) {
            }

            @Override
            public void frequencyHopEvent(NurEventFrequencyHop event) {
            }

            @Override
            public void epcEnumEvent(NurEventEpcEnum event) {
            }

            @Override
            public void deviceSearchEvent(NurEventDeviceInfo event) {
            }

            @Override
            public void debugMessageEvent(String event) {
            }

            @Override
            public void clientDisconnectedEvent(NurEventClientInfo event) {
            }

            @Override
            public void clientConnectedEvent(NurEventClientInfo event) {
            }

            @Override
            public void bootEvent(String event) {

            }

            @Override
            public void autotuneEvent(NurEventAutotune event) {
            }

            @Override
            public void IOChangeEvent(NurEventIOChange event)
            {
                // Holding "Power" and "Pair" buttons for 1.5secs disconnects device (only Smart Pair connection)
                String clsName = mAcTr.getClass().getSimpleName();
                if (clsName.equals("NurApiSmartPairAutoConnect"))
                {
                    if (event.source >= 101 && event.source <= 102) {
                        mSmartPairDisconButtons[(event.source - 101)] = event.direction;

                        if (mSmartPairDisconButtons[0] + mSmartPairDisconButtons[1] == 2) {
                            // Both buttons down
                            if (!mSmartPairDisconScheduled) {
                                mSmartPairDisconScheduled = true;
                                Log.d(TAG, "Smart Pair disconnect sheduled");
                                // Start led blinkin on EXA side to notify user about disconnect in 1.5sec
                                try {
                                    getAccessoryApi().setLedOpMode(3);
                                }catch (Exception e) { }
                                // Schedule disconnect
                                timerHandler.postDelayed(mSmartPairDisconRunnable, 1500);
                            }
                        } else {
                            // Buttons up (one or both)
                            if (mSmartPairDisconScheduled) {
                                mSmartPairDisconScheduled = false;
                                Log.d(TAG, "Smart Pair disconnect cancelled");
                                timerHandler.removeCallbacks(mSmartPairDisconRunnable);
                                // Stop led blinking
                                try {
                                    getAccessoryApi().setLedOpMode(0);
                                }catch (Exception e) { }
                            }
                        }
                    }
                }
            }

            @Override
            public void tagTrackingScanEvent(NurEventTagTrackingData event) {
            }

            @Override
            public void tagTrackingChangeEvent(NurEventTagTrackingChange event) {
            }
        });

        (findViewById(R.id.app_statustext)).setOnClickListener(mStatusBarOnClick);

        beginHint();
    }

    boolean mSmartPairDisconScheduled = false;

    Runnable mSmartPairDisconRunnable = new Runnable() {
        @Override
        public void run() {

            if (!getNurApi().isConnected())
                return;

            try {
                Log.d(TAG, "User Disconnect Smart Pair");

                // Beep on host side to notify user about disconnect
                Beeper.beep(Beeper.BEEP_100MS);

                // Stop led blink
                try {
                    getAccessoryApi().setLedOpMode(0);
                }catch (Exception e) { }

                final String addr = mAcTr.getAddress();

                // Disconnect
                mAcTr.setAddress("");

                // reconnect after 2sec
                timerHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Reconnect
                        mAcTr.setAddress(addr);
                    }
                }, 2000);

            } catch (Exception e)
            {
                e.printStackTrace();
            }

            // Make sure no discon scheduled
            timerHandler.removeCallbacks(mSmartPairDisconRunnable);
        }
    };

    int []mSmartPairDisconButtons = new int[] { 0, 0 };

    int testmodeClickCount = 0;
    long testmodeClickTime = 0;

    View.OnClickListener mStatusBarOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (testmodeClickCount < 10) {
                if (testmodeClickTime != 0 && System.currentTimeMillis() - testmodeClickTime > 5000) {
                    testmodeClickCount = 0;
                }
                testmodeClickTime = System.currentTimeMillis();
                testmodeClickCount++;

                if (testmodeClickCount == 10) {
                    Toast.makeText(Main.this, "Test Mode enabled", Toast.LENGTH_SHORT).show();
                    getSubAppList().getApp("Test Mode").setIsVisibleInMenu(true);
                }
            }
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ((TextView) findViewById(R.id.app_statustext)).setOnClickListener(mStatusBarOnClick);
        updateStatus();
    }

    @Override
    public void onCreateDrawerItems(Drawer drawer) {
        drawer.addTitle(getString(R.string.drawer_connection));
        drawer.addTitle(getString(R.string.drawer_updates));
        drawer.addTitle(getString(R.string.drawer_contact));
        drawer.addTitle(getString(R.string.drawer_guide));
        drawer.addTitle("Eula");
        drawer.addTitle(getString(R.string.drawer_about));
    }

    void handleQuickGuide() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        // looks ugly
        //alert.setTitle("Quick guide");
        WebView wv = new WebView(this);
        wv.loadUrl("file:///android_res/raw/guide.html");
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        alert.setView(wv);
        alert.setNegativeButton("Close", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        alert.show();
    }

    void handleEula(boolean forceShow) {

        boolean eulaAccepted = mApplicationPrefences.getBoolean("EulaAccepted", false);
        if (eulaAccepted && !forceShow) {
            doHint();
            return;
        }

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        WebView wv = new WebView(this);
        wv.loadUrl("file:///android_res/raw/eula.html");
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        alert.setView(wv);
        alert.setPositiveButton("Accept", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                SharedPreferences pref = getSharedPreferences("DemoApp", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = pref.edit();
                editor.putBoolean("EulaAccepted", true);
                editor.apply();

                dialog.dismiss();
                doHint();
            }
        });
        alert.setNegativeButton("Decline", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                SharedPreferences pref = getSharedPreferences("DemoApp", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = pref.edit();
                editor.putBoolean("EulaAccepted", false);
                editor.apply();

                dialog.dismiss();
                Main.this.finishApp();
            }
        });
        alert.show();
    }

    public String getModuleType(){
        try {
            if (mApi.isConnected()) {
                return mApi.getFwInfo().get("MODULE");
            }
        } catch (Exception e){
            Log.e(TAG,e.toString());
        }
        return null;
    }

    public String getNurAppVersion(){
        try{
            if(mApi.isConnected())
                return ((mIsApplicationMode) ? mApi.getVersions().primaryVersion : mApi.getVersions().secondaryVersion);
        } catch (Exception e){
            Log.e(TAG,e.toString());
        }
        return null;
    }

    public String getNurBldrVersion(){
        try{
            if(mApi.isConnected())
                return ((mIsApplicationMode) ? getNurApi().getVersions().secondaryVersion : mApi.getVersions().primaryVersion);
        } catch (Exception e){
            //
        }
        return null;
    }

    public void checkDeviceUpdates() {

        if(!mApi.isConnected()) {
            Toast.makeText(this,"No device connected",Toast.LENGTH_SHORT).show();
            return;
        }

        if(isBarcodeOnly() && isApplicationMode()==true) {

            final AlertDialog.Builder alertDialog = new AlertDialog.Builder(gInstance);
            alertDialog.setTitle("Check available updates from:");
            alertDialog.setMessage("Setting / System / Advanced / Additional system updates");

            alertDialog.setNegativeButton("Cancel",null);
            alertDialog.setPositiveButton("Go to 'Settings'..", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    startActivityForResult(new Intent(android.provider.Settings.ACTION_SETTINGS), 0);
                }
            });

            alertDialog.show();
            return;
        }

        SettingsAppTabbed.setPreferredTab(getString(R.string.firmware_updates));
        setApp("Settings");
    }

    public AccVersionInfo getAccesoryVersionInfo()
    {
        try{
            if (getAccessorySupported() && mApi.isConnected())
                return getAccessoryApi().getFwVersion();
        } catch (Exception e){
            //
        }
        return null;
    }

    // Parse the URI the get the actual file name.
    private String getActualFileName(String strUri) {
        String strFileName = null;
        Uri uri;
        String scheme;

        uri = Uri.parse(strUri);
        scheme = uri.getScheme();

        if (scheme.equalsIgnoreCase("content")) {
            String primStr;
            primStr = uri.getLastPathSegment().replace("primary:", "");
            strFileName = Environment.getExternalStorageDirectory() + "/" + primStr;
        }

        return strFileName;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

			case REQ_FILE_OPEN:
			{
				if (data != null) {
					String fullPath;

					fullPath = getActualFileName(data.getDataString());

					if (fullPath == null)
						Toast.makeText(Main.this, "No file selected.", Toast.LENGTH_SHORT).show();
					else {
						saveKeyFilename(fullPath);

						SettingsAppAuthTab authTab = SettingsAppAuthTab.getInstance();

						if (authTab != null)
							authTab.updateViews();
					}
				}
			}
			break;

			case NurDeviceListActivity.REQUEST_SELECT_DEVICE: {
				if (data == null || resultCode != NurDeviceListActivity.RESULT_OK)
					return;

				try {
					NurDeviceSpec spec = new NurDeviceSpec(data.getStringExtra(NurDeviceListActivity.SPECSTR));

					if (mAcTr != null) {
						System.out.println("Dispose transport");
						mAcTr.dispose();
					}

					String strAddress;
                    mAcTr = NurDeviceSpec.createAutoConnectTransport(this, getNurApi(), spec);
                    strAddress = spec.getAddress();
                    mAcTr.setAddress(strAddress);
					saveSettings(spec);
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
			break;
		}
        super.onActivityResult(requestCode,resultCode,data);
    }

	void handleConnectionClick()
	{
        boolean isAllowed = false;
        //BLE permissions for v11 and lower
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            if ((ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) &&
                    ((ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED))) {
                isAllowed = true;
            }
        } else { //BLE permissions for v12 and higher
            if ((ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) &&
                    ((ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED)) &&
                    ((ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED))) {
                isAllowed = true;
            }
        }

        if (isAllowed) {
            NurDeviceListActivity.startDeviceRequest(this, mApi);
        } else {
            Toast.makeText(this, "Bluetooth permission denied.\nPlease make sure Bluetooth is enabled and permission is allowed.", Toast.LENGTH_LONG).show();
        }
	}

    void handleContactClick() {
        final View dialogLayout = getLayoutInflater().inflate(R.layout.dialog_contact, null);
        AlertDialog.Builder builder = new Builder(this);
        builder.setView(dialogLayout);
        builder.show();
    }

    void handleAboutClick() {
        final View dialogLayout = getLayoutInflater().inflate(R.layout.dialog_about, null);
        AlertDialog.Builder builder = new Builder(this);
        builder.setView(dialogLayout);

        final TextView readerAttachedTextView = (TextView) dialogLayout.findViewById(R.id.reader_attached_is);

        String appversion = "0.0";
        try {
            appversion = this.getPackageManager().getPackageInfo("com.nordicid.rfiddemo", 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        final TextView appVersion = (TextView) dialogLayout.findViewById(R.id.app_version);
        appVersion.setText(getString(R.string.about_dialog_app) + " " + appversion);

        final TextView nurApiVersion = (TextView) dialogLayout.findViewById(R.id.nur_api_version);
        //nurApiVersion.setText(getString(R.string.about_dialog_nurapi) + " " + getNurApi().getFileVersion());

        String verStr = getString(R.string.about_dialog_nurapi) + " " + getNurApi().getFileVersion() + "; NurAndroidApi " + NurApiAndroid.getVersion();
        if (NurSmartPairSupport.isSupported()) {
            verStr += "; SmartPair " + NurSmartPairSupport.getVersion();
        }
        verStr +="; NurUpdateLib " + NurDeviceUpdate.getVersion();
        verStr +="; NiduLib " + NiduLib.version();
        nurApiVersion.setText(verStr);

        if (getNurApi().isConnected()) {

            readerAttachedTextView.setText(getString(R.string.attached_reader_info));

            try {
                NurRespReaderInfo readerInfo = getNurApi().getReaderInfo();
                NurRespDevCaps devCaps = getNurApi().getDeviceCaps();

                final TextView modelTextView = (TextView) dialogLayout.findViewById(R.id.reader_info_model);
                modelTextView.setText(getString(R.string.about_dialog_model) + " " + getModuleType());
                modelTextView.setVisibility(View.VISIBLE);

                if(isBarcodeOnly()==false) {
                    final TextView serialTextView = (TextView) dialogLayout.findViewById(R.id.reader_info_serial);
                    serialTextView.setText(getString(R.string.about_dialog_serial) + " " + readerInfo.serial);
                    serialTextView.setVisibility(View.VISIBLE);

                    final TextView deviceSerialTextView = (TextView) dialogLayout.findViewById(R.id.device_serial);
                    deviceSerialTextView.setText(getString(R.string.about_dialog_device_serial) + " " + readerInfo.altSerial);
                    deviceSerialTextView.setVisibility(View.VISIBLE);

                    final TextView firmwareTextView = (TextView) dialogLayout.findViewById(R.id.reader_info_firmware);
                    firmwareTextView.setText("Firmware: " + getNurAppVersion());
                    firmwareTextView.setVisibility(View.VISIBLE);

                    final TextView bootloaderTextView = (TextView) dialogLayout.findViewById(R.id.reader_bootloader_version);
                    bootloaderTextView.setText(getString(R.string.about_dialog_bootloader) + " " + getNurBldrVersion());
                    bootloaderTextView.setVisibility(View.VISIBLE);

                    final TextView secChipTextView = (TextView) dialogLayout.findViewById(R.id.reader_sec_chip_version);
                    if (devCaps.isOneWattReader())
                        secChipTextView.setText(getString(R.string.about_dialog_sec_chip) + " " + devCaps.secChipMajorVersion + "." + devCaps.secChipMinorVersion + "." + devCaps.secChipMaintenanceVersion + "." + devCaps.secChipReleaseVersion);
                    else secChipTextView.setText("-------------");
                    secChipTextView.setVisibility(View.VISIBLE);
                }

                if (getAccessorySupported()) {

                    final AccVersionInfo accessoryVersion = getAccesoryVersionInfo();

                    final TextView accessoryTextView = (TextView) dialogLayout.findViewById(R.id.accessory_version);
                    accessoryTextView.setText(getString(R.string.about_dialog_accessory) + " " + accessoryVersion.getFullApplicationVersion());
                    accessoryTextView.setVisibility(View.VISIBLE);

                    final  TextView accessoryBldrVersion = (TextView) dialogLayout.findViewById(R.id.accessory_bootloader_version);
                    accessoryBldrVersion.setText(getString(R.string.about_dialog_accessory_bldr) + " " + accessoryVersion.getBootloaderVersion());
                    accessoryBldrVersion.setVisibility(View.VISIBLE);

                    try {
                        final TextView connDetails = (TextView) dialogLayout.findViewById(R.id.conn_info);
                        connDetails.setText(getString(R.string.about_dialog_conn_info) + " " + getAccessoryApi().getConnectionInfo());
                        connDetails.setVisibility(View.VISIBLE);
                    } catch (Exception ex)
                    {
                        // Ignore exception, connection info is not available on old FW
                        ex.printStackTrace();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            readerAttachedTextView.setText(getString(R.string.no_reader_attached));
        }

        builder.show();
    }

    @Override
    public void onDrawerItemClick(AdapterView<?> parent, View view, int position, long id) {
        switch (position) {
            case 0:
                handleConnectionClick();
                break;
            case 1:
                checkDeviceUpdates();
                break;
            case 2:
                handleContactClick();
                break;
            case 3:
                handleQuickGuide();
                break;
            case 4:
                handleEula(true);
                break;
            case 5:
                handleAboutClick();
                break;
            default:
                break;
        }
    }

    // Get "raw" filename from given resource ID.
    String getRawFileName(int resourceID)
    {
        TypedValue tv = new TypedValue();
        String fullRawName;
        String []split;
        int i;

        getResources().getValue(resourceID, tv, true);

        fullRawName = tv.string.toString();

        if (fullRawName.isEmpty())
            return "";

        split = fullRawName.split("/");

        // Last entry is expected to be the file's name without an extension.
        return split[split.length-1];
    }

    // Checks if the given file already exists.
    private boolean fileAlreadyExists(String fullPath)
    {
        File f = new File(fullPath);

        if (f.exists() && f.isFile() && !f.isDirectory())
            return true;

        return false;
    }

    // Makes a target file name in the external storage.
    private String makeTargetName(String fileName)
    {
        return Environment.getExternalStorageDirectory() + "/" + fileName;
    }

    // List the file is the raw resources that may present an authentication key file.
    private ArrayList<ResourceIdTargetName> listAuthenticationKeyfileCandidates()
    {
        int i, resourceID;
        String rawName, targetName;
        ArrayList<ResourceIdTargetName> result = new ArrayList<>();

        Log.d(TAG, "Raw file list:");

        Field[]fields;

        fields = R.raw.class.getFields();

        for(i=0; i < fields.length; i++){
            Log.d(TAG, "Checking: " + fields[i].getName() + " / " + fields[i].getGenericType().toString());
            if (!fields[i].getName().toLowerCase(Locale.ENGLISH).contains(NUR_AUTH_IDENT_STR))
                continue;

            Log.e(TAG, " -> OK, continue check.");

            if (fields[i].getGenericType().toString().equalsIgnoreCase("int")) {
                try {
                    resourceID = fields[i].getInt(R.raw.class);
                    rawName = getRawFileName(resourceID);
                    targetName =  makeTargetName(rawName);
                    Log.d(TAG, "Add pair: " + resourceID + " -> " + targetName);
                    result.add(new ResourceIdTargetName(resourceID, targetName));
                }
                catch (Exception ex) {
                    Log.e(TAG, "Exception: "+ ex.getMessage());
                }
            }
            else
                Log.e(TAG, "No match");

        }

        return result;
    }

    // Copies a file from raw resource to device root directory so can be browsed to.
    void copyToDeviceRoot(ResourceIdTargetName rawRes)
    {
        InputStream inputStream = null;
        FileOutputStream outputFile = null;
        boolean ok;

        if (fileAlreadyExists(rawRes.getTargetName())) {
            Log.d(TAG, "Copy to device: file \"" + rawRes.getTargetName() + "\" already exists.");
            return;
        }

        try {
            inputStream = getResources().openRawResource(rawRes.getID());
        }
        catch (Exception ex) {
            Log.e(TAG, "Copy to device SOURCE file error");
            Log.e(TAG, ex.getMessage());
            return;
        }

        try {
            File f = new File(rawRes.getTargetName());
            f.setWritable(true);
            outputFile = new FileOutputStream(f);

            ok = true;
        }
        catch (Exception ex) {
            Log.e(TAG, "Copy to device TARGET file error");
            Log.e(TAG, ex.getMessage());
            ok = false;
        }

        if (!ok ) {
            try { inputStream.close(); } catch (Exception ex) { }
            return;
        }

        Log.d(TAG, "Copy from resources to " + rawRes.getTargetName());

        try {
            int read;
            int total = 0;
            byte []buf = new byte[1024];
            while ((read = inputStream.read(buf)) > 0) {
                outputFile.write(buf, 0, read);
                total += read;
            }

            Log.d(TAG, "Wrote " + total + " + bytes.");
        }
        catch (Exception ex) {
            Log.e(TAG, "Error during copy: " + ex.getMessage());
        }

        try {
            inputStream.close();
        }
        catch (Exception ex) { }

        try {
            outputFile.close();
        }
        catch (Exception ex) { }

    }

    // Go through the list of files and copy them into the device's external storage directory.
    void tryCopyAuthFilesFromResources(ArrayList<ResourceIdTargetName> resourceDefinitions)
    {
        int i;

        for (i=0;i<resourceDefinitions.size();i++)
            copyToDeviceRoot(resourceDefinitions.get(i));
    }

    // The main method that copies the (possibly) present authentication key files from the raw resource.
    private void copyAuthenticationFilesToDevice()
    {
        ArrayList<ResourceIdTargetName> tamFileSourceTargets = listAuthenticationKeyfileCandidates();
        tryCopyAuthFilesFromResources(tamFileSourceTargets);
    }

    public void enableTimerPing(boolean b) {
        mEnableTimerPing = b;
    }
}
