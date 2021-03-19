package com.nordicid.rfiddemo;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;

import com.nordicid.apptemplate.AppTemplate;
import com.nordicid.nurapi.ACC_SENSOR_FEATURE;
import com.nordicid.nurapi.ACC_SENSOR_FILTER_FLAG;
import com.nordicid.nurapi.ACC_SENSOR_MODE_FLAG;
import com.nordicid.nurapi.ACC_SENSOR_SOURCE;
import com.nordicid.nurapi.AccSensorChanged;
import com.nordicid.nurapi.AccSensorConfig;
import com.nordicid.nurapi.AccSensorEventListener;
import com.nordicid.nurapi.AccSensorFilter;
import com.nordicid.nurapi.AccSensorRangeData;
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
import java.util.HashMap;

public class SettingsAppSensorTab extends Fragment
{
    public static final String TAG = "SENSOR";

    SettingsAppTabbed mOwner;
    NurApi mApi;
    AccessoryExtension mExt;
    NurApiListener mOrigNurListener;

    ArrayList<AccSensorConfig> sensorList;
    //Range Data var
    private static int mRangeData = 0;
    private static int mGpioState = 0;
    private static int selectedSensor = -1;
    private static boolean mChangeStreaming=false;

    private static CheckBox mCheckEnableToF;
    //private static ProgressBar mProgressRange;
    private static EditText mEditRangeLo;
    //private static EditText mEditRangeHi;
    private static Button mButtonApply;
    private static TextView mTxtRange;
    private static TextView mTxtState;

    private NurApiListener mThisClassListener = null;

    public NurApiListener getNurApiListener()
    {
        return mThisClassListener;
    }


    public SettingsAppSensorTab()
    {
        mOwner = SettingsAppTabbed.getInstance();
        mApi = mOwner.getNurApi();
        mExt = AppTemplate.getAppTemplate().getAccessoryApi();

        Log.w(TAG,"SettingsAppSensorTab");


        mThisClassListener = new NurApiListener() {
            @Override public void logEvent(int i, String s) { }
            @Override public void connectedEvent() {
                if (isAdded()) {

                }
            }
            @Override public void disconnectedEvent() {
                if (isAdded()) {

                }
            }
            @Override public void bootEvent(String s) { }
            @Override public void inventoryStreamEvent(NurEventInventory nurEventInventory) { }
            @Override public void IOChangeEvent(NurEventIOChange nurEventIOChange) {
                //ToF sensor has triggered GPIO event
                // Direction goes 0->1 when sensors reads less than Range Lo filter (unit: mm)
                // Direction goes 1->0 when sensors reads more than Range Hi filter (unit: mm)
                String ioEventString = "IOEvent Dir=" + nurEventIOChange.direction + " Sensor=" + nurEventIOChange.sensor + " Source=" + nurEventIOChange.source+"\n";
                Log.w(TAG,ioEventString);
                if(nurEventIOChange.source == ACC_SENSOR_SOURCE.ToFSensor.getNumVal()) {
                    mGpioState = nurEventIOChange.direction;
                    Beeper.beep(Beeper.BEEP_100MS);
                }
            }
            @Override public void traceTagEvent(NurEventTraceTag nurEventTraceTag) { }
            @Override public void triggeredReadEvent(NurEventTriggeredRead nurEventTriggeredRead) { }
            @Override public void frequencyHopEvent(NurEventFrequencyHop nurEventFrequencyHop) { }
            @Override public void debugMessageEvent(String s) { }
            @Override public void inventoryExtendedStreamEvent(NurEventInventory nurEventInventory) { }
            @Override public void programmingProgressEvent(NurEventProgrammingProgress nurEventProgrammingProgress) { }
            @Override public void deviceSearchEvent(NurEventDeviceInfo nurEventDeviceInfo) { }
            @Override public void clientConnectedEvent(NurEventClientInfo nurEventClientInfo) { }
            @Override public void clientDisconnectedEvent(NurEventClientInfo nurEventClientInfo) { }
            @Override public void nxpEasAlarmEvent(NurEventNxpAlarm nurEventNxpAlarm) { }
            @Override public void epcEnumEvent(NurEventEpcEnum nurEventEpcEnum) { }
            @Override public void autotuneEvent(NurEventAutotune nurEventAutotune) { }
            @Override public void tagTrackingScanEvent(NurEventTagTrackingData nurEventTagTrackingData) { }
            @Override public void tagTrackingChangeEvent(NurEventTagTrackingChange nurEventTagTrackingChange) { }
        };

    }

