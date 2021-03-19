package com.nordicid.rfiddemo;

import java.util.HashMap;

import com.nordicid.apptemplate.SubApp;
import com.nordicid.controllers.InventoryController;
import com.nordicid.controllers.InventoryController.InventoryControllerListener;
import com.nordicid.nuraccessory.NurAccessoryExtension;
import com.nordicid.nurapi.ACC_SENSOR_SOURCE;
import com.nordicid.nurapi.NurApiListener;
import com.nordicid.nurapi.NurEventIOChange;
import com.nordicid.nurapi.NurTag;
import com.nordicid.nurapi.NurTagStorage;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class InventoryApp extends SubApp {

	private final int REQUEST_CODE_OPEN_DIRECTORY = 44;

    private TextView mInventoryCountTextView;
    private TextView mInventoryTagsInTime;
    private TextView mInventoryMaxTagsPerSecond;
    private TextView mInventoryAvgTagPerSecond;
    private TextView mInventoryTagsPerSecond;
	private ListView mFoundTagsListView;
	private SimpleAdapter mFoundTagsListViewAdapter;
	private Button mStartStopInventory;
	private View mView;

	long mLastUpdateTagCount = 0;
	long mLastExportedCount = 0;

	Handler mHandler;

	private InventoryController mInventoryController;

	@Override
	public NurApiListener getNurApiListener()
	{		
		return mInventoryController.getNurApiListener();	
	}

	public InventoryApp() {
		super();
		mHandler = new Handler(Looper.getMainLooper());
		mInventoryController = new InventoryController(getNurApi());
	}

	@Override
	public int getTileIcon() {
		return R.drawable.ic_inventory;
	}
	
	@Override
	public String getAppName() {
		return "Inventory";
	}
	
	@Override
	public int getLayout() {
		return R.layout.app_inventory;
	}
	
	Runnable mTimeUpdate = new Runnable() {
		@Override
		public void run() {

			updateStats(mInventoryController);
			
			if (mLastUpdateTagCount != mInventoryController.getTagStorage().size())
				mFoundTagsListViewAdapter.notifyDataSetChanged();
			
			mLastUpdateTagCount = mInventoryController.getTagStorage().size();
			
			if (mInventoryController.isInventoryRunning())
				mHandler.postDelayed(mTimeUpdate, 250);				
		}
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        mInventoryController.setListener(new InventoryControllerListener() {
			@SuppressWarnings("unchecked")
			@Override
			public void tagFound(NurTag tag, boolean isNew) {
				mFoundTagsListViewAdapter.notifyDataSetChanged();
				if (isNew) {
					//mFoundTagsListViewAdapter.notifyDataSetChanged();
					mLastUpdateTagCount = mInventoryController.getTagStorage().size();
				}
			}

			@Override
			public void inventoryRoundDone(NurTagStorage storage, int newTagsOffset, int newTagsAdded) { }

			@Override
			public void readerDisconnected() {
				mStartStopInventory.setText(getString(R.string.start));
			}

			@Override
			public void readerConnected() { }

			@Override
			public void inventoryStateChanged() {
				if (mInventoryController.isInventoryRunning()) {
					keepScreenOn(true);
					mStartStopInventory.setText(getString(R.string.stop));
					//clearReadings();
					mHandler.postDelayed(mTimeUpdate, 250);
				}
				else {
					keepScreenOn(false);
					mStartStopInventory.setText(getString(R.string.start));
				}
			}

			@Override
			public void IOChangeEvent(NurEventIOChange event) {
				// Handle BLE trigger

				if (event.source == NurAccessoryExtension.TRIGGER_SOURCE && event.direction == 1)
				{
					//Trigger down
					if (!mInventoryController.isInventoryRunning())
						startInventory();
					else stopInventory();
				}
				if (event.source == NurAccessoryExtension.TRIGGER_SOURCE && event.direction == 0)
				{
					//Trigger released
					if (mInventoryController.isInventoryRunning()) {

						if(mInventoryController.mTriggerDown)
							stopInventory();
					}
				}
				else if(event.source == ACC_SENSOR_SOURCE.ToFSensor.getNumVal()) {
					//ToF sensor of EXA21 has triggered GPIO event
					// Direction goes 0->1 when sensors reads less than Range Lo filter (unit: mm)
					// Direction goes 1->0 when sensors reads more than Range Hi filter (unit: mm)
					if(event.direction == 1) {
						//There are something front of EXA21 ToF sensor, let's start inventory
						startInventory();
					}
					else {
						//Nothing seen at front of ToF sensor. It's time to stop inventory.
						stopInventory();
					}
				}
			}

		});
	}

    public void updateStats(InventoryController invCtl)
	{
		InventoryController.Stats stats = invCtl.getStats();

		mInventoryTagsInTime.setText(String.format("%.1f", stats.getTagsFoundInTimeSecs()));
		mInventoryTagsPerSecond.setText(String.format("%.1f", stats.getTagsPerSec()));
		mInventoryCountTextView.setText(Long.toString(invCtl.getTagStorage().size()));
        mInventoryMaxTagsPerSecond.setText(String.format("%.1f", stats.getMaxTagsPerSec()));
        mInventoryAvgTagPerSecond.setText(String.format("%.1f", stats.getAvgTagsPerSec()));
    }
	
	private void clearReadings() {
		mInventoryController.clearInventoryReadings();
		mFoundTagsListViewAdapter.notifyDataSetChanged();
		mLastUpdateTagCount = 0;
		mLastExportedCount = 0;
		updateStats(mInventoryController);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		mView = view;

		//Start/stop button
		mStartStopInventory = addButtonBarButton(getString(R.string.start), new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mInventoryController.isInventoryRunning()) {
					stopInventory();
				}
				else {
					startInventory();
				}
			}
		});
		
		if (mInventoryController.isInventoryRunning()) {
			mStartStopInventory.setText(getString(R.string.stop));
		}
			
		// Clear button and alertdialog

		addButtonBarButton(getString(R.string.clear), new OnClickListener(){
			@Override
			public void onClick(View v) {
				clearReadings();
			}
		});


		addButtonBarButton(getString(R.string.export), new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mInventoryController.isInventoryRunning()) {
					Toast.makeText(getActivity(), getString(R.string.export_stop_first), Toast.LENGTH_SHORT).show();
					return;
				}

				mLastUpdateTagCount = mInventoryController.getTagStorage().size();

				if(mLastUpdateTagCount == 0) {
					Toast.makeText(getActivity(), getString(R.string.export_nothing), Toast.LENGTH_SHORT).show();
					return;
				}

				if(mLastUpdateTagCount == mLastExportedCount) {
					Toast.makeText(getActivity(), getString(R.string.export_already), Toast.LENGTH_SHORT).show();
					return;
				}

				String ret=mInventoryController.Export(getActivity().getApplicationContext());
				if(!ret.isEmpty()) {
					Toast.makeText(getActivity(), "Error occurred!: " + ret, Toast.LENGTH_LONG).show();
					selectFolder();
					return;
				}
				else {
					Toast.makeText(getActivity(), getString(R.string.export_success), Toast.LENGTH_LONG).show();
				}

				mLastExportedCount = mLastUpdateTagCount;
			}
		});


		// statistics UI
        mInventoryCountTextView = (TextView) mView.findViewById(R.id.num_of_tags_textview);
        mInventoryAvgTagPerSecond = (TextView) mView.findViewById(R.id.average_tags_per_second_textview);
        mInventoryTagsInTime = (TextView) mView.findViewById(R.id.tags_in_time_textview);
        mInventoryMaxTagsPerSecond = (TextView) mView.findViewById(R.id.max_tags_per_second);
        mInventoryTagsPerSecond = (TextView) mView.findViewById(R.id.tags_per_second_textview);

		mFoundTagsListView = (ListView) mView.findViewById(R.id.tags_listview);

		//sets simple adapter for foundtags list
		mFoundTagsListViewAdapter = new SimpleAdapter(
											getActivity(), 
											mInventoryController.getListViewAdapterData(),
											R.layout.taglist_row,
											new String[] { "epc","rssi" },
											new int[] { R.id.tagText, R.id.rssiText });
		
		//empty view when no tags in list
		mFoundTagsListView.setEmptyView(mView.findViewById(R.id.no_tags));
		mFoundTagsListView.setAdapter(mFoundTagsListViewAdapter);
		mFoundTagsListView.setCacheColorHint(0);
		mFoundTagsListView.setOnItemClickListener(new OnItemClickListener() {

			@SuppressWarnings("unchecked")
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				//if tag clicked
				HashMap<String, String> selectedTagData = (HashMap<String, String>) mFoundTagsListView.getItemAtPosition(position);
				mInventoryController.showTagDialog(getActivity(), selectedTagData);
			}
			
		});

		updateStats(mInventoryController);
	}

	public void selectFolder()
	{
		Main.getInstance().setDoNotDisconnectOnStop(true);
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		startActivityForResult(intent,  REQUEST_CODE_OPEN_DIRECTORY);
		Toast.makeText(getActivity(), "Specify folder for exporting data", Toast.LENGTH_LONG).show();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == Activity.RESULT_OK) {

			Uri treeUri = data.getData();
			String mPath = treeUri.toString();
			mInventoryController.SaveUriPath(mPath);
			String ret=mInventoryController.Export(getActivity().getApplicationContext());
			if(!ret.isEmpty()) {
				Toast.makeText(getActivity(), "Error occurred!: " + ret, Toast.LENGTH_LONG).show();
				return;
			}
			else {
				Toast.makeText(getActivity(), getString(R.string.export_success), Toast.LENGTH_LONG).show();
				mLastExportedCount = mLastUpdateTagCount;
			}
		}

		Main.getInstance().setDoNotDisconnectOnStop(false);
	}

	public void startInventory() {
		try {
			mLastExportedCount = 0;
			if (!mInventoryController.startContinuousInventory()) {
				Toast.makeText(getActivity(), getString(R.string.reader_connection_error), Toast.LENGTH_SHORT).show();
			}
		} catch (Exception e)
		{
			Toast.makeText(getActivity(), getString(R.string.reader_error), Toast.LENGTH_SHORT).show();
		}
	}
	
	public void stopInventory() {
		mInventoryController.stopInventory();
	}

	//if back pressed when fragment is active
	@Override
	public boolean onFragmentBackPressed() {
		
		// if inventory running, stop it and return true to indicate AppTemplate we've handled back press
		if (mInventoryController.isInventoryRunning()) {
			stopInventory();
			return true;
		}

		// Return false to let AppTemplate to handle back press
		return false;
	}
	
	//When inventory is running keep screen on
	private void keepScreenOn(boolean value) {
		mView.setKeepScreenOn(value);
	}
	
	//if app pauses stop the inventory.
	@Override
	public void onPause() {
		super.onPause();
		
		if (getAppTemplate().isRecentConfigurationChange() == false)
		{
			if (mInventoryController.isInventoryRunning()) {
				stopInventory();
			}
		}
	}
}
