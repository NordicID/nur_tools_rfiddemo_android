package com.nordicid.rfiddemo;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nordicid.apptemplate.AppTemplate;
import com.nordicid.nuraccessory.NurAccessoryConfig;
import com.nordicid.nuraccessory.NurAccessoryExtension;
import com.nordicid.nuraccessory.NurAccessoryVersionInfo;
import com.nordicid.nurapi.ACC_WIRELESS_CHARGE_STATUS;
import com.nordicid.nurapi.AccConfig;
import com.nordicid.nurapi.AccVersionInfo;
import com.nordicid.nurapi.AccessoryExtension;
import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurApiAutoConnectTransport;
import com.nordicid.nurapi.NurApiBLEAutoConnect;
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
import com.nordicid.nurapi.NurSmartPairSupport;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Set;

public class SettingsAppHidTab extends Fragment {
	SettingsAppTabbed mOwner;

	final private String TAG = "HIDTAB";

	NurApi mApi;
	AccessoryExtension mExt;
	BluetoothDevice device=null;

	boolean mHasWirelessCharging = false;
	boolean mBondFound = false;
	
	CheckBox mHidBarcodeCheckBox;
	CheckBox mHidRFIDCheckBox;
	CheckBox mWirelessChargingCheckBox;
    CheckBox mAllowPairingCheckBox;

    LinearLayout mSpLayout;
	CheckBox mSpShowUiCheckBox;
	CheckBox mSpAutodisconExa51CheckBox;
    CheckBox mSpSensitivityCheckBox;
	TextView mWirelessChargingLabel;

	Button mButtonBond;
	Button mButtonRebootDevice;
	
	private NurApiListener mThisClassListener = null;
	
	public NurApiListener getNurApiListener()
	{
		return mThisClassListener;
	}
	
