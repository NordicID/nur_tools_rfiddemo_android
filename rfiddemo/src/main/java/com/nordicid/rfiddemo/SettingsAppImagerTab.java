package com.nordicid.rfiddemo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by Ari_Poyhonen on 14.12.2017.
 */

public class SettingsAppImagerTab extends Fragment
{
    SettingsAppTabbed mOwner;
    NurApi mApi;

    NurAccessoryExtension mExt;

    TextView mImagerConfigInfoLabel;

    private Button mOpenCfgFile;
    private Button mBarcodeTest;
    private TextView mUserGuideLink;
    private TextView mLinkHeader;

    private NurApiListener mThisClassListener = null;

    public NurApiListener getNurApiListener()
    {
        return mThisClassListener;
    }

    public SettingsAppImagerTab()
    {
        mOwner = SettingsAppTabbed.getInstance();
        mApi = mOwner.getNurApi();
        mExt = AppTemplate.getAppTemplate().getAccessoryApi();

        mThisClassListener = new NurApiListener() {
            @Override
            public void connectedEvent() {
                if (isAdded()) {
                    enableItems(true);
                }
            }

            @Override
            public void disconnectedEvent() {
                if (isAdded()) {
                    enableItems(false);
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

    public void onVisibility(boolean val)
    {
        if (val)
        {
            if (isAdded()) {
                enableItems(mApi.isConnected());
            }
        }
    }

    private void enableItems(boolean v)
    {
        mOpenCfgFile.setEnabled(v);
        mBarcodeTest.setEnabled(v);

        /*
        if (v == true)
        {
            mLinkHeader.setText("How to generate configuration file?");
            try
            {

                NurAccessoryConfig cfg = mExt.getConfig();

                if (cfg.isDeviceEXA51()) {
                    mUserGuideLink.setText("https://www.nordicid.com/en/home/products-barcode-uhf-rfid-reader-writer/extensions-for-smart-devices/nordic-id-exa51/#tab_support-and-downloads");
                } else {
                    mUserGuideLink.setText("https://www.nordicid.com/en/home/products-barcode-uhf-rfid-reader-writer/extensions-for-smart-devices/nordic-id-exa31/#tab_support-and-downloads");
                }

            } catch (Exception ex) {
                String strErr = ex.getMessage();
                Toast.makeText(getActivity(), "Error:\n" + strErr, Toast.LENGTH_SHORT).show();
            }
        }
        else
        {

            mUserGuideLink.setText("");
            mLinkHeader.setText("");

        }
        */
    }

    private void handleSetConfiguration()
    {
        Intent intent;
        Intent filePicker;

        intent = new Intent(Intent.ACTION_GET_CONTENT);
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
        return inflater.inflate(R.layout.tab_settings_imager, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);
       // mImagerConfigInfoLabel = (TextView)view.findViewById(R.id.imagerConfigInfo);
        //mImagerConfigInfoLabel.setText = "Hello imager config";
        mOpenCfgFile = (Button) view.findViewById(R.id.open_img_cfg_button);
        mBarcodeTest = (Button) view.findViewById(R.id.barcode_test_button);
        mUserGuideLink = (TextView) view.findViewById(R.id.exa_userguide_link);
        mLinkHeader = (TextView) view.findViewById(R.id.link_header);
        mUserGuideLink.setText("https://www.nordicid.com/en/downloads/");
        mLinkHeader.setText("Information about how to generate configuration file can be found from user guide of respective product.\n" +
                "The user guides can be found from Nordic ID Support pages\n");

        mOpenCfgFile.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                //Toast.makeText(getActivity(), "OpenCfg", Toast.LENGTH_SHORT).show();
                handleSetConfiguration();
            }
        });

        mBarcodeTest.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                Main.getInstance().setApp("Barcode");
                //Toast.makeText(getActivity(), "BarcodeTst", Toast.LENGTH_SHORT).show();
            }
        });
        enableItems(mApi.isConnected());
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 42 && resultCode == Activity.RESULT_OK)
        {
            super.onActivityResult(requestCode, resultCode, data);
            if (data != null)
            {
                Uri uri = data.getData();
                handleFileSelection(uri);
            }
        }
    }

    private void handleFileSelection(Uri uri)
    {
        BufferedReader br;
        int succes_cnt=0;
        int nack_cnt = 0;

        try {
            br = new BufferedReader(new InputStreamReader(getActivity().getContentResolver().openInputStream(uri)));

            String line = null;
            while ((line = br.readLine()) != null)
            {
                Log.e("0","IMG cfg=" + line);
                try
                {
                    byte arr[] = mExt.imagerCmd(line, 0);
                    if(arr == null)
                    {
                        //mEditText.setText("Config failed! (invalid config string)");
                        //Toast.makeText(getActivity(), "Config line not valid", Toast.LENGTH_SHORT).show();
                        //Not valid config line. Take next
                        continue;
                    }
                    if(arr[0] == 21)
                    {
                        nack_cnt++;
                    }
                    else if(arr[0] == 6)
                    {
                        //mEditText.setText("Config success!");
                        succes_cnt++;
                    }
					/*
					else
					{
						mEditText.setText("Config failed! " + String.valueOf(arr[0]));

						for (int x = 0; x < arr.length; x++) {
							Log.e("0", "Line [" + String.valueOf(x) + "]=" + String.valueOf(arr[x]));
						}
					}
					*/
                }
                catch (Exception e)
                {
                    Log.e("0","IMG err=" + e.getMessage());
                    Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    //mEditText.setText(e.getMessage());
                    //break;
                }


            }
            br.close();

            if(succes_cnt>0)
            {
                //Save codes to Imager flash(Opticon)
                line = "@MENU_OPTO@ZZ@Z2@ZZ@OTPO_UNEM@";
                try {
                    byte rsp[] = mExt.imagerCmd(line, 0);
                    if (rsp == null) {
                        Toast.makeText(getActivity(), "Saving configuration failed! (no response)", Toast.LENGTH_SHORT).show();
                        //mEditText.setText("Saving configuration failed! (no response)");
                    } else if (rsp[0] == 21) {
                        Toast.makeText(getActivity(), "Saving configuration failed! (nack)", Toast.LENGTH_SHORT).show();
                        //mEditText.setText("Saving configuration failed! (nack)");
                    } else if (rsp[0] == 6) {
                        if(nack_cnt==0)
                            Toast.makeText(getActivity(), "Config success!", Toast.LENGTH_SHORT).show();
                            //mEditText.setText("Config success!");
                        else
                        {
                            String txt = "Config success: (" + String.valueOf(succes_cnt)+" rows ) failed: ("+ String.valueOf(nack_cnt)+" rows)";
                            Toast.makeText(getActivity(), txt, Toast.LENGTH_SHORT).show();
                            //mEditText.setText("Config success: (" + String.valueOf(succes_cnt)+" rows ) failed: ("+ String.valueOf(nack_cnt)+" rows)");
                        }

                    } else {
                        Toast.makeText(getActivity(), "Saving configuration failed! ", Toast.LENGTH_SHORT).show();
                        //mEditText.setText("Saving configuration failed! " + String.valueOf(rsp[0]));
						/*
						for (int x = 0; x < arr.length; x++) {
							Log.e("0", "Line [" + String.valueOf(x) + "]=" + String.valueOf(arr[x]));
						}
						*/
                    }
                }
                catch (Exception ex)
                {
                    Log.e("0","IMG saveerr=" + ex.getMessage());
                    Toast.makeText(getActivity(), ex.getMessage(), Toast.LENGTH_SHORT).show();
                    //mEditText.setText(ex.getMessage());
                }

            }
            else
            {
                if(nack_cnt>0)
                    Toast.makeText(getActivity(), "Config failed! (Check your config string)", Toast.LENGTH_SHORT).show();
                    //mEditText.setText("Config failed! (Check your config string)");
                else
                    //mEditText.setText("Valid config string not found!");
                Toast.makeText(getActivity(), "Valid config string not found!", Toast.LENGTH_SHORT).show();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
