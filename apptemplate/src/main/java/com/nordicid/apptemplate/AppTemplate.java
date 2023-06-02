package com.nordicid.apptemplate;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.nordicid.nurapi.AccessoryExtension;
import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurApiListener;
import com.nordicid.nurapi.NurApiUiThreadRunner;
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
import com.nordicid.nidulib.*;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

//import androidx.legacy.app.ActionBarDrawerToggle;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Extend this class to your MainActivity to use the template.
 * 
 * If not you do not use manifestmerger remember to add
 * android:configChanges="orientation|screenSize|keyboardHidden" to your
 * Manifest inside <activity>
 * 
 * @author Nordic ID
 * 
 */

public class AppTemplate extends FragmentActivity {

	static final String TAG = "AppTemplate";
	public static final String MIME_TEXT_PLAIN = "text/plain";
	private SubAppList mSubAppList;
	private String mGpsString="-";
	private FusedLocationProviderClient fusedLocationClient;

	private final int APP_PERMISSION_REQ_CODE = 41;

	private DrawerLayout mDrawerLayout;
	private FrameLayout mMenuContainer;
	private ActionBarDrawerToggle mDrawerToggle;
	private ListView mDrawerList;
	private Menu mMenu;
	private MenuItem mCloseButton;
	private Drawer mDrawer;
	private TextView mBatteryStatus = null;
	private ImageView mBatteryIcon = null;
    private AccessoryExtension mAccessoryApi = null;
	public boolean mAccessorySupported = false;
	private boolean mEnableBattUpdate = true;

	private FragmentManager mFragmentManager;
	private FragmentTransaction mFragmentTransaction;
	
	protected NurApi mApi;
	private NfcAdapter mNfcAdapter;
	private PendingIntent mPendingIntent;
	private NdefMessage mNdefPushMessage;
	
	private boolean mConfigurationChanged = false;
	private boolean mNoConfigChangeCheck = false;

	public boolean mUpdatePending;
	public boolean mNFCToastAlredyShown;

	protected NiduLib nidu;
	byte [] updInfo; //Byte array of UpdateInfo data downloaded earlier
	ArrayList<UpdateItem> availUpdatesList;
	static int availUpdDownloadStatus; //-1 = init 0=Download error 1=Download pending 2=Download success.

	//Set parameters for Update job
	AsyncTask<Void, Void, String> runningTask;

	private static final int REQUEST_LOCATION_CODE = 1;
	private static final int REQUEST_BLE_CODE = 2;
	private static final int REQUEST_EXTERNAL_STORAGE_CODE = 3;

	private static AppTemplate gInstance = null;
	public static AppTemplate getAppTemplate()
	{
		return gInstance;
	}
	public static int getAvailUpdDownloadStatus() { return availUpdDownloadStatus;}

	/**
	 * Get latitude and longitude
	 * @return format "%s;%s", location.getLatitude(), location.getLongitude()
	 * @see #refreshLocation()
	 */
	public String getLocation() { return mGpsString;}

