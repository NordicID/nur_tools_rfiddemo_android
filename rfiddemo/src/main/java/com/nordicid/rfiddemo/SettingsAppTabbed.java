package com.nordicid.rfiddemo;

import java.util.ArrayList;

import com.nordicid.apptemplate.SubAppTabbed;
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

import android.app.Activity;
import androidx.fragment.app.Fragment;
import android.util.Log;

public class SettingsAppTabbed extends SubAppTabbed {
	private static String mPreferredTab = "";
	final private String TAG = "MAIN";

	private SettingsAppSettingsTab mSettingsTab;
	private SettingsAppTuneTab mSettingsTuneTab;
	private SettingsAppHidTab mReaderSettings;
	private static SettingsAppImagerTab mImagerSettingsTab;
	private static SettingsAppInventoryTab mInventorySettingsTab;
	private static SettingsAppSensorTab mSensorSettingsTab;
	private SettingsAppAuthTab mSettingsAuthTab;
    private SettingsAppTab mSettingsAppTab;
    private SettingsAppUpdatesTab mSettingsUpdateTab;

	private NurApiListener mThisClassListener = null;

	private static SettingsAppTabbed gInstance = null;
	public static SettingsAppTabbed getInstance()
	{
		return gInstance;
	}

	public static void setPreferredTab(String preferredTab)
	{
		mPreferredTab = preferredTab;
	}

	@Override
	protected String onGetPreferredTab()
	{
		String preferredTab = mPreferredTab;
		mPreferredTab = "";
		Log.w(TAG,"onGetPreferredTab:" + preferredTab );
		return preferredTab;
	}

	@Override
    public void onAttach(Activity context){
        super.onAttach(context);
    }

    @Override
	public NurApiListener getNurApiListener()
	{
		return mThisClassListener;
	}
	
	public SettingsAppTabbed() {
		super();
		gInstance = this;
		
		mSettingsTab = new SettingsAppSettingsTab();
		mSettingsTuneTab = new SettingsAppTuneTab();
		mReaderSettings = new SettingsAppHidTab();
		mImagerSettingsTab = new SettingsAppImagerTab();
		mInventorySettingsTab = new SettingsAppInventoryTab();
		mSensorSettingsTab = new SettingsAppSensorTab();
		mSettingsAuthTab = new SettingsAppAuthTab();
        mSettingsAppTab = new SettingsAppTab();
        mSettingsUpdateTab = new SettingsAppUpdatesTab();
		
		mThisClassListener =  new NurApiListener() {
			@Override
			public void connectedEvent() {
				if (isAdded()) {
					mSettingsTab.getNurApiListener().connectedEvent();
					mReaderSettings.getNurApiListener().connectedEvent();
					mImagerSettingsTab.getNurApiListener().connectedEvent();
					mInventorySettingsTab.getNurApiListener().connectedEvent();
					mSensorSettingsTab.getNurApiListener().connectedEvent();
					mSettingsUpdateTab.showAvailUpdatesStatus();
				}
			}

			@Override
			public void disconnectedEvent() {
				if (isAdded()) {
					mSettingsTab.getNurApiListener().disconnectedEvent();
					mReaderSettings.getNurApiListener().disconnectedEvent();
					mImagerSettingsTab.getNurApiListener().disconnectedEvent();
					mInventorySettingsTab.getNurApiListener().disconnectedEvent();
					mSensorSettingsTab.getNurApiListener().disconnectedEvent();
					mSettingsUpdateTab.showAvailUpdatesStatus();
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
			@Override public void tagTrackingScanEvent(NurEventTagTrackingData event) {}
			@Override public void tagTrackingChangeEvent(NurEventTagTrackingChange event) {}
		};
	}

	@Override
	protected int onGetFragments(ArrayList<Fragment> fragments, ArrayList<String> fragmentNames) throws Exception
	{
        fragmentNames.add(getString(R.string.app_settings));
        fragments.add(mSettingsAppTab);

		if(Main.isBarcodeOnly() == false) {
			fragmentNames.add(getString(R.string.rfid_settings));
			fragments.add(mSettingsTab);
		}

		fragmentNames.add(getString(R.string.reader_settings));
		fragments.add(mReaderSettings);

		if(Main.isImager()) {
			fragmentNames.add(getString(R.string.imager_settings));
			fragments.add(mImagerSettingsTab);
		}

		if(Main.isBarcodeOnly() == false) {
			fragmentNames.add("Inventory");
			fragments.add(mInventorySettingsTab);
		}

		if(Main.isSensors()) {
			fragmentNames.add("Sensors");
			fragments.add(mSensorSettingsTab);
		}

		if(Main.isBarcodeOnly() == false) {
			fragmentNames.add(getString(R.string.antenna_settings));
			fragments.add(mSettingsTuneTab);
		}


		/** Application Disabled for now **/
		/*
		fragmentNames.add("Authentication");
		fragments.add(mSettingsAuthTab);
		*/

		if(Main.isBarcodeOnly() == false || Main.isApplicationMode() == false) {
			fragmentNames.add(getString(R.string.firmware_updates));
			fragments.add(mSettingsUpdateTab);
			//mSettingsUpdateTab.showAvailUpdatesStatus();
		}

		return R.id.pager;
	}

	@Override
	public void onVisibility(boolean val)
	{
		mSettingsTab.onVisibility(val);
		mReaderSettings.onVisibility(val);
		mImagerSettingsTab.onVisibility(val);
		mInventorySettingsTab.onVisibility(val);
		mSensorSettingsTab.onVisibility(val);
		Log.w(TAG,"onVisibilityEx:" + val );
	}
	
	//main layout
	@Override
	public int getLayout() {
		return R.layout.app_settings_tabbed;
	}

	@Override
	public String getAppName() {
		return "Settings";
	}

	@Override
	public int getTileIcon() {
		return R.drawable.ic_settings;
	}
}
