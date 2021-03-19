package com.nordicid.rfiddemo;

import java.util.ArrayList;
import java.util.HashMap;

import com.nordicid.apptemplate.SubAppTabbed;
import com.nordicid.controllers.InventoryController;
import com.nordicid.controllers.InventoryController.InventoryControllerListener;
import com.nordicid.nuraccessory.NurAccessoryExtension;
import com.nordicid.nurapi.*;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import android.os.CountDownTimer;

/*The host that hostes InventoryAppReadingTab and InventoryAppFoundTab cause
 * by default SubApp defines the main/host fragment to retain its information.
 * 
 */
public class InventoryAppTabbed extends SubAppTabbed {

	final private String TAG = "INVAPP";
	private final int REQUEST_CODE_OPEN_DIRECTORY = 44;
	private Button mStartStopInventory;
	
	private InventoryAppReadingTab mReadingTab;
	private InventoryAppFoundTab mFoundTab;

	private InventoryController mInventoryController;

	long mLastUpdateTagCount = 0;
	long mLastExportedCount = 0;

	Handler mHandler;
	private CountDownTimer mPingTimer; //For keeping NUR module wake

	private static InventoryAppTabbed gInstance = null;
	public static InventoryAppTabbed getInstance()
	{
		return gInstance;
	}
	
	public InventoryController getInventoryController()
	{		
		return mInventoryController;	
	}

	@Override
	public NurApiListener getNurApiListener()
	{		
		return mInventoryController.getNurApiListener();	
	}
	
	public InventoryAppTabbed() {
		super();
		gInstance = this;
		mHandler = new Handler(Looper.getMainLooper());
		mInventoryController = new InventoryController(getNurApi());
	}

	@Override
	public void onVisibility(boolean val) {
	}

	@Override
	protected int onGetFragments(ArrayList<Fragment> fragments, ArrayList<String> fragmentNames) throws Exception
	{
		//create instances to fragments and pager.
		mReadingTab = new InventoryAppReadingTab();
		mFoundTab = new InventoryAppFoundTab();
		
		fragmentNames.add("Reading");
		fragments.add(mReadingTab);

		fragmentNames.add("Found");
		fragments.add(mFoundTab);

		return R.id.pager;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
	}
	
	//When inventory is running keep screen on
	private void keepScreenOn(boolean value) {
		mView.setKeepScreenOn(value);
	}

	private View mView;
	
	Runnable mTimeUpdate = new Runnable() {
		@Override
		public void run() {
			mReadingTab.updateStats(mInventoryController);
			
			if (mLastUpdateTagCount != mInventoryController.getTagStorage().size())
				mFoundTab.mFoundTagsListViewAdapter.notifyDataSetChanged();
			
			mLastUpdateTagCount = mInventoryController.getTagStorage().size();

			if (mInventoryController.isInventoryRunning()) {
				mHandler.postDelayed(mTimeUpdate, 250);
			}
		}
	};
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		mView = view;
		Log.w("INVENTORY","onViewCreated");

		//Wakeup NUR module in case its in sleep
		try {
			if(getNurApi().isConnected())
				getAppTemplate().getNurApi().ping();
		}catch (Exception e) {}

		//We'll need to ping NUR module time to time for preventing to go sleep because waking up takes 2 sec.
		//This helps to start inventory immediately when user press START button.
		mPingTimer = new CountDownTimer(10000,1000) {
			@Override
			public void onTick(long millisUntilFinished) {
				//Log.i(TAG,"seconds remaining: " + millisUntilFinished / 1000);
			}

			@Override
			public void onFinish() {

				if (mInventoryController.isInventoryRunning()==false)
				{
					//No inventory pending so let's keep NUR module wake
					try {
						getAppTemplate().getNurApi().ping();
						Log.i(TAG,"Ping!");
					}catch (Exception e) {}
				}

				mPingTimer.start(); //Start again

			}
		};

		Log.i(TAG,"StartPingTimer!");
		mPingTimer.start();

		mInventoryController.setListener(new InventoryControllerListener() {

			@SuppressWarnings("unchecked")
			@Override
			public void tagFound(NurTag tag, boolean isNew) {

				mFoundTab.mFoundTagsListViewAdapter.notifyDataSetChanged();

				if (isNew) {
					//mFoundTab.mFoundTagsListViewAdapter.notifyDataSetChanged();
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
			public void readerConnected() {
				mStartStopInventory.setText(getString(R.string.start));
			}

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
		
		//adds button bar button. Buttonbar will be visible in every tab.
		addButtonBarButton(getString(R.string.clear), new OnClickListener() {
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
					Toast.makeText(getActivity(), "Error occurred!: " + ret, Toast.LENGTH_SHORT).show();
					selectFolder();
					return;
				}
				else {
					Toast.makeText(getActivity(), getString(R.string.export_success), Toast.LENGTH_LONG).show();
				}

				mLastExportedCount = mLastUpdateTagCount;
			}
		});

		super.onViewCreated(view, savedInstanceState);
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

	public void stopInventory() {
		mInventoryController.stopInventory();
	}
	
	public void startInventory() {
		mLastExportedCount = 0;
		//Going to start inventory. Let's take inventory settings configurated by user.
		mInventoryController.LoadInventorySettings();

		try {
			if (!mInventoryController.startContinuousInventory()) {
				Toast.makeText(getActivity(), getString(R.string.reader_connection_error), Toast.LENGTH_SHORT).show();
			}
		} catch (Exception e)
		{
			Toast.makeText(getActivity(), getString(R.string.reader_error), Toast.LENGTH_SHORT).show();
		}
	}
	
	public void clearReadings() {
		mInventoryController.clearInventoryReadings();
		mFoundTab.mFoundTagsListViewAdapter.notifyDataSetChanged();
        mLastUpdateTagCount = 0;
        mLastExportedCount = 0;
		mReadingTab.updateStats(mInventoryController);
	}

	//main layout
	@Override
	public int getLayout() {
		return R.layout.app_inventory_tabbed;
	}

	@Override
	public String getAppName() {
		return "Inventory";
	}

	@Override
	public int getTileIcon() {
		return R.drawable.ic_inventory;
	}
	
	@Override
	public void onPause() {
		Log.w("INVENTORY","onPause");
		super.onPause();
		if (mInventoryController.isInventoryRunning()) {
			stopInventory();
		}
	}

	@Override
	public void onStop() {
		Log.w("INVENTORY","onStop");
		mPingTimer.cancel();

		super.onStop();
		if (mInventoryController.isInventoryRunning()) {
			stopInventory();
		}
	}

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
}
