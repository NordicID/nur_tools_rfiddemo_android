package com.nordicid.rfiddemo;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.text.Layout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RadioButton;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.nordicid.apptemplate.AppTemplate;
import com.nordicid.nuraccessory.NurAccessoryConfig;
import com.nordicid.nuraccessory.NurAccessoryExtension;
import com.nordicid.nuraccessory.NurAccessoryVersionInfo;

import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurApiListener;
import com.nordicid.nurapi.NurEventAutotune;
import com.nordicid.nurapi.NurEventClientInfo;
import com.nordicid.nurapi.NurEventDeviceInfo;
import com.nordicid.nurapi.NurEventEpcEnum;
import com.nordicid.nurapi.NurEventFrequencyHop;
import com.nordicid.nurapi.NurEventIOChange;
import com.nordicid.nurapi.NurEventInventory;
import com.nordicid.nurapi.NurEventNxpAlarm;
import com.nordicid.nurapi.NurEventProgrammingProgress;
import com.nordicid.nurapi.NurEventTagTrackingChange;
import com.nordicid.nurapi.NurEventTagTrackingData;
import com.nordicid.nurapi.NurEventTraceTag;
import com.nordicid.nurapi.NurEventTriggeredRead;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ari_Poyhonen on 14.12.2017.
 */

public class SettingsAppInventoryTab extends Fragment
{
    SettingsAppTabbed mOwner;
    NurApi mApi;

    NurAccessoryExtension mExt;

    private Spinner mInvTypeSpinner;
    private Spinner mDataLen;
    private TextView mTxtDataLen;
    private CheckBox mChkExport;
    //private TextView mFilePath;

    private RadioButton mRadioDown;
    private RadioButton mRadioClick;

    private int mDataLengthInt;
    private int mInvTypeInt;
    private boolean mExportBoolean;
    private boolean mTriggerDown;

    SharedPreferences settings = null;
    SharedPreferences.Editor settingEditor = null;

    private NurApiListener mThisClassListener = null;

    public NurApiListener getNurApiListener()
    {
        return mThisClassListener;
    }

    public SettingsAppInventoryTab()
    {
        mOwner = SettingsAppTabbed.getInstance();
        mApi = mOwner.getNurApi();
        mExt = AppTemplate.getAppTemplate().getAccessoryApi();

        mThisClassListener = new NurApiListener() {
            @Override
            public void connectedEvent() {
                if (isAdded()) {
                    enableItems();
                }
            }

            @Override
            public void disconnectedEvent() {
                if (isAdded()) {
                    enableItems();
                }
            }

            @Override public void logEvent(int level, String txt) {}
            @Override public void bootEvent(String event) {}
            @Override public void inventoryStreamEvent(NurEventInventory event) { }
            @Override public void IOChangeEvent(NurEventIOChange event) {}
            @Override public void traceTagEvent(NurEventTraceTag event) { }
            @Override public void triggeredReadEvent(NurEventTriggeredRead event) {}
            @Override public void frequencyHopEvent(NurEventFrequencyHop event) {}
            @Override public void debugMessageEvent(String event) {}
            @Override public void inventoryExtendedStreamEvent(NurEventInventory event) { }
            @Override public void programmingProgressEvent(NurEventProgrammingProgress event) {}
            @Override public void deviceSearchEvent(NurEventDeviceInfo event) {}
            @Override public void clientConnectedEvent(NurEventClientInfo event) {}
            @Override public void clientDisconnectedEvent(NurEventClientInfo event) {}
            @Override public void nxpEasAlarmEvent(NurEventNxpAlarm event) {}
            @Override public void epcEnumEvent(NurEventEpcEnum event) {}
            @Override public void autotuneEvent(NurEventAutotune event) {}

            @Override
            public void tagTrackingScanEvent(NurEventTagTrackingData event) {
                // TODO Auto-generated method stub

            }

            @Override
            public void tagTrackingChangeEvent(NurEventTagTrackingChange event) {
                // TODO Auto-generated method stub

            }

        };
    }

