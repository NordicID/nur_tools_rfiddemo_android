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
import com.nordicid.nidulib.UpdateItem;
import com.nordicid.nurapi.AccessoryExtension;
import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurApiAutoConnectTransport;

import java.util.ArrayList;
import nordicid.com.nurupdate.NurUpdateParams;
import static com.nordicid.apptemplate.AppTemplate.getAppTemplate;

public class SettingsAppUpdatesTab extends androidx.fragment.app.Fragment implements View.OnClickListener {

    static final int UPDATE_RESULT = 1;  // The request code

    SettingsAppTabbed mOwner;
    View mView;
    TextView mTxtHdr;
    TextView mTxtAvail;

    NurApi mApi;
    AccessoryExtension mExt;
    NurApiAutoConnectTransport mAutoTransport;

    //Set parameters for Update job
    NurUpdateParams updateParams;
    Button mFilePicker;
    Button mRemoteFilePicker;

    public SettingsAppUpdatesTab(){

        mOwner = SettingsAppTabbed.getInstance();
        mApi = mOwner.getNurApi();
        //mExt = getAppTemplate().getAccessoryApi();
        mExt = AppTemplate.getAppTemplate().getAccessoryApi();
        updateParams = new NurUpdateParams();
        getAppTemplate().mUpdatePending = false;
    }

    public void showAvailUpdatesStatus() {

        if(getAppTemplate().mUpdatePending) return; //if updating pending, do not show get availUpdates.

        getAppTemplate().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ArrayList<UpdateItem> availUpdatesList=null;
                if(mRemoteFilePicker == null)
                    return; //UI Not yet initialized

                mRemoteFilePicker.setEnabled(false);

                if(mApi.isConnected()) {
                    getAppTemplate().refreshAvailableUpdates();
                    availUpdatesList = getAppTemplate().getAvailableUpdates();
                    if(availUpdatesList == null)
                    {
                        if(getAppTemplate().getAvailUpdDownloadStatus() == 1)
                            mTxtHdr.setText("Loading update info...");
                        else
                           mTxtHdr.setText("Cannot load update info!");

                        mTxtHdr.setTextColor(Color.RED);
                        mTxtAvail.setText("");
                    } else {
                        if(availUpdatesList.size() > 0) {
                            mTxtHdr.setText("Available updates");
                            mTxtHdr.setTextColor(Color.BLUE);
                            String listTxt="";
                            for(int x=0;x<availUpdatesList.size();x++) {
                                listTxt += availUpdatesList.get(x).name + System.getProperty("line.separator");
                            }
                            mTxtAvail.setText(listTxt);
                            mTxtAvail.setTextColor(Color.RED);
                            mRemoteFilePicker.setEnabled(true);

                        }
                        else {
                            mTxtHdr.setText("Device UP-TO-DATE");
                            mTxtHdr.setTextColor(Color.rgb(0,128,0));
                            mTxtAvail.setText("");
                        }
                    }
                }
                else {
                    if(getAppTemplate().mUpdatePending==true)
                        mTxtHdr.setText("Updating..");
                    else
                        mTxtHdr.setText("Disconnected");
                    mTxtHdr.setTextColor(Color.BLACK);
                    mTxtAvail.setText("");
                }


            }
        });
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
    public void onClick(View view){

        //NurApi instance
        updateParams.setNurApi(mApi);
        //Possible Nur accessory instance
        updateParams.setAccessoryExtension(mExt);
        //If we are connected to device via Bluetooth, give current ble address.
        mAutoTransport =  Main.getInstance().getAutoConnectTransport();
        if(mApi.isConnected() == false || mAutoTransport == null) {
            //Transport not connected
            Toast.makeText(Main.getInstance(), "Transport not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        updateParams.setDeviceAddress(mAutoTransport.getAddress());

        switch (view.getId()){
            case R.id.btn_select_file: //Local storage
                //Force user to select zip from the filesystem
                updateParams.setZipPath("");
                showNurUpdateUI();
                break;
            case R.id.btn_download_file: //NORDIC ID Servers
                //Load from Nordic ID server.
                updateParams.setZipPath("https://raw.githubusercontent.com/NordicID/nur_firmware/master/zip/NIDLatestFW.zip");
                showNurUpdateUI();
                break;

        }
    }

    boolean showNurUpdateUI()
    {
        getAppTemplate().mUpdatePending=false;

        try {

            Intent startIntent = new Intent(mOwner.getContext(), Class.forName ("nordicid.com.nurupdate.NurDeviceUpdate"));
            //startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityForResult(startIntent, UPDATE_RESULT);
            getAppTemplate().mUpdatePending=true;

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to

        getAppTemplate().mUpdatePending=false;

        Log.i("AppTemp","onActivityResult reqCode=" + requestCode + " Result=" + resultCode);
        if (requestCode == UPDATE_RESULT) {
            getAppTemplate().refreshAvailableUpdates();
            showAvailUpdatesStatus();
        }
    }

    @Override
    public void onStart(){
        super.onStart();
        Log.i("AppTemp","UPD: OnStart...");
        Main.getInstance().setDoNotDisconnectOnStop(true);
    }

    @Override
    public void onResume(){
        super.onResume();
        Log.i("AppTemp","UPD: OnResume...");
    }

    @Override
    public void onPause(){

        super.onPause();
        Log.i("AppTemp","UPD: OnPause....");

    }

    @Override
    public void onStop(){
        super.onStop();
        Log.i("AppTemp","UPD:OnStop....");
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.i("AppTemp","UPD:onDestroy....");
        Main.getInstance().setDoNotDisconnectOnStop(false);
    }
}