package com.nordicid.rfiddemo;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.nordicid.apptemplate.AppTemplate;
import com.nordicid.nidulib.NiduLib;
import com.nordicid.nidulib.NiduLibListener;
import com.nordicid.nidulib.UpdateItem;
import com.nordicid.nurapi.AccessoryExtension;
import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurApiAutoConnectTransport;

import java.util.ArrayList;

import nordicid.com.nurupdate.NurUpdateParams;

import static com.nordicid.apptemplate.AppTemplate.getAppTemplate;

public class SettingsAppUpdatesTab extends androidx.fragment.app.Fragment implements View.OnClickListener {
    static final String TAG = "Update";
    static final int UPDATE_RESULT = 1;  // The request code

    SettingsAppTabbed mOwner;
    View mView;
    TextView mTxtHdr;
    TextView mTxtAvail;

    NurApi mApi;
    AccessoryExtension mExt;
    static NiduLib nidu = null;
    NurApiAutoConnectTransport mAutoTransport;

    //Set parameters for Update job
    NurUpdateParams updateParams;
    Button mFilePicker;
    Button mRemoteFilePicker;

    Thread validateThread = null;
    static ArrayList<UpdateItem> availUpdatesList;

    public SettingsAppUpdatesTab() {

        mOwner = SettingsAppTabbed.getInstance();
        mApi = mOwner.getNurApi();
        //mExt = getAppTemplate().getAccessoryApi();
        mExt = AppTemplate.getAppTemplate().getAccessoryApi();
        updateParams = new NurUpdateParams();
        getAppTemplate().mUpdatePending = false;

        nidu = new NiduLib();
        nidu.setNurApi(mApi);
        nidu.setAccessoryExtension(mExt);
        nidu.setListener(mNiduLibListener);
    }

    NiduLibListener mNiduLibListener = (event, i, o) -> {
        switch (event) {
            case LOG:
                Log.i(TAG, "LOG:" + o.toString());
                break;
            case STATUS:
                Log.i(TAG, "STATUS:" + o.toString());
                break;
            case VALIDATE:
                break;
        }
    };

    public synchronized void showAvailUpdatesStatus() {
        if (mRemoteFilePicker == null) return; //UI Not yet initialized

        mTxtHdr.setTextColor(Color.BLACK);

        if (!mApi.isConnected()) {
            mTxtHdr.setText("Disconnected");
            return;
        }

        mTxtHdr.setText("Loading update info...");

        if (getAppTemplate().mUpdatePending) {
            mTxtHdr.setText("Updating..");
            return;
        }

        if (validateThread != null) {
            if (validateThread.isAlive()) return;
        }

        validateThread = new Thread(() -> {
            try {
                byte[] updInfo = NiduLib.DownLoadFromURL("https://raw.githubusercontent.com/NordicID/nur_firmware/master/zip/NIDLatestFW.json");
                availUpdatesList = nidu.GetAvailableUpdates(updInfo);
            } catch (Exception e) {
                mTxtHdr.setText("Invalid file!");
                return;
            }

            getAppTemplate().runOnUiThread(() -> {
                if (availUpdatesList.size() > 0) {
                    mTxtHdr.setText("Available updates");
                    mTxtHdr.setTextColor(Color.BLUE);
                    StringBuilder listTxt = new StringBuilder();
                    for (int x = 0; x < availUpdatesList.size(); x++) {
                        listTxt.append(availUpdatesList.get(x).name).append(System.getProperty("line.separator"));
                    }
                    mTxtAvail.setText(listTxt.toString());
                    mTxtAvail.setTextColor(Color.RED);
                    mRemoteFilePicker.setEnabled(true);

                } else {
                    mTxtHdr.setText("Device UP-TO-DATE");
                    mTxtHdr.setTextColor(Color.rgb(0, 128, 0));
                    mTxtAvail.setText("");
                }
            });
        });
        validateThread.start();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.tab_settings_updates, container, false);
        mView = view;

        mTxtHdr = (TextView) view.findViewById(R.id.textViewUpdateHdr);
        mTxtAvail = (TextView) view.findViewById(R.id.textViewAvailUpdates);
        mFilePicker = (Button) view.findViewById(R.id.btn_select_file);
        mFilePicker.setOnClickListener(this);
        mRemoteFilePicker = (Button) view.findViewById(R.id.btn_download_file);
        mRemoteFilePicker.setOnClickListener(this);

        showAvailUpdatesStatus();

        return view;
    }

    @Override
    public void onClick(View view) {

        //NurApi instance
        updateParams.setNurApi(mApi);
        //Possible Nur accessory instance
        updateParams.setAccessoryExtension(mExt);
        //If we are connected to device via Bluetooth, give current ble address.
        mAutoTransport = Main.getInstance().getAutoConnectTransport();
        if (!mApi.isConnected() || mAutoTransport == null) {
            //Transport not connected
            Toast.makeText(Main.getInstance(), "Transport not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        updateParams.setDeviceAddress(mAutoTransport.getAddress());

        switch (view.getId()) {
            case R.id.btn_select_file -> { //Local storage
                //Force user to select zip from the filesystem
                updateParams.setZipPath("");
                showNurUpdateUI();
            }
            case R.id.btn_download_file -> { //NORDIC ID Servers
                //Load from Nordic ID server.
                updateParams.setZipPath("https://raw.githubusercontent.com/NordicID/nur_firmware/master/zip/NIDLatestFW.zip");
                showNurUpdateUI();
            }
        }
    }

    void showNurUpdateUI() {
        getAppTemplate().mUpdatePending = false;
        try {
            Intent startIntent = new Intent(mOwner.getContext(), Class.forName("nordicid.com.nurupdate.NurDeviceUpdate"));
            //startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityForResult(startIntent, UPDATE_RESULT);
            getAppTemplate().mUpdatePending = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to

        getAppTemplate().mUpdatePending = false;

        Log.i("AppTemp", "onActivityResult reqCode=" + requestCode + " Result=" + resultCode);
        if (requestCode == UPDATE_RESULT) {
            showAvailUpdatesStatus();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i("AppTemp", "UPD: OnStart...");
        Main.getInstance().setDoNotDisconnectOnStop(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i("AppTemp", "UPD: OnResume...");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i("AppTemp", "UPD: OnPause....");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.i("AppTemp", "UPD:OnStop....");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("AppTemp", "UPD:onDestroy....");
        Main.getInstance().setDoNotDisconnectOnStop(false);
    }
}