    public void onRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.radio_trig_down:
                if (checked)
                    mTriggerDown = true;
                settingEditor.putBoolean("TriggerBehavior",mTriggerDown);
                settingEditor.apply();
                break;
            case R.id.radio_trig_click:
                if (checked)
                    mTriggerDown = false;
                settingEditor.putBoolean("TriggerBehavior",mTriggerDown);
                settingEditor.apply();
                break;
        }
    }

    public void onVisibility(boolean val)
    {
        if (val)
        {
            if (isAdded()) {
                enableItems();
            }
        }
    }

    private void enableItems() {

        Boolean enable=false;

        if (mApi.isConnected()) {
            enable=true;
        }

        mChkExport.setEnabled(enable);
        mDataLen.setEnabled(enable);

        if(Main.getAppTemplate().getAccessorySupported() && enable == true)
        {
            mRadioClick.setEnabled(enable);
            mRadioDown.setEnabled(enable);
        }
        else
        {
            mRadioClick.setEnabled(false);
            mRadioDown.setEnabled(false);
        }
    }

    private void UpdateIRViews()
    {
        if(mInvTypeInt > 0) {
            mDataLen.setVisibility(View.VISIBLE);
            mInvTypeSpinner.setSelection(mInvTypeInt);
            mDataLen.setEnabled(true);
            mTxtDataLen.setVisibility(View.VISIBLE);
            if(mInvTypeInt == 1) mTxtDataLen.setText("TID bank read length in words");
            else mTxtDataLen.setText("USER bank read length in words");
        }
        else {
            mDataLen.setVisibility(View.INVISIBLE);
            mTxtDataLen.setVisibility(View.INVISIBLE);
        }
    }

    private void handleSetConfiguration()
    {
        Intent intent;
        Intent filePicker;

        //intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.setType("text/plain");

        filePicker = Intent.createChooser(intent, getResources().getString(R.string.file_picker));

        try {
            Main.getInstance().setDoNotDisconnectOnStop(true);

            startActivityForResult(filePicker, 42);
        } catch (Exception ex) {

            String strErr = ex.getMessage();
            Toast.makeText(getActivity(), "Error:\n" + strErr, Toast.LENGTH_SHORT).show();
            Main.getInstance().setDoNotDisconnectOnStop(false);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        settings = Main.getApplicationPrefences();
        settingEditor = settings.edit();

        mDataLengthInt = settings.getInt("DataLength",2);
        mInvTypeInt = settings.getInt("InvType",0);

        mExportBoolean = settings.getBoolean("ExportBool",false);
        mTriggerDown = settings.getBoolean("TriggerDown",false);

        //File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        //mExportString = path.getAbsolutePath();

        return inflater.inflate(R.layout.tab_settings_inventory, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        //mFilePath = (TextView) view.findViewById(R.id.textExportFolder);
        mInvTypeSpinner = (Spinner) view.findViewById(R.id.inv_mode_spinner);
        mTxtDataLen = (TextView) view.findViewById(R.id.textDataLength);
        mChkExport = (CheckBox) view.findViewById(R.id.checkBoxExport);
        mDataLen = (Spinner) view.findViewById(R.id.data_length_spinner);
        mRadioDown = (RadioButton) view.findViewById(R.id.radio_trig_down);
        mRadioClick = (RadioButton) view.findViewById(R.id.radio_trig_click);

        mChkExport.setChecked(mExportBoolean);

        UpdateIRViews();

        if(Main.getAppTemplate().getAccessorySupported()) {
            if (mTriggerDown) {
                mRadioDown.setChecked(true);
                mRadioClick.setChecked(false);
            } else {
                mRadioDown.setChecked(false);
                mRadioClick.setChecked(true);
            }
        }

        //File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        //mFilePath.setText(path.getAbsolutePath());

        final List<String> dataLengthItems = new ArrayList<>();

        for(int x=1;x<=16;x++)
            dataLengthItems.add(Integer.toString(x));


        final ArrayAdapter<String> spinnerArrayAdapterDataLen = new ArrayAdapter<String>(view.getContext(),android.R.layout.simple_spinner_item,dataLengthItems);
        mDataLen.setAdapter(spinnerArrayAdapterDataLen);


        String comp=Integer.toString(mDataLengthInt);
        int spinnerPosition = spinnerArrayAdapterDataLen.getPosition(comp);
        mDataLen.setSelection(spinnerPosition);

        mRadioClick.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mTriggerDown = false;
                settingEditor.putBoolean("TriggerDown",mTriggerDown);
                settingEditor.apply();

            }
        });

        mRadioDown.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mTriggerDown = true;
                settingEditor.putBoolean("TriggerDown",mTriggerDown);
                settingEditor.apply();

            }
        });


        mChkExport.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mExportBoolean = mChkExport.isChecked();
                settingEditor.putBoolean("ExportBool",mExportBoolean);
                settingEditor.apply();
            }
        });

        mInvTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                //Object item = parent.getItemAtPosition(pos);
                mInvTypeInt = pos; //Integer.parseInt(item.toString());
                settingEditor.putInt("InvType",mInvTypeInt);
                settingEditor.apply();

                UpdateIRViews();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mDataLen.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                Object item = parent.getItemAtPosition(pos);
                mDataLengthInt = Integer.parseInt(item.toString());
                settingEditor.putInt("DataLength",mDataLengthInt);
                settingEditor.apply();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });


        enableItems();
    }


}
