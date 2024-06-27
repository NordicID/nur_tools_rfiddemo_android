package com.nordicid.rfiddemo;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.nordicid.apptemplate.AppTemplate;

import com.nordicid.nurapi.AccessoryExtension;
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

import java.util.ArrayList;
import java.util.List;

import static com.nordicid.apptemplate.AppTemplate.getAppTemplate;

/**
 * Created by Ari_Poyhonen on 14.12.2017.
 */

public class SettingsAppInventoryTab extends Fragment
{
    private final int REQUEST_CODE_OPEN_DIRECTORY = 44;
    //Create a class variable that is your activities request code
    private static final int REQUEST_DIRECTORY_PICKER = 1;

    SettingsAppTabbed mOwner;
    NurApi mApi;

    AccessoryExtension mExt;

    private Spinner mInvTypeSpinner;
    private Spinner mTagDataTransSpinner;
    private Spinner mDataLen;
    private TextView mTxtDataLen;
    private Button mButtonBrowseFolder;
    private TextView mTxtPath;
    private CheckBox mAddGpsCoordinate;

    private RadioButton mRadioDown;
    private RadioButton mRadioClick;

    private int mDataLengthInt;
    private int mInvTypeInt;
    private int mTagDataTransInt;
    private boolean mAddGpsCoord;
    private boolean mTriggerDown;
    private String mPath;

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
        mExt = getAppTemplate().getAccessoryApi();

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

        mDataLen.setEnabled(enable);

        if(getAppTemplate().getAccessorySupported() && enable == true)
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
        mTagDataTransSpinner.setSelection(mTagDataTransInt);
        Log.w("INV","Update TranslationMode=" + mTagDataTransInt);

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
        mTagDataTransInt = settings.getInt("TagDataTrans",0);
        mAddGpsCoord = settings.getBoolean("AddGpsCoord",false);

        mTriggerDown = settings.getBoolean("TriggerDown",false);
        //mPath = settings.getString("FilePath","content://com.android.externalstorage.documents/tree/primary%3ADownload");
        //mPath = settings.getString("FilePath","content://com.android.externalstorage.documents/tree/primary%3ADownload");
        mPath = settings.getString("FilePath", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath());

        return inflater.inflate(R.layout.tab_settings_inventory, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        mInvTypeSpinner = (Spinner) view.findViewById(R.id.inv_mode_spinner);
        mTagDataTransSpinner = (Spinner) view.findViewById(R.id.inv_read_spinner);
        mTxtDataLen = (TextView) view.findViewById(R.id.textDataLength);
        mButtonBrowseFolder = (Button)view.findViewById(R.id.buttonBrowseFolder);
        mTxtPath = (TextView) view.findViewById(R.id.textPath);
        mAddGpsCoordinate = (CheckBox) view.findViewById(R.id.checkBoxExportLocation);
        mDataLen = (Spinner) view.findViewById(R.id.data_length_spinner);
        mRadioDown = (RadioButton) view.findViewById(R.id.radio_trig_down);
        mRadioClick = (RadioButton) view.findViewById(R.id.radio_trig_click);

        UpdateIRViews();

        if(getAppTemplate().getAccessorySupported()) {
            if (mTriggerDown) {
                mRadioDown.setChecked(true);
                mRadioClick.setChecked(false);
            } else {
                mRadioDown.setChecked(false);
                mRadioClick.setChecked(true);
            }
        }

        if(mPath.startsWith("content")) {
            mTxtPath.setText(Uri.decode(mPath));
        }
        else
            mTxtPath.setText(mPath);

        final List<String> dataLengthItems = new ArrayList<>();

        for(int x=1;x<=16;x++)
            dataLengthItems.add(Integer.toString(x));


        final ArrayAdapter<String> spinnerArrayAdapterDataLen = new ArrayAdapter<String>(view.getContext(),android.R.layout.simple_spinner_item,dataLengthItems);
        mDataLen.setAdapter(spinnerArrayAdapterDataLen);

        mAddGpsCoordinate.setChecked((mAddGpsCoord));

        mAddGpsCoordinate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settingEditor.putBoolean("AddGpsCoord",isChecked);
                settingEditor.apply();
                if(isChecked)
                    getAppTemplate().refreshLocation();
            }
        });

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

        mButtonBrowseFolder.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                selectFolder();
            }
        });

        mInvTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

                mInvTypeInt = pos;
                settingEditor.putInt("InvType",mInvTypeInt);
                settingEditor.apply();

                UpdateIRViews();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mTagDataTransSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

                mTagDataTransInt = pos;
                settingEditor.putInt("TagDataTrans",mTagDataTransInt);
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

    public void selectFolder() {
        Main.getInstance().setDoNotDisconnectOnStop(true);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == Activity.RESULT_OK) {

            Uri treeUri = data.getData();
            mPath = treeUri.toString();

            settingEditor.putString("FilePath",mPath);
            settingEditor.apply();
        }

        Main.getInstance().setDoNotDisconnectOnStop(false);
    }
}