	/**
	 * Activate location read.
	 * @see #getLocation()
	 */
	public void refreshLocation()
	{
		boolean isAllowed = true;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
					ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				isAllowed = false;
				Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
			}
		}

		if (isAllowed) {
			fusedLocationClient.getLastLocation()
					.addOnSuccessListener(this, new OnSuccessListener<Location>() {
						@Override
						public void onSuccess(Location location) {
							if (location != null) {
								mGpsString = (String.format(Locale.getDefault(), "%s;%s", location.getLatitude(), location.getLongitude()));
								Log.w(TAG, "GPS " + mGpsString);
							}
						}
					});
		}
	}

	public boolean isRecentConfigurationChange() {
		boolean rc = mConfigurationChanged;
		mConfigurationChanged = false;
		return rc;
	}

	public void setAppListener(NurApiListener l)
	{
		mAppListener = l;
	}

	/* Which sub-application is listening. */
	private NurApiListener mAppListener = null;	
	private NurApiListener mCurrentListener = null;	
	private NurApiListener mNurApiListener = new NurApiListener() {		
		@Override
		public void triggeredReadEvent(NurEventTriggeredRead event) { }
		@Override
		public void traceTagEvent(NurEventTraceTag event) {
			if (mCurrentListener != null)
				mCurrentListener.traceTagEvent(event);					
		}
		@Override
		public void programmingProgressEvent(NurEventProgrammingProgress event) {
            if (mCurrentListener != null)
                mCurrentListener.programmingProgressEvent(event);
        }
		@Override
		public void nxpEasAlarmEvent(NurEventNxpAlarm event) { }
		@Override
		public void logEvent(int level, String txt) { }
		@Override
		public void inventoryStreamEvent(NurEventInventory event) {
			if (mCurrentListener != null)
				mCurrentListener.inventoryStreamEvent(event);
		}
		@Override
		public void inventoryExtendedStreamEvent(NurEventInventory event) {
			if (mCurrentListener != null)
				mCurrentListener.inventoryExtendedStreamEvent(event);
		}
		@Override
		public void frequencyHopEvent(NurEventFrequencyHop event) { }
		@Override
		public void epcEnumEvent(NurEventEpcEnum event) { }
		@Override
		public void disconnectedEvent() {
			if (exitingApplication())
				return;
			mAccessorySupported = false;
            // only do these things when reader in application mode

			if (mAppListener != null) {
				mAppListener.disconnectedEvent();
			}
			if (mCurrentListener != null)
				mCurrentListener.disconnectedEvent();		
		}
		@Override
		public void deviceSearchEvent(NurEventDeviceInfo event) { }
		@Override
		public void debugMessageEvent(String event) { }
		@Override
		public void connectedEvent() {
			try {
				mAccessorySupported = getAccessoryApi().isSupported();
				if(availUpdDownloadStatus == 0) {
					if(runningTask != null) {
						runningTask.cancel(true);
					}

					runningTask = new DownLoadAvailableUpdates();
					runningTask.execute();
				}
				//refreshAvailableUpdates();
			} catch (Exception e)
			{
				mAccessorySupported = false;
			}
			if (mAppListener != null)
				mAppListener.connectedEvent();
			if (mCurrentListener != null)
				mCurrentListener.connectedEvent();

		}
		@Override
		public void clientDisconnectedEvent(NurEventClientInfo event) { }
		@Override
		public void clientConnectedEvent(NurEventClientInfo event) { }
		@Override
		public void bootEvent(String event) {
			//Toast.makeText(AppTemplate.this, "BOOT " + event, Toast.LENGTH_SHORT).show();
			if (mCurrentListener != null)
				mCurrentListener.bootEvent(event);			
		}
		@Override
		public void IOChangeEvent(NurEventIOChange event) {
			//Log.d(TAG, "IOCHG " + event.source + "; " + event.direction);

			if (mAppListener != null)
				mAppListener.IOChangeEvent(event);

			if (mCurrentListener != null)
				mCurrentListener.IOChangeEvent(event);
		}
		@Override
		public void autotuneEvent(NurEventAutotune event) { }
		@Override
		public void tagTrackingScanEvent(NurEventTagTrackingData event) { }
		@Override
		public void tagTrackingChangeEvent(NurEventTagTrackingChange event) { }
	};

	private void changeSubAppListener()
	{
		SubApp tmpSubApp = mSubAppList.getCurrentOpenSubApp();
		if (tmpSubApp != null) 
			mCurrentListener = tmpSubApp.getNurApiListener();		
		else 
			mCurrentListener = null;
	}

	/** Swaping Listeners from settings fragment **/
    //FIXME is there a better way ?
	private NurApiListener oldListener = null;
	public void switchNurApiListener(NurApiListener newListener){
        oldListener = mCurrentListener;
        mCurrentListener = newListener;
    }
    public void restoreListener(){
        if(oldListener != null)
            mCurrentListener = oldListener;
        else
            mCurrentListener = mNurApiListener;
    }
	/** Testing only **/

	/**
	 * if back button pressed once. @see doubleOnBackPressedExit
	 */
	private boolean backPressedOnce;
	private boolean showMenuAnimation;

	protected boolean mApplicationPaused = true;

	public boolean isApplicationPaused()
	{
		return mApplicationPaused;
	}
	
	/**
	 * Indicates if the current device has a large screen
	 */
	public static boolean LARGE_SCREEN;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		gInstance = this;

		//Location permission
		if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) &&
				((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED))) {
			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
							Manifest.permission.ACCESS_COARSE_LOCATION},
					REQUEST_LOCATION_CODE);
		}

		//External storage access till v10
		//https://developer.android.com/reference/android/Manifest.permission#READ_EXTERNAL_STORAGE
		//https://developer.android.com/reference/android/Manifest.permission#WRITE_EXTERNAL_STORAGE
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
			if ((ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) &&
					((ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED))) {
				ActivityCompat.requestPermissions(this,
						new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
								Manifest.permission.WRITE_EXTERNAL_STORAGE},
						REQUEST_EXTERNAL_STORAGE_CODE);
			}
		}
		//Do we really need external storage in v11 and higher?
		//consider using MANAGE_EXTERNAL_STORAGE

		//BLUETOOTH && BLE_ADMIN set to work till v11
		//https://developer.android.com/guide/topics/connectivity/bluetooth/permissions
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
			if ((ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) &&
					((ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED))) {
				ActivityCompat.requestPermissions(this,
						new String[]{Manifest.permission.BLUETOOTH,
								Manifest.permission.BLUETOOTH_ADMIN},
						REQUEST_BLE_CODE);
			}
		} else { //BLE permissions for v12 and higher
			if ((ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) &&
					((ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)) &&
					((ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED))) {
				ActivityCompat.requestPermissions(this,
						new String[]{Manifest.permission.BLUETOOTH_SCAN,
								Manifest.permission.BLUETOOTH_ADVERTISE,
								Manifest.permission.BLUETOOTH_CONNECT},
						REQUEST_BLE_CODE);
			}
		}

		//Do we need to check the permission status prior accessing the resource again?

		/** Bluetooth Permission checks **/
		/*if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED  ||
				ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED  ||
				ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
				ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){

			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)  ||
					ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)  ||
					ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE) ||
					ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
					) {
				// ? ? ? //
			} else {
				ActivityCompat.requestPermissions(this, new String[]{
						Manifest.permission.ACCESS_COARSE_LOCATION,
						Manifest.permission.ACCESS_FINE_LOCATION,
						Manifest.permission.READ_EXTERNAL_STORAGE,
						Manifest.permission.WRITE_EXTERNAL_STORAGE},
						APP_PERMISSION_REQ_CODE);
			}
		}*/

		fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

		mApi = new NurApi();
		//mApi.setLogLevel(mApi.getLogLevel() | NurApi.LOG_VERBOSE | NurApi.LOG_DATA);
		mApi.setLogToStdout(true);

        mAccessoryApi = new AccessoryExtension(mApi);
		
		mApi.setUiThreadRunner(new NurApiUiThreadRunner() {
			@Override
			public void runOnUiThread(Runnable r) {
				AppTemplate.this.runOnUiThread(r);
			}
		});
		
		mApi.setListener(mNurApiListener);

		nidu = new NiduLib();
		nidu.setNurApi(mApi);
		nidu.setAccessoryExtension(mAccessoryApi);
		nidu.setListener(mNiduLibListener);

		availUpdDownloadStatus = -1;
		mNFCToastAlredyShown = false;
		mUpdatePending = false;

		//Application theme and typeface will be changed
		setTheme(getApplicationTheme());
		TypefaceOverrider.setDefaultFont(getApplicationContext(), "MONOSPACE", getPahtToTypeface());

		setContentView(R.layout.main);

		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawerLayout);
		mDrawer = new Drawer();
		
		//Set the drawer and add items to it
		setDrawer(true);

        mBatteryStatus = (TextView) findViewById(R.id.battery_level);
		mBatteryIcon = (ImageView) findViewById(R.id.battery_icon);

		//setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		LARGE_SCREEN = ((getResources().getConfiguration().screenLayout &
				Configuration.SCREENLAYOUT_SIZE_MASK) ==
				Configuration.SCREENLAYOUT_SIZE_LARGE);
		mSubAppList = new SubAppList();
		
		//Adds the users SubApps to SubAppList
		onCreateSubApps(mSubAppList);

		runningTask = new DownLoadAvailableUpdates();
		runningTask.execute();

		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

		if(mNfcAdapter != null) {
			if (!mNfcAdapter.isEnabled()) {
				Log.w(TAG, "NFC is disabled.");
			} else {
				Log.w(TAG, "NFC is enabled");
			}
			mPendingIntent = PendingIntent.getActivity(this, 0,
					new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
			mNdefPushMessage = new NdefMessage(new NdefRecord[]{newTextRecord(
					"Message from NFC Reader :-)", Locale.ENGLISH, true)});

		}

		if (mSubAppList.getAllApps().size() == 0)
		{
			Toast.makeText(this, "No subapps found", Toast.LENGTH_SHORT).show();
		} 
		else 
		{
			mMenuContainer = (FrameLayout) findViewById(R.id.menu_container);
			
			//Activity is created show menu animation
			showMenuAnimation = false;
			
			//If application started in landscape, configure the layout
			if (mMenuContainer != null) {
				mNoConfigChangeCheck = true;
				onConfigurationChanged(getResources().getConfiguration());	
			}
		}

		super.onCreate(savedInstanceState);
	}

	private final class DownLoadAvailableUpdates extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... params) {
			//byte[] arr;
			availUpdDownloadStatus=1;
			try {
				Log.i(TAG,"Download..");
				updInfo = NiduLib.DownLoadFromURL("https://raw.githubusercontent.com/NordicID/nur_firmware/master/zip/NIDLatestFW.json");
				refreshAvailableUpdates();
				availUpdDownloadStatus=2;
				//availUpdatesList = nidu.GetAvailableUpdates(updInfo);
				//Log.i(TAG,"availUpdatesList size=" + availUpdatesList.size());
			}catch (Exception e) {
				Log.i(TAG,"Download Error:" + e.getMessage());
				availUpdDownloadStatus=0;
				e.printStackTrace();
				updInfo=null;
				return e.getMessage();
			}

			return "Bytes received:" + updInfo.length ;
		}

		@Override
		protected void onPostExecute(String result) {
			Log.i(TAG,"onPostExecute=" + result);
		}
	}

	private NdefRecord newTextRecord(String text, Locale locale, boolean encodeInUtf8) {
		byte[] langBytes = locale.getLanguage().getBytes(Charset.forName("US-ASCII"));

		Charset utfEncoding = encodeInUtf8 ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
		byte[] textBytes = text.getBytes(utfEncoding);

		int utfBit = encodeInUtf8 ? 0 : (1 << 7);
		char status = (char) (utfBit + langBytes.length);

		byte[] data = new byte[1 + langBytes.length + textBytes.length];
		data[0] = (byte) status;
		System.arraycopy(langBytes, 0, data, 1, langBytes.length);
		System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);

		return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], data);
	}

	/**
	 * @param activity The corresponding {@link BaseActivity} requesting to stop the foreground dispatch.
	 * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
	 */
	public void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
		adapter.disableForegroundDispatch(activity);
		Log.w(TAG, "stopForegroundDispatch");
	}

	/**
	 * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
	 * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
	 */
	public void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
		final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
		intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

		final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(),
				0, intent, PendingIntent.FLAG_IMMUTABLE);

		IntentFilter[] filters = new IntentFilter[1];
		String[][] techList = new String[][]{};

		// Notice that this is the same filter as in our manifest.
		filters[0] = new IntentFilter();
		filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
		filters[0].addCategory(Intent.CATEGORY_DEFAULT);
		try {
			filters[0].addDataType(MIME_TEXT_PLAIN);
		} catch (IntentFilter.MalformedMimeTypeException e) {
			throw new RuntimeException("Check your mime type.");
		}

		adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
		Log.w(TAG, "setupForegroundDispatch");
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, final String permissions[], final int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		final List<String> missingPermissions = new ArrayList<>();
		switch (requestCode) {
			case REQUEST_LOCATION_CODE:
				if (grantResults.length > 0 &&
						grantResults[0] == PackageManager.PERMISSION_DENIED) {
					AlertDialog.Builder locNotifier = new AlertDialog.Builder(this);
					locNotifier.setMessage("Application won't function properly without location permissions.")
							.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									dialog.dismiss();
								}
							});
					locNotifier.create().show();
				}
				break;
			case REQUEST_BLE_CODE:
				if (grantResults.length > 0 &&
						grantResults[0] == PackageManager.PERMISSION_DENIED) {
					AlertDialog.Builder bleNotifier = new AlertDialog.Builder(this);
					bleNotifier.setMessage("Application won't function properly without bluetooth permissions.")
							.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									dialog.dismiss();
								}
							});
					bleNotifier.create().show();
				}
				break;
			case REQUEST_EXTERNAL_STORAGE_CODE:
				if (grantResults.length > 0 &&
						grantResults[0] == PackageManager.PERMISSION_DENIED) {
					AlertDialog.Builder externalStorageNotifier = new AlertDialog.Builder(this);
					externalStorageNotifier.setMessage("Application won't function properly without external storage access permissions.")
							.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									dialog.dismiss();
								}
							});
					externalStorageNotifier.create().show();
				}
				break;
			//below case is no longer applicable
			case APP_PERMISSION_REQ_CODE: {
				for (int i = 0; i < grantResults.length; i++) {
					if (grantResults[i] == PackageManager.PERMISSION_DENIED)
						missingPermissions.add(permissions[i]);
				}
				if (missingPermissions.size() > 0) {
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setMessage("Application will not work properly without all requested permissions. Do you want to grant them now ?")
							.setPositiveButton("Grant Permissions", new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int id) {
									ActivityCompat.requestPermissions(gInstance, missingPermissions.toArray(new String[0]), APP_PERMISSION_REQ_CODE);
								}
							})
							.setNegativeButton("No thanks", new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int id) {
									Toast.makeText(gInstance, "Missing permissions", Toast.LENGTH_SHORT).show();
								}
							});
					builder.create().show();
				}

			}
		}
	}

	long mLastBattUpdate = 0;

	NiduLibListener mNiduLibListener = new NiduLibListener() {
		@Override
		public void niduEvent(Event event, int i, Object o) {
			switch (event) {

				case LOG:
					Log.i(TAG, "LOG:" + o.toString());
					break;

				case STATUS:
					Log.i(TAG, "STATUS:" + o.toString());
					break;

				case VALIDATE:

					if (nidu.ValidateEXA(i)) {
						Log.i(TAG, nidu.getUpdateItem(i).name + " Validate EXA OK");
						//It looks like this device need this new firmware
						//SaveToFile(ui.filename,i);
						nidu.getUpdateItem(i).status = Status.READY;
						nidu.setStatus(Status.READY); //forces to do programming
						Log.i(TAG, (nidu.getUpdateItem(i).name + " READY TO UPDATE Status=" + nidu.getUpdateItem(i).status));
					} else {
						Log.i(TAG, "EXA " + nidu.getUpdateItem(i).name + " Validate FAIL");
					}


					break;
			}
		}
	};

	public void setStatusText(String text)
	{
		TextView t = (TextView) findViewById(R.id.app_statustext);
		t.setText(text.toUpperCase());

        if(getNurApi().isConnected() && getAccessorySupported()) {
            try {
				if (mEnableBattUpdate && (System.currentTimeMillis()-mLastBattUpdate) > 20000) {
					mLastBattUpdate = System.currentTimeMillis();
					setBatteryStatus(getAccessoryApi().getBatteryInfo().getPercentageString());
				}
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else {
            setBatteryStatus("");
        }
	}

    public void setBatteryStatus(String text) {
		if (text.length() == 0)
		{
			if (mBatteryStatus.getVisibility() != View.GONE) {
				mBatteryStatus.setVisibility(View.GONE);
				mBatteryIcon.setVisibility(View.GONE);
			}
		}
        else
		{
			if (mBatteryStatus.getVisibility() != View.VISIBLE) {
				mBatteryStatus.setVisibility(View.VISIBLE);
				mBatteryIcon.setVisibility(View.VISIBLE);
			}
			mBatteryStatus.setText(text);
		}
    }
	
	@Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

	public int getTemplateTheme() {
		return R.style.TemplateDefaultTheme;
	}
	
	/**
	 * Creates the action / title bar. Used internally.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		
		getMenuInflater().inflate(R.menu.actionbar_menu, menu);
		
		mMenu = menu;
		mCloseButton = mMenu.findItem(R.id.actionbar_close_button);
		if(mSubAppList.getCurrentOpenSubApp() != null && getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
            mCloseButton.setVisible(true);
		return super.onCreateOptionsMenu(menu);
	}

	/**
	 * Gets the applications root view.
	 * @return rootView
	 */
	protected View getRootView() {
		View rootView = getWindow().getDecorView().getRootView();
		return rootView;
	}
	
	/**
	 * Method that is executed when SubAppList created. Use 
	 * this method to add SubApps to SubAppList.
	 *
	 * @see com.nordicid.apptemplate.SubAppList#addSubApp(SubApp)
	 */
	protected void onCreateSubApps(SubAppList subAppList) { }

	/**
	 * Method that is executed when NFC tag read.
	 *
	 * @see com.nordicid.apptemplate.SubAppList#addSubApp(SubApp)
	 */
	protected void onNfcRead(String hex, String dec, String tec) { }

	/**
	 * Returns SubAppList
	 * @return SubAppList
	 * @see com.nordicid.apptemplate.SubAppList
	 */
	public SubAppList getSubAppList() {
		return mSubAppList;
	}

	/**
	 * Sets the layout for the new configuration that the devices
	 * has at the moment. Used internally.
	 */
	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		Log.d(TAG, "onConfigurationChanged() newConfig.orientation " + newConfig.orientation);
		super.onConfigurationChanged(newConfig);

		if (mNoConfigChangeCheck == false)
			mConfigurationChanged = true;
		else 
			mNoConfigChangeCheck = false;

		setContentView(R.layout.main);

		if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
		{
			mMenuContainer = (FrameLayout) findViewById(R.id.menu_container);
			if (mCloseButton != null)
				mCloseButton.setVisible(false);
		} else {
			mMenuContainer = null;
			if (mSubAppList.getCurrentOpenSubApp() != null) {
				if (mCloseButton != null)
					mCloseButton.setVisible(true);
			}
		}

		if (!mApplicationPaused) {
			setFragments(true);
		}
		if (mSubAppList.getCurrentOpenSubApp() == null) {
			mSubAppList.setCurrentOpenSubApp(mSubAppList.getVisibleApp(0));
		}
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawerLayout);
        mBatteryStatus = (TextView) findViewById(R.id.battery_level);
		mBatteryIcon = (ImageView) findViewById(R.id.battery_icon);
		setDrawer(false);
        mDrawerToggle.onConfigurationChanged(newConfig);
	}
	
	/**
	 * Handles back button presses. Current open SubApps onFragmentBackPressed 
	 * must return false to execute this method fully. If orientation is landscape 
	 * current open SubApp wont be closed, method will execute doubleOnBackPressExit 
	 * to exit.
	 * @see #doubleOnBackPressExit
	 * @see com.nordicid.apptemplate.AppTemplate
	 */

	@Override
	public void onBackPressed() {
		SubApp currentSubApp = mSubAppList.getCurrentOpenSubApp();
		
		if (currentSubApp != null) {
			// See if SubApp handled back button press
			if (currentSubApp.onFragmentBackPressed()) {
				return;
			}
		}

		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			if (currentSubApp == null) {
				doubleOnBackPressExit();
			}
			else {
				// Go back to main menu
				openSubApp(null);
			}
		}
		else {
			doubleOnBackPressExit();
		}
	}

	private boolean mExitingApplication = false;

	// Return the "exiting now" status; may be required for not to
	// poke around the sub-apps too much at the wrong time.
	public boolean exitingApplication()
	{
		return mExitingApplication;
	}

	public void finishApp()
	{
		// Stop generating NurApi events
		mApi.setListener(null);

		mExitingApplication = true;
		finish();
	}

	/**
	 * Method to exit the app if called twice in 2 seconds
	 */
	private void doubleOnBackPressExit() {
		if (backPressedOnce) {
			// Back pressed twice within 2sec -> shutdown everything
			finishApp();
			//super.onBackPressed();
			return;
	    }

	    backPressedOnce = true;
	    Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
	    new Handler().postDelayed(new Runnable() {
	        @Override
	        public void run() {
	            backPressedOnce = false;
				mExitingApplication = false;
	        }
	    }, 2000);
	}

	/**
	 * Method to open another SubApp with its name.
	 * If SubApp with given name not found, a Toast will be shown.
	 *
	 *  @param name of the SubApp
	 */
	public void setApp(String name) {

        if (name == null) {
            openSubApp(null);
        }
        else {
            SubApp app = mSubAppList.getVisibleSubApp(name);

            if (app == null) {
                Toast.makeText(this, "App with name \"" + name + "\" not found", Toast.LENGTH_SHORT).show();
            } else {
                openSubApp(app);
            }
        }
    }

    //TODO gets visible / hidden apps
    public void setApp(String name,boolean visible) {

        if (name == null) {
            openSubApp(null);
        }
        else {
            SubApp app;
            if(visible)
                app = mSubAppList.getVisibleSubApp(name);
            else
                app = mSubAppList.getApp(name);
            if (app == null) {
                Toast.makeText(this, "App with name \"" + name + "\" not found", Toast.LENGTH_SHORT).show();
            } else {
                openSubApp(app);
            }
        }
    }
	
	/**
	 *  Used internally to open some SubApp
	 */
	private void openSubApp(SubApp app) {
		if (app == null)
		{
			// Go back to main menu
			mSubAppList.setCurrentOpenSubApp(null);
			setFragments(false);
		}
		else if (app != mSubAppList.getCurrentOpenSubApp() && !app.isVisible()) {
			mSubAppList.setCurrentOpenSubApp(app);
			setFragments(false);
		}
	}
	
	/**
	 * gets the NurApi
	 * @return NurApi
	 */
	public NurApi getNurApi() {
		return mApi;
	}

	public AccessoryExtension getAccessoryApi() { return mAccessoryApi; }
	public boolean getAccessorySupported() { return mAccessorySupported; }
	public void refreshAvailableUpdates()
	{
		Log.w(TAG, "refreshAvailableUpdates");
		if(updInfo != null)
		{
			try {
				availUpdatesList = nidu.GetAvailableUpdates(updInfo);
			}
			catch (Exception e) {
				Log.e(TAG,"ERR: refreshAvailableUpdates:" + e.getMessage());
				updInfo = null;
			}
		}
	}

	public ArrayList<UpdateItem> getAvailableUpdates()
	{
		Log.w(TAG, "getAvailableUpdates");
		if(updInfo != null)
		{
			return availUpdatesList;
		}

		return null;
	}

	public void setEnableBattUpdate(boolean val) { mEnableBattUpdate = val; }

	private Fragment lastSetFragment = null;

	/**
	 *  Sets all the fragments
	 */
	private void setFragments(boolean configChange)
	{
		SubApp currentOpenSubApp = mSubAppList.getCurrentOpenSubApp();
		int orientation = getResources().getConfiguration().orientation;

		mFragmentManager = getSupportFragmentManager();

		// Always remove subapplist on config change
		if (configChange && mSubAppList.isAdded())
		{
			mFragmentTransaction = mFragmentManager.beginTransaction();
			mFragmentTransaction.remove(mSubAppList);
			mFragmentTransaction.commit();
			mFragmentManager.executePendingTransactions();
		}

		// Remove last set subapp
		if (lastSetFragment != null && lastSetFragment.isAdded()) {
			mFragmentTransaction = mFragmentManager.beginTransaction();
			mFragmentTransaction.remove(lastSetFragment);
			mFragmentTransaction.commit();
			mFragmentManager.executePendingTransactions();
		}

		if (orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			if (currentOpenSubApp == null)
			{
				// No subapp selected, show subapplist
				if (!mSubAppList.isAdded()) {
					mFragmentTransaction = mFragmentManager.beginTransaction();
					mFragmentTransaction.replace(R.id.content, mSubAppList);
					mFragmentTransaction.commit();
				}

				if (mCloseButton != null)
					mCloseButton.setVisible(false);

				getActionBar().setTitle(R.string.app_name);
			}
			else
			{
				// Subapp selected, remove subapplist if needed first
				if (mSubAppList.isAdded()) {
					mFragmentTransaction = mFragmentManager.beginTransaction();
					mFragmentTransaction.remove(mSubAppList);
					mFragmentTransaction.commit();
					mFragmentManager.executePendingTransactions();
				}

				// Add subapp
				mFragmentTransaction = mFragmentManager.beginTransaction();
				mFragmentTransaction.replace(R.id.content, currentOpenSubApp);
				mFragmentTransaction.commit();
				lastSetFragment = currentOpenSubApp;

				getActionBar().setTitle(currentOpenSubApp.getAppName());

				if (mCloseButton != null)
					mCloseButton.setVisible(true);
			}
		}
		else
		{
			// Landscape

			if (mCloseButton != null)
				mCloseButton.setVisible(false);

			// Always show some subapp
			if (currentOpenSubApp == null) {
				currentOpenSubApp = mSubAppList.getVisibleApp(0);
			}

			// Add subapp
			mFragmentTransaction = mFragmentManager.beginTransaction();
			mFragmentTransaction.replace(R.id.content, currentOpenSubApp);
			mFragmentTransaction.commit();
			lastSetFragment = currentOpenSubApp;

			setTitle(currentOpenSubApp.getAppName());

			// If subapp not added, add it to sidebar
			if (!mSubAppList.isAdded()) {
				mFragmentTransaction = mFragmentManager.beginTransaction();
				mFragmentTransaction.replace(R.id.menu_container, mSubAppList);
				mFragmentTransaction.commit();
			}
		}

		changeSubAppListener();
	}
	
	/**
	 * Sets the navigation drawer and adds the items to it
	 * if necessary
	 * 
	 * @param addItems
	 */
	private void setDrawer(boolean addItems) {
		
		if (addItems) {
			onCreateDrawerItems(mDrawer);
		}
					
		/*mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
				R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close)*/
		mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
				R.string.drawer_open, R.string.drawer_close)		{
	
				@Override
				public void onDrawerClosed(View drawerView) {
					super.onDrawerClosed(drawerView);
                    invalidateOptionsMenu();
				}
	
				@Override
				public void onDrawerOpened(View drawerView) {
					super.onDrawerOpened(drawerView);
                    invalidateOptionsMenu();
				}
		};
		
		mDrawerList = (ListView) findViewById(R.id.left_drawer);
		mDrawerList.setAdapter(new DrawerItemAdapter(this,mDrawer.getDrawerItemTitles()));
		mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
		mDrawerLayout.setDrawerListener(mDrawerToggle);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setHomeButtonEnabled(true);

	}
	
	/**
	 * Gets desired theme that will be set to
	 * the application. Override this to return desired
	 * theme.
	 * 
	 * Default: R.style.TemplateDefaultTheme
	 * @return R.style.theme_name
	 */
	public int getApplicationTheme() {
		return R.style.TemplateDefaultTheme;
	}

	public void onCreateDrawerItems(Drawer drawer) {}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		if (mDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}
		else if (item.getItemId() == R.id.actionbar_close_button) {
			onBackPressed();
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResumeFragments() {

		Log.d(TAG, "onResumeFragments() mApplicationPaused " + mApplicationPaused);

		super.onResumeFragments();
		setFragments(true);
		
		mApplicationPaused = false;
	}
	
	@Override
	protected void onPause() {
		Log.d(TAG, "onPause() mApplicationPaused " + mApplicationPaused);

		super.onPause();
		mApplicationPaused = true;
		if (mNfcAdapter != null) {
			mNfcAdapter.disableForegroundDispatch(this);
			mNfcAdapter.disableForegroundNdefPush(this);
		}
	}
	
	@Override
	protected void onResume() {
		Log.d(TAG, "onResume() mApplicationPaused " + mApplicationPaused);
		super.onResume();
		if (mNfcAdapter != null) {
			if (!mNfcAdapter.isEnabled() && getAppTemplate().mNFCToastAlredyShown==false) {
				Toast.makeText(AppTemplate.this, "NFC disabled!", Toast.LENGTH_LONG).show();
				getAppTemplate().mNFCToastAlredyShown=true;
				//showWirelessSettingsDialog();
			}
			mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
			mNfcAdapter.enableForegroundNdefPush(this, mNdefPushMessage);
		}
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		mApplicationPaused = true;
		
		if (mApi != null) 
		{
			if (mApi.isConnected()) {
				try {				
					mApi.stopAllContinuousCommands();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	@Override
	protected void onDestroy() {
		Log.d(TAG, "onDestroy() mApplicationPaused " + mApplicationPaused);
		super.onDestroy();

		mExitingApplication = true;

		if (mApi != null) {
			mApi.dispose();
		}
	}

	private void showWirelessSettingsDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("NFC disabled. ");
		builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialogInterface, int i) {

				Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
				startActivity(intent);

			}
		});
		builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialogInterface, int i) {
				finish();
			}
		});
		builder.create().show();
		return;
	}

	static final byte GET_MANUFACTURING_DATA = (byte) 0x60;



	private void resolveIntent(Intent intent) {
		String action = intent.getAction();
		String s="";
		Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

		if(tag == null)
			return;

		Log.w(TAG, "resolveIntent=" + action + " Tag=" + tag.toString());
		if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)	|| NfcAdapter.ACTION_TECH_DISCOVERED.equals(action) || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
			Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			NdefMessage[] msgs;
			if (rawMsgs != null) {
				msgs = new NdefMessage[rawMsgs.length];

				if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {

					try {
						for (int i = 0; i < rawMsgs.length; i++) {
							NdefRecord [] recs = ((NdefMessage)rawMsgs[i]).getRecords();

							for (int j = 0; j < recs.length; j++) {
								Log.w(TAG, "recs[j].getTnf()=" + recs[j].getTnf());
								if (recs[j].getTnf() == NdefRecord.TNF_WELL_KNOWN) {
									StringBuilder sb = new StringBuilder();
									sb.append(recs[j].toUri() + "\n");
									String prefix = "android.nfc.tech.";
									sb.append("Technologies: ");
									for (String tech : tag.getTechList()) {
										sb.append(tech.substring(prefix.length()));
										sb.append(", ");
									}

									String tHex= sb.toString();
									String tDec="";
									String tTech="";
									onNfcRead(tHex,tDec,tTech);
									break;
								}
							}
						}
					} catch (Exception e) {
						Log.e("TagDispatch", e.toString());
					}
				}


					for (int i = 0; i < rawMsgs.length; i++) {
					msgs[i] = (NdefMessage) rawMsgs[i];
					Log.w(TAG, "NDefMsg=" + msgs[i].toString());
				}
			} else {
				// Unknown tag type
				byte[] id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
				//Tag tag = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

				String tHex= dumpTagData(tag);//toHex(tag.getId());
				String tDec="";//Long.toString(toDec(tag.getId()));
				String tTech="";
				onNfcRead(tHex,tDec,tTech);
				/*
				String [] arr=getTagInfo(intent);
				for(int x=0;x<arr.length;x++)
					Log.w(TAG, "x=" + x + " Txt=" + arr[x] );
				*/


			}
			// Setup the views
			//buildTagViews(msgs);
		}
	}

	private String[] getTagInfo(Intent intent) {
		Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		String prefix = "android.nfc.tech.";
		String[] info = new String[2];

		// UID
		byte[] uid = tag.getId();
		info[0] = "UID In Hex: " + NurApi.byteArrayToHexString(uid) + "\n";

		// Tech List
		String[] techList = tag.getTechList();
		String techListConcat = "Technologies: ";
		for(int i = 0; i < techList.length; i++) {
			techListConcat += techList[i].substring(prefix.length()) + ",";
		}
		info[0] += techListConcat.substring(0, techListConcat.length() - 1) + "\n\n";

		// Mifare Classic/UltraLight Info
		info[0] += "Card Type: ";
		String type = "Unknown";
		for(int i = 0; i < techList.length; i++) {
			if(techList[i].equals(MifareClassic.class.getName())) {
				info[1] = "Mifare Classic";
				MifareClassic mifareClassicTag = MifareClassic.get(tag);

				// Type Info
				switch (mifareClassicTag.getType()) {
					case MifareClassic.TYPE_CLASSIC:
						type = "Classic";
						break;
					case MifareClassic.TYPE_PLUS:
						type = "Plus";
						break;
					case MifareClassic.TYPE_PRO:
						type = "Pro";
						break;
				}
				info[0] += "Mifare " + type + "\n";

				// Size Info
				info[0] += "Size: " + mifareClassicTag.getSize() + " bytes \n" +
						"Sector Count: " + mifareClassicTag.getSectorCount() + "\n" +
						"Block Count: " + mifareClassicTag.getBlockCount() + "\n";
			} else if(techList[i].equals(MifareUltralight.class.getName())) {
				info[1] = "Mifare UltraLight";
				MifareUltralight mifareUlTag = MifareUltralight.get(tag);

				// Type Info
				switch (mifareUlTag.getType()) {
					case MifareUltralight.TYPE_ULTRALIGHT:
						type = "Ultralight";
						break;
					case MifareUltralight.TYPE_ULTRALIGHT_C:
						type = "Ultralight C";
						break;
				}
				info[0] += "Mifare " + type + "\n";
			} else if(techList[i].equals(IsoDep.class.getName())) {
				info[1] = "IsoDep";
				IsoDep isoDepTag = IsoDep.get(tag);
				info[0] += "IsoDep \n";
			} else if(techList[i].equals(Ndef.class.getName())) {
				Ndef ndefTag = Ndef.get(tag);
				info[0] += "Is Writable: " + ndefTag.isWritable() + "\n" +
						"Can Make ReadOnly: " + ndefTag.canMakeReadOnly() + "\n";
			} else if(techList[i].equals(NdefFormatable.class.getName())) {
				NdefFormatable ndefFormatableTag = NdefFormatable.get(tag);
			}
		}

		return info;
	}

	private String dumpTagData(Tag tag) {
		StringBuilder sb = new StringBuilder();
		byte[] id = tag.getId();
		//sb.append("ID (hex): ").append(toHex(id)).append('\n');
		sb.append("Serial: ").append(toReversedHex(id)).append('\n');
		//sb.append("ID (dec): ").append(toDec(id)).append('\n');
		//sb.append("ID (reversed dec): ").append(toReversedDec(id)).append('\n');

		String prefix = "android.nfc.tech.";
		sb.append("Technologies: ");
		for (String tech : tag.getTechList()) {
			sb.append(tech.substring(prefix.length()));
			sb.append(", ");
		}
		sb.delete(sb.length() - 2, sb.length());
		for (String tech : tag.getTechList()) {
			if (tech.equals(MifareClassic.class.getName())) {
				sb.append('\n');
				String type = "Unknown";
				try {
					MifareClassic mifareTag;
					try {
						mifareTag = MifareClassic.get(tag);
					} catch (Exception e) {
						// Fix for Sony Xperia Z3/Z5 phones
						tag = cleanupTag(tag);
						mifareTag = MifareClassic.get(tag);
					}
					switch (mifareTag.getType()) {
						case MifareClassic.TYPE_CLASSIC:
							type = "Classic";
							break;
						case MifareClassic.TYPE_PLUS:
							type = "Plus";
							break;
						case MifareClassic.TYPE_PRO:
							type = "Pro";
							break;
					}
					sb.append("Mifare Classic type: ");
					sb.append(type);
					sb.append('\n');

					sb.append("Mifare size: ");
					sb.append(mifareTag.getSize() + " bytes");
					sb.append('\n');

					sb.append("Mifare sectors: ");
					sb.append(mifareTag.getSectorCount());
					sb.append('\n');

					sb.append("Mifare blocks: ");
					sb.append(mifareTag.getBlockCount());
				} catch (Exception e) {
					sb.append("Mifare classic error: " + e.getMessage());
				}
			}

			if (tech.equals(MifareUltralight.class.getName())) {
				sb.append('\n');
				MifareUltralight mifareUlTag = MifareUltralight.get(tag);
				String type = "Unknown";
				switch (mifareUlTag.getType()) {
					case MifareUltralight.TYPE_ULTRALIGHT:
						type = "Ultralight";
						break;
					case MifareUltralight.TYPE_ULTRALIGHT_C:
						type = "Ultralight C";
						break;
				}
				sb.append("Mifare Ultralight type: ");
				sb.append(type);
			}
		}

		return sb.toString();
	}

	private Tag cleanupTag(Tag oTag) {
		if (oTag == null)
			return null;

		String[] sTechList = oTag.getTechList();

		Parcel oParcel = Parcel.obtain();
		oTag.writeToParcel(oParcel, 0);
		oParcel.setDataPosition(0);

		int len = oParcel.readInt();
		byte[] id = null;
		if (len >= 0) {
			id = new byte[len];
			oParcel.readByteArray(id);
		}
		int[] oTechList = new int[oParcel.readInt()];
		oParcel.readIntArray(oTechList);
		Bundle[] oTechExtras = oParcel.createTypedArray(Bundle.CREATOR);
		int serviceHandle = oParcel.readInt();
		int isMock = oParcel.readInt();
		IBinder tagService;
		if (isMock == 0) {
			tagService = oParcel.readStrongBinder();
		} else {
			tagService = null;
		}
		oParcel.recycle();

		int nfca_idx = -1;
		int mc_idx = -1;
		short oSak = 0;
		short nSak = 0;

		for (int idx = 0; idx < sTechList.length; idx++) {
			if (sTechList[idx].equals(NfcA.class.getName())) {
				if (nfca_idx == -1) {
					nfca_idx = idx;
					if (oTechExtras[idx] != null && oTechExtras[idx].containsKey("sak")) {
						oSak = oTechExtras[idx].getShort("sak");
						nSak = oSak;
					}
				} else {
					if (oTechExtras[idx] != null && oTechExtras[idx].containsKey("sak")) {
						nSak = (short) (nSak | oTechExtras[idx].getShort("sak"));
					}
				}
			} else if (sTechList[idx].equals(MifareClassic.class.getName())) {
				mc_idx = idx;
			}
		}

		boolean modified = false;

		if (oSak != nSak) {
			oTechExtras[nfca_idx].putShort("sak", nSak);
			modified = true;
		}

		if (nfca_idx != -1 && mc_idx != -1 && oTechExtras[mc_idx] == null) {
			oTechExtras[mc_idx] = oTechExtras[nfca_idx];
			modified = true;
		}

		if (!modified) {
			return oTag;
		}

		Parcel nParcel = Parcel.obtain();
		nParcel.writeInt(id.length);
		nParcel.writeByteArray(id);
		nParcel.writeInt(oTechList.length);
		nParcel.writeIntArray(oTechList);
		nParcel.writeTypedArray(oTechExtras, 0);
		nParcel.writeInt(serviceHandle);
		nParcel.writeInt(isMock);
		if (isMock == 0) {
			nParcel.writeStrongBinder(tagService);
		}
		nParcel.setDataPosition(0);

		Tag nTag = Tag.CREATOR.createFromParcel(nParcel);

		nParcel.recycle();

		return nTag;
	}

	private long toReversedDec(byte[] bytes) {
		long result = 0;
		long factor = 1;
		for (int i = bytes.length - 1; i >= 0; --i) {
			long value = bytes[i] & 0xffl;
			result += value * factor;
			factor *= 256l;
		}
		return result;
	}

	private String toReversedHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < bytes.length; ++i) {
			if (i > 0) {
				sb.append(":");
			}
			int b = bytes[i] & 0xff;
			if (b < 0x10)
				sb.append('0');
			sb.append(Integer.toHexString(b));
		}
		return sb.toString();
	}

	private String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (int i = bytes.length - 1; i >= 0; --i) {
			int b = bytes[i] & 0xff;
			if (b < 0x10)
				sb.append('0');
			sb.append(Integer.toHexString(b));
			if (i > 0) {
				sb.append(" ");
			}
		}
		return sb.toString();
	}

	private long toDec(byte[] bytes) {
		long result = 0;
		long factor = 1;
		for (int i = 0; i < bytes.length; ++i) {
			long value = bytes[i] & 0xffl;
			result += value * factor;
			factor *= 256l;
		}
		return result;
	}

	@Override
	protected void onNewIntent(Intent intent) {
		/**
		 * This method gets called, when a new Intent gets associated with the current activity instance.
		 * Instead of creating a new activity, onNewIntent will be called. For more information have a look
		 * at the documentation.
		 *
		 * In our case this method gets called, when the user attaches a Tag to the device.
		 */
		super.onNewIntent(intent);
		setIntent(intent);
		resolveIntent(intent);
	}

	public void onDrawerItemClick(AdapterView<?> parent, View view, int position, long id) {}

	public String getPahtToTypeface() {
		return "fonts/Lato-Regular.ttf";
	}
	
	private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            onDrawerItemClick(parent, view, position, id);
            mDrawerLayout.closeDrawers();
        }
    }
	
	/**
	 * Handles the navigation drawers
	 */
	public class Drawer {
		
		private ArrayList<String> mDrawerItemTitles;
		
		public Drawer() {
			mDrawerItemTitles = new ArrayList<String>();
		}
		
		public void addTitle(String title) {
			mDrawerItemTitles.add(title);
		}
		
		public ArrayList<String> getDrawerItemTitles() {
			return mDrawerItemTitles;
		}
	}
}