	public SettingsAppHidTab() {
		mOwner = SettingsAppTabbed.getInstance();
		mApi = mOwner.getNurApi();
		mExt = AppTemplate.getAppTemplate().getAccessoryApi();
		
		mThisClassListener = new NurApiListener() {
			@Override
			public void connectedEvent() {
				if (isAdded()) {
					enableItems(true);
					readCurrentSetup();
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
				if (mApi.isConnected()) {
					readCurrentSetup();
				}
			}
		}
	}

	
	void iterateBluetoothDevices() {
		mBondFound=false;
		try {
			BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
			NurApiAutoConnectTransport mTr = Main.getInstance().getAutoConnectTransport();
			if (btAdapter != null) {
				device = btAdapter.getRemoteDevice(mTr.getAddress());
				Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
				Log.i(TAG, String.format("found %d bluetooth devices", (pairedDevices != null) ? pairedDevices.size() : 0));
				if (pairedDevices.size() > 0) {
					for (BluetoothDevice d : pairedDevices) {

						if (mTr.getAddress().equals(d.getAddress())) {
							//Bonded device found
							mBondFound = true;
							String btID = String.format("bluetooth device [%s]  type: %s BondState=%d Addr=%s thisAddr=%s", d.getName(), d.getType(), d.getBondState(), d.getAddress(), mTr.getAddress());
							Log.i(TAG, btID);
							btID = String.format("bluetoothDevice [%s]", device.getAddress());
							Log.i(TAG, btID);
							break;
						}
						//Toast.makeText(this, btID, Toast.LENGTH_SHORT).show();
					}
				}
			}
		}catch (Exception ex) {
			Log.e(TAG, ex.getMessage());
		}
	}

	private void pairDevice(BluetoothDevice device) {
		try {

			Log.i(TAG, "Pairing...");

			//waitingForBonding = true;

			Method m = device.getClass().getMethod("createBond", (Class[]) null);
			m.invoke(device, (Object[]) null);

			Log.i(TAG, "Pairing done.");
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	private void unpairDevice(BluetoothDevice device) {
		try {
			Method m = device.getClass().getMethod("removeBond", (Class[]) null);
			m.invoke(device, (Object[]) null);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	private void enableItems(boolean v) {
		mHidBarcodeCheckBox.setEnabled(v);
		mHidRFIDCheckBox.setEnabled(v);
		mButtonBond.setEnabled(v);
		mButtonRebootDevice.setEnabled(v);
		mWirelessChargingCheckBox.setEnabled(v);
		mAllowPairingCheckBox.setEnabled(v);
	}

	private String removeSpecificChars(String originalstring, String removecharacterstring)
	{
		char[] orgchararray=originalstring.toCharArray();
		char[] removechararray=removecharacterstring.toCharArray();
		int start,end=0;

		//tempBoolean automatically initialized to false ,size 128 assumes ASCII
		boolean[]  tempBoolean = new boolean[128];

		//Set flags for the character to be removed
		for(start=0;start < removechararray.length;++start)
		{
			tempBoolean[removechararray[start]]=true;
		}

		//loop through all characters ,copying only if they are flagged to false
		for(start=0;start < orgchararray.length;++start)
		{
			if(!tempBoolean[orgchararray[start]])
			{
				orgchararray[end++]=orgchararray[start];
			}
		}


		return new String(orgchararray,0,end);
	}

	private void enableListeners(boolean enable)
	{
		if (enable) {
			mHidBarcodeCheckBox.setOnCheckedChangeListener(mOnCheckedChangeListener);
			mHidRFIDCheckBox.setOnCheckedChangeListener(mOnCheckedChangeListener);
			mWirelessChargingCheckBox.setOnCheckedChangeListener(mWirelessChargingChangeListener);
			mAllowPairingCheckBox.setOnCheckedChangeListener(mAllowPairingChangeListener);
			mSpShowUiCheckBox.setOnCheckedChangeListener(mSpShowUiChangeListener);
			mSpAutodisconExa51CheckBox.setOnCheckedChangeListener(mSpAutodisconExa51ChangeListener);
			mSpSensitivityCheckBox.setOnCheckedChangeListener(mSpSensitivityChangeListener);
		} else {
			mHidBarcodeCheckBox.setOnCheckedChangeListener(null);
			mHidRFIDCheckBox.setOnCheckedChangeListener(null);
			mWirelessChargingCheckBox.setOnCheckedChangeListener(null);
			mAllowPairingCheckBox.setOnCheckedChangeListener(null);
			mSpShowUiCheckBox.setOnCheckedChangeListener(null);
			mSpAutodisconExa51CheckBox.setOnCheckedChangeListener(null);
			mSpSensitivityCheckBox.setOnCheckedChangeListener(null);
		}
	}

	private void readCurrentSetup() {
		enableListeners(false);

		if (mApi.isConnected() && AppTemplate.getAppTemplate().getAccessorySupported())
		{
			AccConfig cfg;
			try {
				enableItems(true);
				cfg = mExt.getConfig();
				AccVersionInfo info = mExt.getFwVersion();
				String appver = removeSpecificChars(info.getApplicationVersion(), ".");

				int ver = Integer.parseInt(appver);
				if (ver >= 221 || cfg.isDeviceEXA81()) {
					mAllowPairingCheckBox.setEnabled(true);
				} else {
					mAllowPairingCheckBox.setEnabled(false);
				}

				// Disable HID from devices w/ integrated reader module
				String addr = Main.getInstance().getNurAutoConnect().getAddress();
				if (addr != null && addr.equals("integrated_reader")) {
					mHidBarcodeCheckBox.setEnabled(false);
					mHidRFIDCheckBox.setEnabled(false);
					mButtonBond.setEnabled(false);
					mAllowPairingCheckBox.setEnabled(false);
				}

				if(cfg.isDeviceEXA51()) {
					mSpAutodisconExa51CheckBox.setEnabled(true);
				}
				else mSpAutodisconExa51CheckBox.setEnabled(false);

				if((cfg.hasImagerScanner() && cfg.getAllowPairingState()) || cfg.isDeviceEXA81())
					mHidBarcodeCheckBox.setChecked(cfg.getHidBarCode());
				else mHidBarcodeCheckBox.setEnabled(false);

				mHidRFIDCheckBox.setChecked(cfg.getHidRFID());
				if(cfg.isDeviceEXA21())
					mHidRFIDCheckBox.setEnabled(true); //USB is possible even if not BLE paired

				if(cfg.getAllowPairingState())
				{
					mButtonBond.setEnabled(true);

					if(mBondFound){
						mButtonBond.setText("Unpair");
						mAllowPairingCheckBox.setEnabled(false);
					}
					else {
						mButtonBond.setText("Pair");
						mAllowPairingCheckBox.setEnabled(true);
					}
				}
				else {
					mButtonBond.setEnabled(false);
					mBondFound=false;
				}

				mWirelessChargingCheckBox.setEnabled(cfg.hasWirelessCharging());
				mAllowPairingCheckBox.setChecked(cfg.getAllowPairingState());
			}
			catch (Exception e) {
				e.printStackTrace();
				enableItems(false);
			}

		} else {
			enableItems(false);
		}

		// Setup smart pair settings
		SharedPreferences pref = Main.getApplicationPrefences();
		mSpShowUiCheckBox.setChecked(pref.getBoolean("SmartPairShowUi", true));

		try {
			JSONObject settings = new JSONObject(pref.getString("SmartPairSettings", "{}"));

			try {
				// JSON: {"AutoDisconnectMap":{"EXA51":<true|false>}}
				mSpAutodisconExa51CheckBox.setChecked(settings.getJSONObject("AutoDisconnectMap").getBoolean("EXA51"));
			} catch (Exception ex) { }

			try {
				// JSON: {"ConnThresholdAdjust":<0|-10>}
				mSpSensitivityCheckBox.setChecked(settings.getInt("ConnThresholdAdjust") == 0 ? false : true);
			} catch (Exception ex) { }

		} catch (JSONException e) {
			e.printStackTrace();
		}

		// Enable listeners
		mHidBarcodeCheckBox.post(new Runnable() {
			@Override
			public void run() {
				enableListeners(true);
			}
		});
	}
	
	void setNewHidConfig()
	{
		try {
			AccConfig cfg = mExt.getConfig();
			cfg.setHidBarcode(mHidBarcodeCheckBox.isChecked());
			cfg.setHidRFID(mHidRFIDCheckBox.isChecked());

			//if(mHidRFIDCheckBox.isChecked()) {
				mButtonRebootDevice.setVisibility(View.VISIBLE); //Allow user
				//cfg.setAllowPairingState(true);
				//mAllowPairingCheckBox.setChecked(true);
			//}

			mExt.setConfig(cfg);
			readCurrentSetup();
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(AppTemplate.getAppTemplate(),"Operation failed", Toast.LENGTH_SHORT).show();
		}
	}

	void setNewAllowPairingConfig()
	{
		try {
			AccConfig cfg = mExt.getConfig();
			cfg.setAllowPairingState(mAllowPairingCheckBox.isChecked());
			mExt.setConfig(cfg);
			readCurrentSetup();

			if(cfg.getAllowPairingState()==false && mBondFound) {
				//device paired and now user want to disable pairing.
				//Unpair and give reset
				mExt.clearPairingData();
				unpairDevice(device);
				Toast.makeText(AppTemplate.getAppTemplate(),"Unpairing and rebooting device..", Toast.LENGTH_SHORT).show();
				mBondFound=false;
			}
			else {
				Toast.makeText(AppTemplate.getAppTemplate(), "Rebooting device..", Toast.LENGTH_SHORT).show();
			}

			mExt.restartBLEModule();
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(AppTemplate.getAppTemplate(),"Operation failed", Toast.LENGTH_SHORT).show();
		}
	}

	void storeSmartPairSettings()
	{
		SharedPreferences.Editor editor = Main.getApplicationPrefences().edit();
		String settingsStr = NurSmartPairSupport.getSettingsString();
		editor.putString("SmartPairSettings", settingsStr);
		editor.apply();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.tab_settings_hid, container, false);
	}
	
	OnCheckedChangeListener mOnCheckedChangeListener = new OnCheckedChangeListener() {		
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			setNewHidConfig();
		}
	};

	OnCheckedChangeListener mAllowPairingChangeListener = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
		{
			setNewAllowPairingConfig();
		}
	};

	OnCheckedChangeListener mWirelessChargingChangeListener = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			try {
				String msg;
				ACC_WIRELESS_CHARGE_STATUS result = mExt.setWirelessCharge(mWirelessChargingCheckBox.isChecked());
				Log.d("SetWirelessCharging","result " + result);
				switch (result)
				{
					case Fail:
					case Refused:
						msg = "Failed to set wireless charging value";
						break;
					case NotSupported:
						msg = "Wireless Charging not supported";
						break;
					default:
						msg = "Wireless charging turned " + ((result == ACC_WIRELESS_CHARGE_STATUS.On) ? "On" : "Off");
						break;
				}
				mWirelessChargingCheckBox.setOnCheckedChangeListener(null);

				if(mExt.getWirelessChargeStatus() == ACC_WIRELESS_CHARGE_STATUS.On)
					mWirelessChargingCheckBox.setChecked(true);
				else mWirelessChargingCheckBox.setChecked(false);
				Toast.makeText(AppTemplate.getAppTemplate(),msg, Toast.LENGTH_SHORT).show();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				Toast.makeText(AppTemplate.getAppTemplate(),"Operation failed", Toast.LENGTH_SHORT).show();
			}
			mWirelessChargingCheckBox.setOnCheckedChangeListener(mWirelessChargingChangeListener);
		}
	};

