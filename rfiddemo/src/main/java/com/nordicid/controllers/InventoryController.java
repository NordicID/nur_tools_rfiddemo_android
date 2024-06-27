package com.nordicid.controllers;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.TextView;
import com.nordicid.tdt.*;

import androidx.documentfile.provider.DocumentFile;

import com.nordicid.apptemplate.AppTemplate;
import com.nordicid.nurapi.*;

import com.nordicid.rfiddemo.Beeper;
import com.nordicid.rfiddemo.Main;
import com.nordicid.rfiddemo.R;
import com.nordicid.rfiddemo.TraceApp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class InventoryController {

	public class Stats
	{
		final double TAGS_PER_SEC_OVERTIME = 2;

		private AvgBuffer mTagsPerSecBuffer = new AvgBuffer(1000, (int)(TAGS_PER_SEC_OVERTIME * 1000));

		private long mTagsReadTotal = 0;
		private double mTagsPerSec = 0;
		private double mAvgTagsPerSec = 0;
		private double mMaxTagsPerSec = 0;
		private int mInventoryRounds = 0;
		private long mInventoryStartTime = 0;
		private double mTagsFoundInTime = 0;

		public void updateStats(NurEventInventory ev)
		{
			mTagsPerSecBuffer.add(ev.tagsAdded);

			mTagsReadTotal += ev.tagsAdded;

			mTagsPerSec = mTagsPerSecBuffer.getSumValue() / TAGS_PER_SEC_OVERTIME;
			if (getElapsedSecs() > 1)
				mAvgTagsPerSec = mTagsReadTotal / getElapsedSecs();
			else
				mAvgTagsPerSec = mTagsPerSec;

			if (mTagsPerSec > mMaxTagsPerSec)
				mMaxTagsPerSec = mTagsPerSec;

			mInventoryRounds += ev.roundsDone;
		}

		public void start()
		{
			mInventoryStartTime = System.currentTimeMillis();
		}

		public void clear()
		{
			mTagsPerSecBuffer.clear();
			mTagsReadTotal = 0;
			mTagsPerSec = 0;
			mAvgTagsPerSec = 0;
			mMaxTagsPerSec = 0;
			mInventoryRounds = 0;
			mInventoryStartTime = 0;
			mTagsFoundInTime = 0;
		}

		public double getElapsedSecs()
		{
			if (mInventoryStartTime == 0) return 0;
			return (double)(System.currentTimeMillis() - mInventoryStartTime) / 1000.0;
		}

		public long getTagsReadTotal() {
			return mTagsReadTotal;
		}

		public double getTagsPerSec() {
			return mTagsPerSec;
		}

		public double getAvgTagsPerSec() {
			return mAvgTagsPerSec;
		}

		public double getMaxTagsPerSec() {
			return mMaxTagsPerSec;
		}

		public int getInventoryRounds() {
			return mInventoryRounds;
		}

		public double getTagsFoundInTimeSecs() {
			return mTagsFoundInTime;
		}

		public void setTagsFoundInTimeSecs() {
			mTagsFoundInTime = getElapsedSecs();
		}
	}

	public interface InventoryControllerListener {
		public void tagFound(NurTag tag, boolean isNew);
		public void inventoryRoundDone(NurTagStorage storage, int newTagsOffset, int newTagsAdded);
		public void readerDisconnected();
		public void readerConnected();
		public void inventoryStateChanged();
		public void IOChangeEvent(NurEventIOChange event);
	}

	private boolean mInventoryRunning = false;
	private int mAddedUnique = 0;
	private NurApi mApi = null;
	private InventoryControllerListener mInventoryListener = null;
	private NurApiListener mThisClassListener = null;
	private Stats mStats = new Stats();
	private Thread mBeeperThread = null;
	private NurTagStorage mTagStorage = new NurTagStorage();

	//Inventory settings
	private int mDataWords;
	public int mInvType; //0=epc 1=epc+tid 2=epc+user
	public int mTagDataTrans; // 0=HEX 1=GS1 Uri 2=Ascii
	private boolean mAddGpsCoord;
	private String mPath;
	public boolean mTriggerDown;
	private TraceApp mTraceApp;

	private ArrayList<HashMap<String, String>> mListViewAdapterData = new ArrayList<HashMap<String,String>>();

	public NurApiListener getNurApiListener()
	{		
		return mThisClassListener;
	}

	public ArrayList<HashMap<String, String>> getListViewAdapterData()
	{
		return mListViewAdapterData;
	}

	public NurTagStorage getTagStorage()
	{
		return mTagStorage;
	}

	public InventoryController(NurApi na)
	{
		mApi = na;

		mThisClassListener = new NurApiListener()
		{
			@Override 
			public void inventoryStreamEvent(NurEventInventory event) {
				// Make sure listener is set
				if (mInventoryListener == null)
					return;

				// Update stats
				mStats.updateStats(event);

				// Handle inventoried tags
				handleInventoryResult();

				// Restart reading if needed
				if (event.stopped && mInventoryRunning) {
					
					try {
						mApi.startInventoryStream();
						if(mAddGpsCoord)
							AppTemplate.getAppTemplate().refreshLocation();
					}
					catch (Exception err) {
						err.printStackTrace();
					}
				}
			}
			
			@Override
			public void connectedEvent() {
				if (mInventoryListener != null) {
					mInventoryListener.readerConnected();
				}
			}
			
			@Override
			public void disconnectedEvent() {
				if (mInventoryListener != null) {
					mInventoryListener.readerDisconnected();
					stopInventory();
				}
			}

			@Override public void IOChangeEvent(NurEventIOChange event) {
				if (mInventoryListener != null) {
					mInventoryListener.IOChangeEvent(event);
				}
			}
			@Override public void bootEvent(String arg0) {}
			@Override public void clientConnectedEvent(NurEventClientInfo arg0) {}
			@Override public void clientDisconnectedEvent(NurEventClientInfo arg0) {}
			@Override public void deviceSearchEvent(NurEventDeviceInfo arg0) {}
			@Override public void frequencyHopEvent(NurEventFrequencyHop arg0) {}
			@Override public void inventoryExtendedStreamEvent(NurEventInventory arg0) {}
			@Override public void nxpEasAlarmEvent(NurEventNxpAlarm arg0) {}
			@Override public void programmingProgressEvent(NurEventProgrammingProgress arg0) {}
			@Override public void traceTagEvent(NurEventTraceTag arg0) { }
			@Override public void triggeredReadEvent(NurEventTriggeredRead arg0) {}
			@Override public void logEvent(int arg0, String arg1) {}
			@Override public void debugMessageEvent(String arg0) {}
			@Override public void epcEnumEvent(NurEventEpcEnum event) {}
			@Override public void autotuneEvent(NurEventAutotune event) { }
			@Override public void tagTrackingScanEvent(NurEventTagTrackingData event) { }
			@Override public void tagTrackingChangeEvent(NurEventTagTrackingChange event) { }			
		};
	}

	SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy kk:mm:ss");

	@SuppressWarnings("unchecked")
	void handleInventoryResult()
	{
		synchronized (mApi.getStorage())
		{
			HashMap<String, String> tmp;
			NurTagStorage tagStorage = mApi.getStorage();
			int curUniqueCount = mTagStorage.size();

			//Log.i("INV","storageSize=" + Integer.toString(tagStorage.size()));
			// Add tags tp internal tag storage
			for (int i = 0; i < tagStorage.size(); i++) {

				NurTag tag = tagStorage.get(i);
				Log.w("INV","ReadingType=" + mTagDataTrans);

				String hex = tag.getEpcString();
				String transVal = null;

				if (mTagStorage.addTag(tag))
				{
					tmp = new HashMap<String, String>();
					// Add new
					if (mTagDataTrans == 1) {
						try {
							//Check if tag is GS1 coded. Exception fired if not and plain EPC shown.
							//This is TDT (TagDataTranslation) library feature.
							EPCTagEngine engine = new EPCTagEngine(hex);
							//Looks like it is GS1 coded.
							transVal = engine.buildTagURI();
							tmp.put("epc_translated", transVal);
							tmp.put("epc", hex);
						}
						catch (Exception ex) {
							tmp.put("epc_translated", hex);
							tmp.put("epc", hex);
						}
					}
					else if (mTagDataTrans == 2) {
						try {
							// Check if tag in ASCII coded.
							byte[] epc = tag.getEpc();
							boolean nullReached = false;
							boolean validTDT = true;

							for (int j = 0; j < epc.length; j++) {
								if (epc[j] == 0) {
									nullReached = true;
									continue;
								}
								if (epc[j] < 0x20 || epc[j] > 0x7F) {
									validTDT = false;
									break;
								}
								if (nullReached) {
									validTDT = false;
									break;
								}
							}

							if (validTDT) {
								transVal = new String(tag.getEpc());
								tmp.put("epc_translated", transVal);
							}
							else {
								tmp.put("epc_translated", hex);
							}
						}
						catch (Exception ex) {
						Log.i("Error reading ascii: ", ex.toString());
						}
						tmp.put("epc", hex);
					}

					else {
						tmp.put("epc_translated", hex);
						tmp.put("epc", hex);
					}

					tmp.put("rssi", Integer.toString(tag.getRssi()));
					tmp.put("maxrssi", Integer.toString(tag.getRssi()));
					tmp.put("timestamp", Integer.toString(tag.getTimestamp()));
					tmp.put("freq", Integer.toString(tag.getFreq())+" kHz Ch: "+Integer.toString(tag.getChannel()));
					tmp.put("found", "1");
					tmp.put("foundpercent", "100");
					tmp.put("firstseentime", dateFormatter.format(new Date()));
					tmp.put("lastseentime", dateFormatter.format(new Date()));
					tmp.put("invtype",Integer.toString(mInvType));

					if(mInvType > 0) {
						byte[] irdata = tag.getIrData();
						if(irdata != null)
							tmp.put("irdata", NurApi.byteArrayToHexString(irdata));
						else tmp.put("irdata", "");
					}
					else tmp.put("irdata","");

					if(mAddGpsCoord) {
						tmp.put("gps",AppTemplate.getAppTemplate().getLocation());
					}
					//Log.w("INV","Update type=" + mInvType + " epc="+tag.getEpcString() + " ir=" +tmp.get("irdata"));
					tag.setUserdata(tmp);
					mListViewAdapterData.add(tmp);

					if (mInventoryListener != null)
						mInventoryListener.tagFound(tag, true);

				}
				else
				{
					// Update
					tag = mTagStorage.getTag(tag.getEpc());
					tmp = (HashMap<String, String>) tag.getUserdata();
					tmp.put("rssi", Integer.toString(tag.getRssi()));

					String rss = tmp.get("maxrssi");
					int val = Integer.decode(rss);
					if(tag.getRssi()>val)
						tmp.put("maxrssi", Integer.toString(tag.getRssi()));

					tmp.put("timestamp", Integer.toString(tag.getTimestamp()));
					tmp.put("freq", Integer.toString(tag.getFreq())+" kHz (Ch: "+Integer.toString(tag.getChannel())+")");
					tmp.put("found", Integer.toString(tag.getUpdateCount()));
					tmp.put("foundpercent", Integer.toString((int) (((double) tag.getUpdateCount()) / (double) mStats.getInventoryRounds() * 100)));
					tmp.put("lastseentime", dateFormatter.format(new Date()));
					tmp.put("invtype",Integer.toString(mInvType));


					if(mInvType > 0) {
						byte[] irdata = tag.getIrData();
						if(irdata != null)
							tmp.put("irdata", NurApi.byteArrayToHexString(irdata));
						else tmp.put("irdata", "");
					}
					else tmp.put("irdata","");

					if(mAddGpsCoord)
						tmp.put("gps",AppTemplate.getAppTemplate().getLocation());

					//Log.w("INV","Update type=" + mInvType + " epc="+tag.getEpcString() + " ir=" +tmp.get("irdata"));
					if (mInventoryListener != null)
						mInventoryListener.tagFound(tag, false);
				}
			}

			// Clear NurApi tag storage
			tagStorage.clear();

			// Check & report new unique tags
			mAddedUnique = mTagStorage.size() - curUniqueCount;
			if (mAddedUnique > 0)
			{
				mStats.setTagsFoundInTimeSecs();

				// Report round done w/ new unique tags
				if (mInventoryListener != null)
					mInventoryListener.inventoryRoundDone(mTagStorage, curUniqueCount, mAddedUnique);
			}

		}
	}

	public String Export(Context ctx) {

		//Need to export readings first
		LoadInventorySettings();
		return ExportReadings(ctx, mPath);
	}

	public String getUriPath() {
		return mPath;
	}

	public void SaveUriPath(String uriPath)
	{
		SharedPreferences settings = Main.getApplicationPrefences();
		SharedPreferences.Editor settingEditor = null;
		settingEditor = settings.edit();
		settingEditor.putString("FilePath",uriPath);
		settingEditor.apply();
	}

	public void LoadInventorySettings()
	{
		SharedPreferences settings = Main.getApplicationPrefences();

		mDataWords = settings.getInt("DataLength",2);
		mInvType = settings.getInt("InvType",0);
		mTagDataTrans = settings.getInt("TagDataTrans",0);
        mAddGpsCoord = settings.getBoolean("AddGpsCoord",false);
		//mPath = settings.getString("FilePath","content://com.android.externalstorage.documents/tree/primary%3ADownload");
		mPath = settings.getString("FilePath",Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath());

		mTriggerDown = settings.getBoolean("TriggerDown",false);
	}

	public boolean doSingleInventory(Boolean clearReadings) throws Exception {
		if (!mApi.isConnected())
			return false;

		// Make sure antenna autoswitch is enabled
		if (mApi.getSetupSelectedAntenna() != NurApi.ANTENNAID_AUTOSELECT)
			mApi.setSetupSelectedAntenna(NurApi.ANTENNAID_AUTOSELECT);

		// Clear old readings
		if(clearReadings)
			clearInventoryReadings();
		// Perform inventory
		try {
			mApi.inventory();
			// Fetch tags from NUR
			mApi.fetchTags();
		}
		catch (NurApiException ex)
		{
			Log.i("INV", ex.getMessage());
			// Did not get any tags
			if (ex.error == NurApiErrors.NO_TAG)
				return true;

			throw ex;
		}
		// Handle inventoried tags
		handleInventoryResult();

		return true;
	}

	private void PrepareDataInventory()
	{
		NurIRConfig ir = new NurIRConfig();

		ir.IsRunning = true;
		ir.irType = NurApi.IRTYPE_EPCDATA;

		if(mInvType == 1)
			ir.irBank = NurApi.BANK_TID;
		else if(mInvType == 2)
			ir.irBank = NurApi.BANK_USER;
		else
			ir.IsRunning = false;

		ir.irAddr = 0;
		ir.irWordCount = mDataWords;

		try {
			mApi.setIRConfig(ir);
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	public boolean startContinuousInventory() throws Exception {

		if (!mApi.isConnected())
			return false;

		// Enable inventory stream zero reading report
		if ((mApi.getSetupOpFlags() & NurApi.OPFLAGS_INVSTREAM_ZEROS) == 0)
			mApi.setSetupOpFlags(mApi.getSetupOpFlags() | NurApi.OPFLAGS_INVSTREAM_ZEROS);

		// Make sure antenna autoswitch is enabled
		if (mApi.getSetupSelectedAntenna() != NurApi.ANTENNAID_AUTOSELECT)
			mApi.setSetupSelectedAntenna(NurApi.ANTENNAID_AUTOSELECT);

		PrepareDataInventory();

		if(mAddGpsCoord)
			AppTemplate.getAppTemplate().refreshLocation();

        mInventoryRunning = true;
		mApi.startInventoryStream();

		// Clear & start stats
		mStats.clear();
		mStats.start();

		// Start beeper thread
		mBeeperThread = new Thread(mBeeperThreadRunnable);
		mBeeperThread.setPriority(Thread.MIN_PRIORITY);
		mBeeperThread.start();

		// Notify state change
		if (mInventoryListener != null)
			mInventoryListener.inventoryStateChanged();

		return true;
	}

	public boolean isInventoryRunning() {
		return mInventoryRunning;
	}

	public void stopInventory() {
		try {
			mInventoryRunning = false;

			// Stop reading
			if (mApi.isConnected()) {

				mApi.stopInventoryStream();
				NurIRConfig	ir = new NurIRConfig();
				ir.IsRunning = false;
				mApi.setIRConfig(ir);
				mApi.setSetupOpFlags(mApi.getSetupOpFlags() & ~NurApi.OPFLAGS_INVSTREAM_ZEROS);
			}

			// Stop beeper thread
			if (mBeeperThread != null) {
				mBeeperThread.join(5000);
				mBeeperThread = null;
			}

		} catch (Exception err) {
			err.printStackTrace();
		}

		// Notify state change
		if (mInventoryListener != null)
			mInventoryListener.inventoryStateChanged();
	}

	public void setListener(InventoryControllerListener l) {
		mInventoryListener = l;
	}

	private String ExportReadings(Context ctx, String pathString)
	{
		final ArrayList<HashMap<String, String>> tags = mListViewAdapterData;

		try {

			String postfix = "";
			if (mApi.isConnected())
			{
				postfix = mApi.getReaderInfo().altSerial;
				if (postfix.length() == 0) {
					postfix = mApi.getReaderInfo().serial;
				}
				if (postfix.length() != 0) {
					postfix += "_";
				}
			}

			String filename = "epc_export_";
			filename += postfix;
			filename += new SimpleDateFormat("ddMMyyyykkmmss").format(new Date());
			filename += ".csv";

			if(!mPath.startsWith("content")) {
				//Uri not selected yet. Use default  folder (Download)
				File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
				File outputFile = new File(path, filename);
				outputFile.createNewFile();

				FileWriter fileWriter = new FileWriter(outputFile, false);
				if(mInvType == 0) {
					if(mAddGpsCoord==false)
						fileWriter.write("firstseen;lastseen;epc;rssi;maxrssi;found\n");
					else fileWriter.write("firstseen;lastseen;epc;rssi;maxrssi;found;GPSlatitude;GPSlongitude\n");
				}
				else if(mInvType == 1) {
					if(mAddGpsCoord==false)
						fileWriter.write("firstseen;lastseen;epc;tid;rssi;maxrssi;found\n");
					else fileWriter.write("firstseen;lastseen;epc;tid;rssi;maxrssi;found;GPSlatitude;GPSlongitude\n");
				}
				else if(mInvType == 2) {
					if(mAddGpsCoord==false)
						fileWriter.write("firstseen;lastseen;epc;user;rssi;maxrssi;found\n");
					else fileWriter.write("firstseen;lastseen;epc;user;rssi;maxrssi;found;GPSlatitude;GPSlongitude\n");
				}

				for (HashMap<String, String> tag : tags)
				{
					fileWriter.append(tag.get("firstseentime") + ";");
					fileWriter.append(tag.get("lastseentime") + ";");
					fileWriter.append(tag.get("epc_translated") + ";");

					if(mInvType > 0) {
						int iType=Integer.parseInt(tag.get("invtype"));
						if(iType == mInvType) {
							String ir = tag.get("irdata");
							Log.w("INV","Type=" + mInvType + " ir=" + ir +" epc" + tag.get("epc"));
							if (ir == null) ir = "";
							else if (ir.startsWith("null") || ir.length() < 4)
								ir = "";
							ir += ";";
							fileWriter.append(ir);
						} else fileWriter.append(";");
					}

					fileWriter.append(tag.get("rssi") + ";");
					fileWriter.append(tag.get("maxrssi") + ";");
					if(mAddGpsCoord==false)
						fileWriter.append(tag.get("found") + "\n");
					else
					{
						fileWriter.append(tag.get("found") + ";");
						fileWriter.append(tag.get("gps") + "\n");
					}
				}
				fileWriter.close();
				return "";
			}


			Uri treeUri = Uri.parse(mPath);
			//ctx.getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			//ctx.getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
			Log.w("SELECTED",mPath);
			DocumentFile pickedDir = DocumentFile.fromTreeUri(ctx,treeUri);
			Log.w("SELECTED",pickedDir.toString() + " filename=" + filename);
			MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
			DocumentFile newFile = pickedDir.createFile(mimeTypeMap.getMimeTypeFromExtension("csv"), filename);
            Log.w("SELECTED","NewFile=" + newFile.getName());
			OutputStream out = ctx.getContentResolver().openOutputStream(newFile.getUri());

			if(mInvType == 0) {
				if(mAddGpsCoord==false)
					out.write("firstseen;lastseen;epc;rssi;maxrssi;found\n".getBytes());
				else out.write("firstseen;lastseen;epc;rssi;maxrssi;found;GPSlatitude;GPSlongitude\n".getBytes());
			}
			else if(mInvType == 1) {
				if(mAddGpsCoord==false)
					out.write("firstseen;lastseen;epc;tid;rssi;maxrssi;found\n".getBytes());
				else out.write("firstseen;lastseen;epc;tid;rssi;maxrssi;found;GPSlatitude;GPSlongitude\n".getBytes());
			}
			else if(mInvType == 2) {
				if(mAddGpsCoord==false)
					out.write("firstseen;lastseen;epc;user;rssi;maxrssi;found\n".getBytes());
				else out.write("firstseen;lastseen;epc;user;rssi;maxrssi;found;GPSlatitude;GPSlongitude\n".getBytes());
			}

			for (HashMap<String, String> tag : tags)
			{
				String txt = tag.get("firstseentime") + ";";
				out.write(txt.getBytes());
				txt=tag.get("lastseentime") + ";";
				out.write(txt.getBytes());
				txt=tag.get("epc") + ";";
				out.write(txt.getBytes());

				if(mInvType > 0) {
					int iType=Integer.parseInt(tag.get("invtype"));
					if(iType == mInvType) {
						String ir = tag.get("irdata");
						if (ir == null) ir = "";
						else if (ir.startsWith("null") || ir.length() < 4)
							ir = "";
						ir += ";";
						out.write(ir.getBytes());
					} else
					{
						String ir=";";
						out.write(ir.getBytes());

					}
				}

				txt = tag.get("rssi") + ";";
				out.write(txt.getBytes());
				txt = tag.get("maxrssi") + ";";
				out.write(txt.getBytes());

				if(mAddGpsCoord==false) {
					txt = tag.get("found") + "\n";
					out.write(txt.getBytes());
				}
				else
				{
					txt = tag.get("found") + ";";
					out.write(txt.getBytes());
					txt = tag.get("gps") + "\n";
					out.write(txt.getBytes());
				}
			}

			out.close();

		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
			//Toast.makeText(getActivity(), "Error:\n" + e.getMessage(), Toast.LENGTH_SHORT).show();
		}

		return "";
	}


	public void clearInventoryReadings() {

		mAddedUnique = 0;
		mApi.getStorage().clear();
		mTagStorage.clear();
		mStats.clear();
		mListViewAdapterData.clear();

		if (isInventoryRunning())
			mStats.start();
	}
	
	Runnable mBeeperThreadRunnable = new Runnable() {
		@Override
		public void run()
		{
			while (mInventoryRunning)
			{
				if (mAddedUnique > 0) {
					int sleepTime = 100 - mAddedUnique;
					int beepDuration = Beeper.BEEP_40MS;

					if (sleepTime < 40)
						sleepTime = 40;

					Beeper.beep(beepDuration);

					try {
						Thread.sleep(sleepTime);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				else
				{
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	};

	public Stats getStats() {
		return mStats;
	}

	public void showTagDialog(Context ctx, final HashMap<String, String> tagData) {
		//shows dialog and the clicked tags information
		LayoutInflater inflater = (LayoutInflater) ctx.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
		View tagDialogLayout = inflater.inflate(R.layout.dialog_tagdata, null);
		final AlertDialog.Builder builder = new AlertDialog.Builder(ctx);

		builder.setView(tagDialogLayout);

		final TextView epcTextView = (TextView) tagDialogLayout.findViewById(R.id.selected_tag_epc);
		epcTextView.setText(ctx.getString(R.string.dialog_epc)+" "+tagData.get("epc"));

		final TextView dataTextView = (TextView) tagDialogLayout.findViewById(R.id.selected_tag_data);
		if(mInvType == 1)
			dataTextView.setText(ctx.getString(R.string.dialog_tid)+" " + tagData.get("irdata"));
		else if(mInvType == 2)
			dataTextView.setText(ctx.getString(R.string.dialog_user)+" " + tagData.get("irdata"));
		else dataTextView.setText("");

		final TextView rssiTextView = (TextView) tagDialogLayout.findViewById(R.id.selected_tag_rssi);
		rssiTextView.setText(ctx.getString(R.string.dialog_rssi)+" "+tagData.get("rssi"));

		final TextView timestampTextView = (TextView) tagDialogLayout.findViewById(R.id.selected_tag_timestamp);
		timestampTextView.setText(ctx.getString(R.string.dialog_timestamp)+" "+tagData.get("timestamp"));

		final TextView fregTextView = (TextView) tagDialogLayout.findViewById(R.id.selected_tag_freq);
		fregTextView.setText(ctx.getString(R.string.dialog_freg)+" "+tagData.get("freq"));

		final TextView foundTextView = (TextView) tagDialogLayout.findViewById(R.id.selected_tag_found);
		foundTextView.setText(ctx.getString(R.string.dialog_found)+" "+tagData.get("found"));

		final TextView foundPercentTextView = (TextView) tagDialogLayout.findViewById(R.id.selected_tag_foundpercent);
		foundPercentTextView.setText(ctx.getString(R.string.dialog_found_precent)+" "+tagData.get("foundpercent"));

		final AlertDialog dialog = builder.create();

		//close button made in "Android L" style. See the layout
		final Button closeDialog = (Button) tagDialogLayout.findViewById(R.id.selected_tag_close_button);
		closeDialog.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});

		final Button locateTag = (Button) tagDialogLayout.findViewById(R.id.selected_tag_locate_button);
		locateTag.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();

				// Set TDT format to ASCII if selcted in settings
				if (mTagDataTrans == 2) {
					TraceApp.setStartParams(tagData.get("epc_translated"), true);
				}
				else {
					TraceApp.setStartParams(tagData.get("epc"), true);
				}
				AppTemplate.getAppTemplate().setApp("Locate");
			}
		});

		final Context _ctx = ctx;
		final ArrayList<HashMap<String, String>> tags = mListViewAdapterData;

		dialog.show();
	}
}
