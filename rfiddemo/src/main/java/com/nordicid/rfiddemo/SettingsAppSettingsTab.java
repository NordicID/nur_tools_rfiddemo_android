package com.nordicid.rfiddemo;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.nordicid.nurapi.AntennaMapping;
import com.nordicid.nurapi.AutotuneSetup;
import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurApiException;
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
import com.nordicid.nurapi.NurPacket;
import com.nordicid.nurapi.NurRespDevCaps;
import com.nordicid.nurapi.NurRespRegionInfo;
import com.nordicid.nurapi.NurSetup;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class SettingsAppSettingsTab extends Fragment
{
	final private String TAG = "Settings";

	SettingsAppTabbed mOwner;
	NurApi mApi;

	private MultiSelectionSpinner mRegionSpinner;
	private Spinner mTxLevelSpinner;
	private TextView mRfProfileLabel;
	private Spinner mRfProfileSpinner;
	private TextView mLinkFreqLabel;
	private Spinner mLinkFreqSpinner;
	private TextView mRxDecodLabel;
	private Spinner mRxDecodSpinner;
	private TextView mTxModulLabel;
	private Spinner mTxModulSpinner;
	private Spinner mQSpinner;
	private Spinner mRoundSpinner;
	private Spinner mSessionSpinner;
	private Spinner mTargetSpinner;
	private Spinner mInventoryFilterMinSpinner;
	private Spinner mInventoryFilterMaxSpinner;
	private Spinner mReadFilterMinSpinner;
	private Spinner mReadFilterMaxSpinner;
	private Spinner mWriteFilterMinSpinner;
	private Spinner mWriteFilterMaxSpinner;
	private MultiSelectionSpinner mAntennaSpinner;
	private TextView mAutotuneLabel;
	private CheckBox mAutotuneCheckbox;

	private NurApiListener mThisClassListener = null;

	public NurApiListener getNurApiListener()
	{
		return mThisClassListener;
	}

	public SettingsAppSettingsTab() {
		mOwner = SettingsAppTabbed.getInstance();
		mApi = mOwner.getNurApi();

		mThisClassListener =  new NurApiListener() {
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
			@Override public void tagTrackingScanEvent(NurEventTagTrackingData event) { }
			@Override public void tagTrackingChangeEvent(NurEventTagTrackingChange event) { }
		};
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.tab_settings, container, false);
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

	private List<String> getTxLevelsFromDevice()
	{
		DecimalFormat df = new DecimalFormat("#.#");

		List <String> list = new ArrayList<String>();
		try {
			NurRespDevCaps caps = mApi.getDeviceCaps();
			for(int x=0;x<caps.txSteps;x++) {

				double dBm = caps.maxTxdBm - (x * caps.txAttnStep);
				//double mW = (double) Math.round(Math.pow(10, (double) dBm / 10));
				double mW = (double) Math.pow(10, (double) dBm / 10);
				list.add(df.format(mW) + " mW");
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return list;
	}

	ArrayAdapter<String> txLevelSpinnerAdapter;

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		mRegionSpinner = (MultiSelectionSpinner) view.findViewById(R.id.region_spinner);
		mRegionSpinner.setEnabled(false);
		mTxLevelSpinner = (Spinner) view.findViewById(R.id.txlevel_spinner);
		mTxLevelSpinner.setEnabled(false);
		mRfProfileSpinner = (Spinner) view.findViewById(R.id.rfprofile_spinner);
		mRfProfileSpinner.setEnabled(false);
		mRfProfileLabel = (TextView) view.findViewById(R.id.rfprofile_label);
		mLinkFreqSpinner = (Spinner) view.findViewById(R.id.linkfreq_spinner);
		mLinkFreqSpinner.setEnabled(false);
		mLinkFreqLabel = (TextView) view.findViewById(R.id.linkfreq_label);
		mRxDecodSpinner = (Spinner) view.findViewById(R.id.rxdecod_spinner);
		mRxDecodSpinner.setEnabled(false);
		mRxDecodLabel = (TextView) view.findViewById(R.id.rxdecod_label);
		mTxModulSpinner = (Spinner) view.findViewById(R.id.txmodul_spinner);
		mTxModulSpinner.setEnabled(false);
		mTxModulLabel = (TextView) view.findViewById(R.id.txmodul_label);
		mQSpinner = (Spinner) view.findViewById(R.id.q_spinner);
		mQSpinner.setEnabled(false);
		mRoundSpinner = (Spinner) view.findViewById(R.id.rounds_spinner);
		mRoundSpinner.setEnabled(false);
		mSessionSpinner = (Spinner) view.findViewById(R.id.session_spinner);
		mSessionSpinner.setEnabled(false);
		mTargetSpinner = (Spinner) view.findViewById(R.id.target_spinner);
		mTargetSpinner.setEnabled(false);

		mInventoryFilterMinSpinner = (Spinner) view.findViewById(R.id.invfilter_min_spinner);
		mInventoryFilterMaxSpinner = (Spinner) view.findViewById(R.id.invfilter_max_spinner);
		mReadFilterMinSpinner = (Spinner) view.findViewById(R.id.readfilter_min_spinner);
		mReadFilterMaxSpinner = (Spinner) view.findViewById(R.id.readfilter_max_spinner);
		mWriteFilterMinSpinner = (Spinner) view.findViewById(R.id.writefilter_min_spinner);
		mWriteFilterMaxSpinner = (Spinner) view.findViewById(R.id.writefilter_max_spinner);

		mInventoryFilterMinSpinner.setEnabled(false);
		mInventoryFilterMaxSpinner.setEnabled(false);
		mReadFilterMinSpinner.setEnabled(false);
		mReadFilterMaxSpinner.setEnabled(false);
		mWriteFilterMinSpinner.setEnabled(false);
		mWriteFilterMaxSpinner.setEnabled(false);

		mAntennaSpinner = (MultiSelectionSpinner) view.findViewById(R.id.antenna_spinner);
		mAntennaSpinner.setEnabled(false);
		mAutotuneCheckbox = (CheckBox)view.findViewById(R.id.autotune_checkbox);
		mAutotuneCheckbox.setEnabled(false);
		mAutotuneLabel = (TextView) view.findViewById(R.id.autotune_label);

		mRfProfileSpinner.setVisibility(View.GONE);
		mRfProfileLabel.setVisibility(View.GONE);

		mRegionSpinner.setMultiSel(false);
		mRegionSpinner.setItems(new String[] { "N/A" });
		mRegionSpinner.setOnItemSelectedListener(new OnItemSelectedListener()
		{
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				if (mApi.isConnected()) {
                    int currentRegion = 0;
					try {
						int newRegionId = mRegionSpinner.getSelectedIndices().get(0);
                        currentRegion = mApi.getSetupRegionId();
						mApi.setSetupRegionId(newRegionId);
						Log.d(TAG, "New region id: " + newRegionId);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (NurApiException e) {
						try {
							//Laastari, May crash because illegalArgument of currentRegion
							mRegionSpinner.setSelection(currentRegion);
							if (e.error == 5)
								Toast.makeText(Main.getInstance(), "Failed to set region, Device is region locked", Toast.LENGTH_SHORT).show();
							else
								storeError(e);
						} catch (Exception exep) {}
					} catch (Exception ex){
                        storeError(ex);
                    }
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		mTxLevelSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				if (mApi.isConnected()) {
					try {
						mApi.setSetupTxLevel(position);
						Log.d(TAG, "New tx level: " + position);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		ArrayAdapter<CharSequence> rfProfileSpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.rfprofile_entries, android.R.layout.simple_spinner_item);
		rfProfileSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mRfProfileSpinner.setAdapter(rfProfileSpinnerAdapter);
		mRfProfileSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				if (mApi.isConnected()) {
					try {
						//mApi.setSetupRfProfile(position);
						NurSetup setup = new NurSetup();
						setup.rfProfile = position;
						mApi.setModuleSetup(setup, NurApi.SETUP_RFPROFILE);
						Log.d(TAG, "New rfProfile: " + position);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		ArrayAdapter<CharSequence> linkFrequencySpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.link_frequency_entries, android.R.layout.simple_spinner_item);
		linkFrequencySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mLinkFreqSpinner.setAdapter(linkFrequencySpinnerAdapter);
		mLinkFreqSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				if (mApi.isConnected()) {
					try {
						switch (position) { // To set proper TX-level, we must use frequency integer in Hz
							case 0:
								mApi.setSetupLinkFreq(NurApi.LINK_FREQUENCY_160000);
								break;
							case 1:
								mApi.setSetupLinkFreq(NurApi.LINK_FREQUENCY_256000);
								break;
							case 2:
								mApi.setSetupLinkFreq(NurApi.LINK_FREQUENCY_320000);
								break;
							default:
								break;
						}
						Log.d(TAG, "New link freq: " + position);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		ArrayAdapter<CharSequence> rxDecodingSpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.rx_decoding_entries, android.R.layout.simple_spinner_item);
		rxDecodingSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mRxDecodSpinner.setAdapter(rxDecodingSpinnerAdapter);
		mRxDecodSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				if (mApi.isConnected()) {
					try {
						mApi.setSetupRxDecoding(position);
						Log.d(TAG, "New rx dec: " + position);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});


		ArrayAdapter<CharSequence> txModulationSpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.tx_modulation_entries, android.R.layout.simple_spinner_item);
		txModulationSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mTxModulSpinner.setAdapter(txModulationSpinnerAdapter);
		mTxModulSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				if (mApi.isConnected()) {
					try {
						mApi.setSetupTxModulation(position);
						Log.d(TAG, "New tx mod: " + position);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		ArrayAdapter<CharSequence> qSpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.q_entries, android.R.layout.simple_spinner_item);
		qSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mQSpinner.setAdapter(qSpinnerAdapter);
		mQSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				if (mApi.isConnected()) {
					try {
						mApi.setSetupInventoryQ(position);
						Log.d(TAG, "New inv Q: " + position);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		ArrayAdapter<CharSequence> roundsSpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.rounds_entries, android.R.layout.simple_spinner_item);
		roundsSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mRoundSpinner.setAdapter(roundsSpinnerAdapter);
		mRoundSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				if (mApi.isConnected()) {
					try {
						mApi.setSetupInventoryRounds(position);
						Log.d(TAG, "New inv rounds: " + position);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		ArrayAdapter<CharSequence> sessionSpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.session_entries, android.R.layout.simple_spinner_item);
		sessionSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSessionSpinner.setAdapter(sessionSpinnerAdapter);
		mSessionSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				if (mApi.isConnected()) {
					try {
						mApi.setSetupInventorySession(position);
						Log.d(TAG, "New inv session: " + position);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		ArrayAdapter<CharSequence> targetSpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.target_entries, android.R.layout.simple_spinner_item);
		targetSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mTargetSpinner.setAdapter(targetSpinnerAdapter);
		mTargetSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				if (mApi.isConnected()) {
					try {
						mApi.setSetupInventoryTarget(position);
						Log.d(TAG, "New inv target: " + position);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		ArrayAdapter<CharSequence> minRssiSpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.rssi_entries, android.R.layout.simple_spinner_item);
		minRssiSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mInventoryFilterMinSpinner.setAdapter(minRssiSpinnerAdapter);
		mInventoryFilterMinSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {

				int max = 0 - (mInventoryFilterMaxSpinner.getSelectedItemPosition()*2);
				int min = 0 - (position*2);

				if (mApi.isConnected()) {
					try {
						mApi.setSetupInventoryRssiFilter(min,max);
						Log.d(TAG, "New inv rssi filter: " + min + " - " + max);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						if(min>max) filterSetError(e);
						else storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		ArrayAdapter<CharSequence> maxRssiSpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.rssi_entries, android.R.layout.simple_spinner_item);
		maxRssiSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mInventoryFilterMaxSpinner.setAdapter(maxRssiSpinnerAdapter);
		mInventoryFilterMaxSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				int min = 0 - (mInventoryFilterMinSpinner.getSelectedItemPosition()*2);
				int max = 0 - (position*2);

				if (mApi.isConnected()) {
					try {
						mApi.setSetupInventoryRssiFilter(min,max);
						Log.d(TAG, "New inv rssi filter: " + min + " - " + max);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						if(min>max) filterSetError(e);
						else storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		ArrayAdapter<CharSequence> minReadSpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.rssi_entries, android.R.layout.simple_spinner_item);
		minReadSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mReadFilterMinSpinner.setAdapter(minReadSpinnerAdapter);
		mReadFilterMinSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {

				int max = 0 - (mReadFilterMaxSpinner.getSelectedItemPosition()*2);
				int min = 0 - (position*2);

				if (mApi.isConnected()) {
					try {
						mApi.setSetupReadRssiFilter(min,max);
						Log.d(TAG, "New read rssi filter: " + min + " - " + max);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						if(min>max) filterSetError(e);
						else storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		ArrayAdapter<CharSequence> maxReadSpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.rssi_entries, android.R.layout.simple_spinner_item);
		maxReadSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mReadFilterMaxSpinner.setAdapter(maxReadSpinnerAdapter);
		mReadFilterMaxSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				int min = 0 - (mReadFilterMinSpinner.getSelectedItemPosition()*2);
				int max = 0 - (position*2);

				if (mApi.isConnected()) {
					try {
						mApi.setSetupReadRssiFilter(min,max);
						Log.d(TAG, "New read rssi filter: " + min + " - " + max);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						if(min>max) filterSetError(e);
						else storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		ArrayAdapter<CharSequence> minWriteSpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.rssi_entries, android.R.layout.simple_spinner_item);
		minWriteSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mWriteFilterMinSpinner.setAdapter(minWriteSpinnerAdapter);
		mWriteFilterMinSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {

				int max = 0 - (mWriteFilterMaxSpinner.getSelectedItemPosition()*2);
				int min = 0 - (position*2);

				if (mApi.isConnected()) {
					try {
						mApi.setSetupWriteRssiFilter(min,max);
						Log.d(TAG, "New write rssi filter: " + min + " - " + max);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						if(min>max) filterSetError(e);
						else storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		ArrayAdapter<CharSequence> maxWriteSpinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.rssi_entries, android.R.layout.simple_spinner_item);
		maxWriteSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mWriteFilterMaxSpinner.setAdapter(maxWriteSpinnerAdapter);
		mWriteFilterMaxSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				int min = 0 - (mWriteFilterMinSpinner.getSelectedItemPosition()*2);
				int max = 0 - (position*2);

				if (mApi.isConnected()) {
					try {
						mApi.setSetupWriteRssiFilter(min,max);
						Log.d(TAG, "New write rssi filter: " + min + " - " + max);
						mApi.storeSetup(NurApi.STORE_RF);
					} catch (Exception e) {
						if(min>max) filterSetError(e);
						else storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		mAntennaSpinner.setItems(new String[] { "Antenna1","Antenna2","Antenna3","Antenna4" });
		mAntennaSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				if (mApi.isConnected()) {
					try {
						int antennaMaskEx = 0;
						for (int a : mAntennaSpinner.getSelectedIndices()) {
							antennaMaskEx |= (1<<a);
						}
						if (antennaMaskEx != 0) {
							Log.d(TAG, "New antenna mask: " + antennaMaskEx);
							mApi.setSetupAntennaMaskEx(antennaMaskEx);
							mApi.storeSetup(NurApi.STORE_RF);
						}
						else {
							Toast.makeText(mOwner.getActivity(), "At least one antenna must be selected", Toast.LENGTH_SHORT).show();
						}

					} catch (Exception e) {
						storeError(e);
					}
				}
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {  }
		});

		mAutotuneCheckbox.setOnCheckedChangeListener(mAutoTuneListener);

		storeListeners();
		enableListeners(false);
		/*enableItems(mApi.isConnected());
		if (mApi.isConnected()) {
			readCurrentSetup();
		}*/
	}

	@Override
	public void onResume() {
		super.onResume();

		enableItems(mApi.isConnected());
		if (mApi.isConnected()) {
			readCurrentSetup();
		} else {
			enableListeners(true);
		}
	}

	@Override
	public void onStop() {
		super.onStop();
	}

	OnCheckedChangeListener mAutoTuneListener = new OnCheckedChangeListener() {

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (mApi.isConnected()) {
				try {
					AutotuneSetup setup = new AutotuneSetup();
					setup.mode = isChecked ? (AutotuneSetup.ATMODE_EN|AutotuneSetup.ATMODE_THEN) : AutotuneSetup.ATMODE_OFF;
					setup.thresholddBm = -10;
					mApi.setSetupAutotune(setup);
					Log.d(TAG, "New autotune: " + isChecked);
					mApi.storeSetup(NurApi.STORE_RF);
				} catch (Exception e) {
					storeError(e);
				}
			}

		}
	};

	void storeError(Exception e)
	{
		Toast.makeText(getActivity(), "Problem occured while setting reader setup", Toast.LENGTH_SHORT).show();
		e.printStackTrace();
	}

	void filterSetError(Exception e)
	{
		Toast.makeText(getActivity(), "Warning! Make sure MIN is not higher than MAX", Toast.LENGTH_LONG).show();
		e.printStackTrace();
	}

	Runnable mFetchRegions = new Runnable() {
		@Override
		public void run() {
			try {
				List<String> regions = new ArrayList<>();
				try {
					Log.d(TAG, "Region count " + mApi.getReaderInfo().numRegions);
					for (int i = 0; i < mApi.getReaderInfo().numRegions; i++) {
						NurRespRegionInfo info = mApi.getRegionInfo(i);
						Log.d(TAG, "Region[" + i + "] = " + info.name);
						regions.add(info.name);
					}
					mRegionSpinner.setItems(regions);
					mRegionSpinner.setSelection(mApi.getModuleSetup().regionId);
					mRegionSpinner.setFetchContentResult(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};

	Object []listeners = new Object[19];

	private void storeListeners()
	{
		int idx = 0;
		listeners[idx++] = mRegionSpinner.getOnItemSelectedListener();
		listeners[idx++] = mTxLevelSpinner.getOnItemSelectedListener();
		listeners[idx++] = mRfProfileSpinner.getOnItemSelectedListener();
		listeners[idx++] = mLinkFreqSpinner.getOnItemSelectedListener();
		listeners[idx++] = mRxDecodSpinner.getOnItemSelectedListener();
		listeners[idx++] = mTxModulSpinner.getOnItemSelectedListener();
		listeners[idx++] = mQSpinner.getOnItemSelectedListener();
		listeners[idx++] = mRoundSpinner.getOnItemSelectedListener();
		listeners[idx++] = mSessionSpinner.getOnItemSelectedListener();
		listeners[idx++] = mTargetSpinner.getOnItemSelectedListener();
		listeners[idx++] = mAntennaSpinner.getOnItemSelectedListener();
		listeners[idx++] = mAutoTuneListener;
		listeners[idx++] = mInventoryFilterMinSpinner.getOnItemSelectedListener();
		listeners[idx++] = mInventoryFilterMaxSpinner.getOnItemSelectedListener();
		listeners[idx++] = mReadFilterMinSpinner.getOnItemSelectedListener();
		listeners[idx++] = mReadFilterMaxSpinner.getOnItemSelectedListener();
		listeners[idx++] = mWriteFilterMinSpinner.getOnItemSelectedListener();
		listeners[idx++] = mWriteFilterMaxSpinner.getOnItemSelectedListener();
	}

	private void enableListeners(boolean enable)
	{
		if (enable) {
			int idx = 0;
			mRegionSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mTxLevelSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mRfProfileSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mLinkFreqSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mRxDecodSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mTxModulSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mQSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mRoundSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mSessionSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mTargetSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mAntennaSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mAutotuneCheckbox.setOnCheckedChangeListener((OnCheckedChangeListener) listeners[idx++]);
			mInventoryFilterMinSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mInventoryFilterMaxSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mReadFilterMinSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mReadFilterMaxSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mWriteFilterMinSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
			mWriteFilterMaxSpinner.setOnItemSelectedListener((OnItemSelectedListener) listeners[idx++]);
		} else {
			mRegionSpinner.setOnItemSelectedListener(null);
			mTxLevelSpinner.setOnItemSelectedListener(null);
			mRfProfileSpinner.setOnItemSelectedListener(null);
			mLinkFreqSpinner.setOnItemSelectedListener(null);
			mRxDecodSpinner.setOnItemSelectedListener(null);
			mTxModulSpinner.setOnItemSelectedListener(null);
			mQSpinner.setOnItemSelectedListener(null);
			mRoundSpinner.setOnItemSelectedListener(null);
			mSessionSpinner.setOnItemSelectedListener(null);
			mTargetSpinner.setOnItemSelectedListener(null);
			mAntennaSpinner.setOnItemSelectedListener(null);
			mAutotuneCheckbox.setOnCheckedChangeListener(null);
			mInventoryFilterMinSpinner.setOnItemSelectedListener(null);
			mInventoryFilterMaxSpinner.setOnItemSelectedListener(null);
			mReadFilterMinSpinner.setOnItemSelectedListener(null);
			mReadFilterMaxSpinner.setOnItemSelectedListener(null);
			mWriteFilterMinSpinner.setOnItemSelectedListener(null);
			mWriteFilterMaxSpinner.setOnItemSelectedListener(null);
		}
	}

	private void readCurrentSetup() {

		enableListeners(false);

		Log.d(TAG, "readCurrentSetup");

		try {

			boolean hasRfProfile = mApi.getDeviceCaps().hasRfProfile();

			List<String> txLevels = getTxLevelsFromDevice();
		txLevelSpinnerAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, txLevels);
		txLevelSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mTxLevelSpinner.setAdapter(txLevelSpinnerAdapter);

			NurSetup setup = mApi.getModuleSetup();

			mRegionSpinner.setFetchContentRunnable(mFetchRegions);
			NurRespRegionInfo info = mApi.getRegionInfo();
			mRegionSpinner.setItems(Arrays.asList(info.name));

			int txLevelSetup = setup.txLevel;
			mTxLevelSpinner.setSelection(txLevelSetup);

			int linkFreqSetup = setup.linkFreq;

			// Make sure antenna autoswitch is enabled
			if (setup.selectedAntenna != NurApi.ANTENNAID_AUTOSELECT)
				mApi.setSetupSelectedAntenna(NurApi.ANTENNAID_AUTOSELECT);

			if (hasRfProfile)
			{
				// NUR2 device
				mRfProfileSpinner.setVisibility(View.VISIBLE);
				mRfProfileLabel.setVisibility(View.VISIBLE);

				mLinkFreqSpinner.setVisibility(View.GONE);
				mRxDecodSpinner.setVisibility(View.GONE);
				mTxModulSpinner.setVisibility(View.GONE);
				mAutotuneCheckbox.setVisibility(View.GONE);

				mLinkFreqLabel.setVisibility(View.GONE);
				mRxDecodLabel.setVisibility(View.GONE);
				mTxModulLabel.setVisibility(View.GONE);
				mAutotuneLabel.setVisibility(View.GONE);

				mRfProfileSpinner.setSelection(setup.rfProfile);

			} else {
				// NUR1 device
				mRfProfileSpinner.setVisibility(View.GONE);
				mRfProfileLabel.setVisibility(View.GONE);

				mLinkFreqSpinner.setVisibility(View.VISIBLE);
				mRxDecodSpinner.setVisibility(View.VISIBLE);
				mTxModulSpinner.setVisibility(View.VISIBLE);
				mAutotuneCheckbox.setVisibility(View.VISIBLE);

				mLinkFreqLabel.setVisibility(View.VISIBLE);
				mRxDecodLabel.setVisibility(View.VISIBLE);
				mTxModulLabel.setVisibility(View.VISIBLE);
				mAutotuneLabel.setVisibility(View.VISIBLE);

				switch (linkFreqSetup) {
					case NurApi.LINK_FREQUENCY_160000:
						mLinkFreqSpinner.setSelection(0);
						break;
					case NurApi.LINK_FREQUENCY_256000:
						mLinkFreqSpinner.setSelection(1);
						break;
					case NurApi.LINK_FREQUENCY_320000:
						mLinkFreqSpinner.setSelection(2);
						break;
					default:
						break;
				}

				int rxDecodSetup = setup.rxDecoding;
				mRxDecodSpinner.setSelection(rxDecodSetup);
				int txModulSetup = setup.txModulation;
				mTxModulSpinner.setSelection(txModulSetup);
			}

			int qSpinnerSetup = setup.inventoryQ;
			mQSpinner.setSelection(qSpinnerSetup, false);

			int roundsSetup = setup.inventoryRounds;
			mRoundSpinner.setSelection(roundsSetup);
			int sessionSetup = setup.inventorySession;
			mSessionSpinner.setSelection(sessionSetup);
			int targetSetup = setup.inventoryTarget;
			mTargetSpinner.setSelection(targetSetup);

            int min = Math.abs(setup.inventoryRssiFilter.min)/2;
            int max = Math.abs(setup.inventoryRssiFilter.max)/2;
			mInventoryFilterMaxSpinner.setSelection(max);
			mInventoryFilterMinSpinner.setSelection(min);

            min = Math.abs(setup.readRssiFilter.min)/2;
            max = Math.abs(setup.readRssiFilter.max)/2;
			mReadFilterMaxSpinner.setSelection(max);
			mReadFilterMinSpinner.setSelection(min);

            min = Math.abs(setup.writeRssiFilter.min)/2;
            max = Math.abs(setup.writeRssiFilter.max)/2;
			mWriteFilterMaxSpinner.setSelection(max);
			mWriteFilterMinSpinner.setSelection(min);

			String []antStrings;
			AntennaMapping []mapping = mApi.getAntennaMapping();
			if (mapping != null && mapping.length > 0)
			{
				antStrings = new String[mapping.length];
				for (int n=0; n<mapping.length; n++)
					antStrings[n] = mapping[n].name;
			} else {
				// Defaults
				antStrings = new String[4];
				for (int n=0; n<4; n++)
					antStrings[n] = "Antenna"+n;
			}
			mAntennaSpinner.setItems(antStrings);

			// Set selection
			int antennaMaskEx = setup.antennaMaskEx;
			ArrayList <Integer> selInd = new ArrayList<Integer>();
			for (int n=0; n<32; n++)
			{
				if ((antennaMaskEx & (1<<n)) != 0) {
					selInd.add(n);
				}
			}
			mAntennaSpinner.setSelectionI(selInd);

			mAutotuneCheckbox.setChecked(setup.autotune.mode != 0);

		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(getActivity(), "Problem occured while retrieving readers setup", Toast.LENGTH_SHORT).show();
		}

		mTargetSpinner.post(new Runnable() {
		    @Override
			public void run() {
				Log.d(TAG, "readCurrentSetup SET LIST");
				enableListeners(true);
				Log.d(TAG, "readCurrentSetup SET LIST DONE");
		    }
		});

		Log.d(TAG, "readCurrentSetup DONE");
	}

	private void enableItems(boolean v) {
		mRegionSpinner.setEnabled(v);
		mTxLevelSpinner.setEnabled(v);
		mLinkFreqSpinner.setEnabled(v);
		mRxDecodSpinner.setEnabled(v);
		mTxModulSpinner.setEnabled(v);
		mQSpinner.setEnabled(v);
		mRoundSpinner.setEnabled(v);
		mSessionSpinner.setEnabled(v);
		mTargetSpinner.setEnabled(v);
		mAntennaSpinner.setEnabled(v);
		mAutotuneCheckbox.setEnabled(v);
        mRfProfileSpinner.setEnabled(v);
        mInventoryFilterMinSpinner.setEnabled(v);
        mInventoryFilterMaxSpinner.setEnabled(v);
		mReadFilterMinSpinner.setEnabled(v);
		mReadFilterMaxSpinner.setEnabled(v);
		mWriteFilterMinSpinner.setEnabled(v);
		mWriteFilterMaxSpinner.setEnabled(v);
	}
}