	OnCheckedChangeListener mSpShowUiChangeListener = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
		{
			SharedPreferences.Editor editor = Main.getApplicationPrefences().edit();
			editor.putBoolean("SmartPairShowUi", isChecked);
			editor.apply();
		}
	};

	OnCheckedChangeListener mSpAutodisconExa51ChangeListener = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
		{
			try {
				// JSON: {"AutoDisconnectMap":{"EXA51":<true|false>}}
				JSONObject setting = new JSONObject().put("AutoDisconnectMap", new JSONObject().put("EXA51", isChecked));
				NurSmartPairSupport.setSettings(setting);

				storeSmartPairSettings();
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	};

    OnCheckedChangeListener mSpSensitivityChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
        	try {
				// JSON: {"ConnThresholdAdjust":<rssiAdjust>}
				int rssiAdjust = isChecked ? -10 : 0;
				JSONObject setting = new JSONObject().put("ConnThresholdAdjust", rssiAdjust);
				NurSmartPairSupport.setSettings(setting);

				storeSmartPairSettings();
			} catch (Exception e)
			{
				e.printStackTrace();
			}
        }
    };


    @Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		mHidBarcodeCheckBox = (CheckBox)view.findViewById(R.id.hid_barcode_checkbox);
		mHidRFIDCheckBox = (CheckBox)view.findViewById(R.id.hid_rfid_checkbox);
		mWirelessChargingCheckBox = (CheckBox)view.findViewById(R.id.hid_wireless_charging_checkbox);
		mAllowPairingCheckBox = (CheckBox)view.findViewById(R.id.allow_pairing_checkbox);
        mWirelessChargingLabel = (TextView)view.findViewById(R.id.hid_wireless_charging_label);
        mButtonBond = (Button)view.findViewById(R.id.buttonBond);

        mButtonRebootDevice = (Button)view.findViewById(R.id.buttonReboot);
		mButtonRebootDevice.setVisibility(View.INVISIBLE);

        mSpLayout = (LinearLayout) view.findViewById(R.id.sp_layout);
		mSpShowUiCheckBox = (CheckBox) view.findViewById(R.id.sp_showui);
        mSpAutodisconExa51CheckBox = (CheckBox) view.findViewById(R.id.sp_autodiscon_exa51);
        mSpSensitivityCheckBox = (CheckBox) view.findViewById(R.id.sp_sensitivity);

        if (!NurSmartPairSupport.isSupported())
            mSpLayout.setVisibility(View.GONE);

        iterateBluetoothDevices();

        mButtonRebootDevice.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					Toast.makeText(AppTemplate.getAppTemplate(),"Rebooting device..", Toast.LENGTH_SHORT).show();
					mExt.restartBLEModule();
					mButtonRebootDevice.setVisibility(View.INVISIBLE);
				}
				catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(AppTemplate.getAppTemplate(),"Operation failed", Toast.LENGTH_SHORT).show();
				}
			}
		});

        mButtonBond.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(mBondFound) {
					//Need to Unpair
					try {
						mExt.clearPairingData();
						unpairDevice(device);
						Toast.makeText(AppTemplate.getAppTemplate(),"Unpair success. Rebooting device..", Toast.LENGTH_LONG).show();
						mBondFound=false;
						mButtonBond.setText("Pair");
					}catch (Exception e) {
						e.printStackTrace();
						Toast.makeText(AppTemplate.getAppTemplate(),"Unpair failed:" + e.getMessage(), Toast.LENGTH_LONG).show();
					}
				}
				else {
					//Try to pair device
					if(device!=null) {
						try {
							//mExt.clearPairingData();
							AccConfig cfg=mExt.getConfig();
							pairDevice(device);

							if(!cfg.isDeviceEXA21() && !cfg.isDeviceEXA81()) {
								Thread.sleep(5000);

								iterateBluetoothDevices();
								if (mBondFound) {
									Toast.makeText(AppTemplate.getAppTemplate(), "Pairing success", Toast.LENGTH_LONG).show();
									mButtonBond.setText("Unpair");
								} else
									Toast.makeText(AppTemplate.getAppTemplate(), "Pairing failed", Toast.LENGTH_LONG).show();
							}
							//mExt.restartBLEModule();
						}catch (Exception e) {
							e.printStackTrace();
							Toast.makeText(AppTemplate.getAppTemplate(),"Pairing failed:" + e.getMessage(), Toast.LENGTH_LONG).show();
						}
					}
				}
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.i(TAG,"Resume");
		iterateBluetoothDevices();
		readCurrentSetup();
	}
}