    private void streamingOff() {

            //Shutdown streaming
            if(selectedSensor == -1)
                return;

            try {
                mChangeStreaming = true; //TODO: force streaming off for now but..
                if (mChangeStreaming) {
                    //Originally streaming was off. Put in that state
                    Log.w(TAG,"Change streaming off");
                    AccSensorConfig cfg = sensorList.get(selectedSensor);
                    cfg.clearModeFlag(ACC_SENSOR_MODE_FLAG.Stream);
                    mExt.accSensorSetConfig(cfg);
                } else
                    Log.w(TAG,"Originally streaming was on");
            }
            catch (Exception e) {

            }
            //Leaving. Set original listener back
            mApi.setListener(mOrigNurListener);

    }

    public void onVisibility(boolean val)    {

        if (isAdded()) {

        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        Log.w(TAG,"Sensor onCreateView");
        mOrigNurListener = mApi.getListener();
        //Set event listener for this activity. Mainly for receiving IOEvents
        mApi.setListener(mThisClassListener);
        return inflater.inflate(R.layout.tab_settings_sensors, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        //Register sensor event listener. Then we are able to receive events from accessory sensors.
        mExt.registerSensorEventListener(mSensorResult);

        mCheckEnableToF = (CheckBox) view.findViewById(R.id.sensorCheckBoxEnableToF);
        mEditRangeLo = (EditText)view.findViewById(R.id.sensorEditRangeLo);
        //mEditRangeHi = (EditText)view.findViewById(R.id.sensorEditRangeHi);
        mButtonApply = (Button)view.findViewById((R.id.sensorButtonApplyFilter));
        //mProgressRange = (ProgressBar)view.findViewById(R.id.sensorProgressBarRange);
        //mProgressRange.setMax(300);
        mTxtRange = (TextView) view.findViewById(R.id.sensorTextViewRange);
        mTxtState = (TextView) view.findViewById(R.id.sensorTextViewGpioState);

        try {
            HashMap<String, String> tmp = new HashMap<String, String>();

            sensorList = mExt.accSensorEnumerate();
            //iterate sensor list. sensorList is null if no sensors available
            for(int x=0;x<sensorList.size();x++) {
                AccSensorConfig cfg = sensorList.get(x);
                //Features of sensor telling us what we can do with it.
                String featureTxt="Features:";
                ACC_SENSOR_FEATURE featureFlags[] = cfg.getFeatureFlags();

                for(int i=0;i<featureFlags.length;i++) {
                    featureTxt+=featureFlags[i].toString() + "/";
                }

                String row = cfg.source + " (" + featureTxt + ")";
                Log.w(TAG,"Sensor:" + row);
                if(cfg.source == ACC_SENSOR_SOURCE.ToFSensor) {
                    //This is the one we are intrested..
                    selectedSensor = x;
                    break;
                }

            }
        } catch (Exception e) {
            Log.e(TAG,"Error enumerating sensors:" + e.getMessage());
        }

        UpdateSensorControls();
        if(mCheckEnableToF.isChecked()) {
            AccSensorConfig cfg = sensorList.get(selectedSensor);
            if(cfg.hasFeature(ACC_SENSOR_FEATURE.StreamValue) == true) {
                //Streaming supported. Activate it if not already set, as we want to see live range value
                if(mChangeStreaming) {
                    cfg.setModeFlag(ACC_SENSOR_MODE_FLAG.Stream);
                    try {
                        mExt.accSensorSetConfig(cfg);
                    } catch (Exception e) {
                        Toast.makeText(view.getContext(), "ERROR changing mode:" + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.w(TAG, "ERROR changing mode:" + e.getMessage());
                    }
                }
            }

        }

        mButtonApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    if(selectedSensor == -1)
                        return;

                    AccSensorConfig cfg = sensorList.get(selectedSensor);
                    AccSensorFilter filter = new AccSensorFilter();
                    filter.setFilterFlag(ACC_SENSOR_FILTER_FLAG.Range);
                    filter.rangeThreshold.lo = Integer.parseInt(mEditRangeLo.getText().toString());
                    filter.rangeThreshold.hi = filter.rangeThreshold.lo + 50; //Integer.parseInt(mEditRangeHi.getText().toString());
                    mExt.accSensorSetFilter(cfg.source, filter);
                    Toast.makeText(view.getContext(), "Range threshold set successfully", Toast.LENGTH_LONG).show();
                }
                catch (Exception e) {
                    Log.e(TAG,e.getMessage());
                    Toast.makeText(view.getContext(), "ERROR changing range threshold:" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

        mCheckEnableToF.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

                if(selectedSensor == -1)
                    return;

                try {
                    AccSensorConfig cfg = sensorList.get(selectedSensor);

                    if (b) {
                        //Enable ToF
                        cfg.setModeFlag(ACC_SENSOR_MODE_FLAG.Gpio);
                        cfg.setModeFlag(ACC_SENSOR_MODE_FLAG.Stream);
                        mExt.accSensorSetConfig(cfg);
                        Toast.makeText(getContext(), "ToF enabled", Toast.LENGTH_LONG).show();
                    } else {
                        //Disable ToF
                        cfg.clearModeFlag(ACC_SENSOR_MODE_FLAG.Gpio);
                        cfg.clearModeFlag(ACC_SENSOR_MODE_FLAG.Stream);
                        mExt.accSensorSetConfig(cfg);
                        Toast.makeText(getContext(), "ToF disabled", Toast.LENGTH_LONG).show();
                    }
                }catch (Exception e) {
                    Log.e(TAG,e.getMessage());
                    Toast.makeText(getContext(), "ERROR:" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.w(TAG,"onStop");
        streamingOff();
    }

    private void UpdateSensorControls() {

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(selectedSensor==-1) return;

                    AccSensorConfig cfg = sensorList.get(selectedSensor);
                    AccSensorFilter filter = mExt.accSensorGetFilter(sensorList.get(selectedSensor).source);
                    boolean streamFound=false;

                    //Set Mode checkboxes
                    ACC_SENSOR_MODE_FLAG[] modeFlags = cfg.getModeFlags();
                    //Clear mode checkboxes and set check again if set
                    mCheckEnableToF.setChecked(false);

                    if(modeFlags != null) {
                        for (int i = 0; i < modeFlags.length; i++) {
                            if (modeFlags[i] == ACC_SENSOR_MODE_FLAG.Gpio)
                                mCheckEnableToF.setChecked(true);
                            if(modeFlags[i] == ACC_SENSOR_MODE_FLAG.Stream)
                                streamFound=true;
                        }
                    }

                    if(streamFound) mChangeStreaming=false;
                    else mChangeStreaming=true;

                    mEditRangeLo.setText(Integer.toString(filter.rangeThreshold.lo));
                    //mEditRangeHi.setText(Integer.toString(filter.rangeThreshold.hi));

                } catch (Exception e) {
                    Log.e(TAG, "Error updating controls:" + e.getMessage());
                }
            }
        });

    }

    private void showOnUI() {
        if (isAdded()==false) return;

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //mProgressRange.setProgress(mRangeData);
                mTxtRange.setText(mRangeData +" mm");
                mTxtState.setText(Integer.toString(mGpioState));
            }
        });
    }

    private AccSensorEventListener mSensorResult = new AccSensorEventListener() {
        @Override
        public void onSensorChanged(AccSensorChanged accSensorChanged) {
            Log.w(TAG,"onSensorChanged=" + accSensorChanged.source + " Removed=" + accSensorChanged.removed+"\n");
        }

        @Override
        public void onRangeData(AccSensorRangeData accSensorRangeData) {

            mRangeData = accSensorRangeData.range;
            //Log.w(TAG,"onRangeData:" + mRangeData );
            showOnUI();
        }
    };
}
