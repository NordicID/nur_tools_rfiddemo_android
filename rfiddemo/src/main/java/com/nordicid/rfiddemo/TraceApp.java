package com.nordicid.rfiddemo;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import com.nordicid.apptemplate.SubApp;
import com.nordicid.controllers.InventoryController;
import com.nordicid.controllers.TraceTagController;
import com.nordicid.controllers.TraceTagController.TraceTagListener;
import com.nordicid.controllers.TraceTagController.TracedTagInfo;
import com.nordicid.nuraccessory.NurAccessoryExtension;
import com.nordicid.nurapi.NurApiListener;
import com.nordicid.nurapi.NurEventIOChange;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class TraceApp extends SubApp {

	static String mLocatableEpc;
	static boolean mAutoStart = false;

	private SimpleAdapter mFoundTagsListViewAdapter;

	private Button mStartStopLocating;
	private EditText mLocatableEpcEditText;
	private EditText mPctText;
	
	private ProgressBar mProgressBar;
	private ListView mFoundTagsListView;
	private RelativeLayout mEmptyListViewNotice;
	private TraceTagController mTraceController;
	private InventoryController mInventoryController;
	public Spinner mLocateTagDataTransSpinner;
	private int mLocateTagDataTrans;
	SharedPreferences settings = null;
	SharedPreferences.Editor settingEditor = null;
	ObjectAnimator mAnimator = null;
	int mLastVal = 0;

	public TraceApp() {
		super();
		mLocatableEpc="";
		mTraceController = new TraceTagController(getNurApi());
		mInventoryController = new InventoryController(getNurApi());

		mTraceController.setListener(new TraceTagListener() {
			@Override
			public void traceTagEvent(TracedTagInfo data) {
				int scaledRssi = data.scaledRssi;

				mPctText.setText(scaledRssi + "%");

				if (mAnimator != null) {
					mLastVal = (int) mAnimator.getAnimatedValue();
					mAnimator.cancel();
				} else {
					mAnimator = ObjectAnimator.ofInt(mProgressBar, "progress", mLastVal, scaledRssi);
					mAnimator.setInterpolator(new LinearInterpolator());
				}

				mAnimator.setIntValues(mLastVal, scaledRssi);
				mAnimator.setDuration(300);
				mAnimator.start();

				mLastVal = scaledRssi;
			}

			@Override
			public void readerDisconnected() {
				stopTrace();
			}

			@Override
			public void readerConnected() {
			}

			@Override
			public void IOChangeEvent(NurEventIOChange event) {
				// Handle BLE trigger
				if (event.source == NurAccessoryExtension.TRIGGER_SOURCE && event.direction == 0)
				{
					if (mTraceController.isTracingTag())
						stopTrace();
					else {
						startTrace();
					}
				}
			}
		});
	}

	private void UpdateIRViews()
	{
		// If Settings TDT format GS1 is enabled
		if (mInventoryController.mTagDataTrans == 1) {
			//Log.d("LOC", "isGS1");
			mLocateTagDataTransSpinner.setEnabled(false);
			mLocateTagDataTrans = 0;
			mLocateTagDataTransSpinner.setSelection(0);
		}
		else {
			mLocateTagDataTransSpinner.setEnabled(true);
		}

		if (mFoundTagsListViewAdapter != null) {
			try {
				if (!mInventoryController.doSingleInventory(true)) {
					Toast.makeText(getActivity(), getString(R.string.reader_connection_error), Toast.LENGTH_SHORT).show();
				}
				else if (mInventoryController.getTagStorage().size() == 0) {
					Toast.makeText(getActivity(), "No tags found", Toast.LENGTH_SHORT).show();
				}

			} catch (Exception e) {
				Toast.makeText(getActivity(), getString(R.string.reader_error), Toast.LENGTH_SHORT).show();
			}
			mFoundTagsListViewAdapter.notifyDataSetChanged();

		}
	}

	@Override
	public int getTileIcon() {
		return R.drawable.ic_locate;
	}

	@Override
	public String getAppName() {
		return "Locate";
	}

	@Override
	public int getLayout() {
		return R.layout.app_locate;
	}

	@Override
	public void onVisibility(boolean val)
	{
		if (!val)
		{
			stopTrace();
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		mLocateTagDataTransSpinner = (Spinner) view.findViewById(R.id.locate_read_spinner);
		settings = Main.getApplicationPrefences();
		settingEditor = settings.edit();

		mInventoryController.mTagDataTrans = settings.getInt("TagDataTrans",0);
		UpdateIRViews();

		mStartStopLocating = addButtonBarButton(getString(R.string.start), new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (mTraceController.isTracingTag()) {
					stopTrace();
				}
				else {
					startTrace();
				}
			}
		});
		addButtonBarButton(getString(R.string.refresh_list), new OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					mInventoryController.LoadInventorySettings();
					if (!mInventoryController.doSingleInventory(true)) {
						Toast.makeText(getActivity(), getString(R.string.reader_connection_error), Toast.LENGTH_SHORT).show();
					}
					else if (mInventoryController.getTagStorage().size() == 0) {
						Toast.makeText(getActivity(), "No tags found", Toast.LENGTH_SHORT).show();
					}

				} catch (Exception e) {
					Toast.makeText(getActivity(), getString(R.string.reader_error), Toast.LENGTH_SHORT).show();
				}
				mFoundTagsListViewAdapter.notifyDataSetChanged();
			}
		});

		mProgressBar = (ProgressBar) view.findViewById(R.id.progressBar);
		mFoundTagsListView = (ListView) view.findViewById(R.id.tags_listview);
		mEmptyListViewNotice = (RelativeLayout) view.findViewById(R.id.listview_empty);
		mLocatableEpcEditText = (EditText) view.findViewById(R.id.locate_epc_edittext);
		mPctText = (EditText) view.findViewById(R.id.pct_text);

		// Do not save EditText state
		mLocatableEpcEditText.setSaveEnabled(false);

		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			LayoutParams lp = mProgressBar.getLayoutParams();
			WindowManager wm = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
			Display display = wm.getDefaultDisplay();
			Point size = new Point();
			display.getSize(size);
			lp.width = size.x / 3;
			lp.height = size.x / 3;
			mProgressBar.setLayoutParams(lp);
			mPctText.setLayoutParams(lp);
		}
		else {
			LayoutParams lp = mProgressBar.getLayoutParams();
			WindowManager wm = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
			Display display = wm.getDefaultDisplay();
			Point size = new Point();
			display.getSize(size);
			lp.width = size.y / 3;
			lp.height = size.y / 3;
			mProgressBar.setLayoutParams(lp);
			mPctText.setLayoutParams(lp);
		}

		mLocateTagDataTransSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

				mLocateTagDataTrans = pos;
				settingEditor.putInt("LocateTagDataTrans",mLocateTagDataTrans);
				settingEditor.apply();

				UpdateIRViews();
			}
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		mFoundTagsListViewAdapter = new SimpleAdapter(getActivity(), mInventoryController.getListViewAdapterData(), R.layout.taglist_row, new String[] {"epc_translated","rssi"}, new int[] {R.id.tagText,R.id.rssiText});
		mFoundTagsListView.setEmptyView(mEmptyListViewNotice);
		mFoundTagsListView.setAdapter(mFoundTagsListViewAdapter);
		mFoundTagsListView.setCacheColorHint(0);

		mFoundTagsListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				@SuppressWarnings("unchecked")
				HashMap<String,String> selectedTagData = (HashMap<String, String>) mFoundTagsListView.getItemAtPosition(position);

				String epc;
				if (mLocateTagDataTrans == 1) {
					epc = selectedTagData.get("epc_translated");
				}
				else {
					epc = selectedTagData.get("epc");
				}

				mLocatableEpcEditText.setText(epc);
				mLocatableEpc = epc;

				if (mTraceController.isTracingTag()) {
					mTraceController.setTagTrace(mLocatableEpc);
				}
			}
		});

		mLocatableEpcEditText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				// If Locate TDT is ASCII
				if (mLocateTagDataTrans == 1) {
					String epcText = mLocatableEpcEditText.getText().toString();
					byte[] epcBytes = epcText.getBytes(StandardCharsets.UTF_8);
					boolean isAsciiValid = TraceTagController.isValidAscii(epcBytes);

					if (isAsciiValid) {
						mLocatableEpcEditText.setBackgroundColor(Color.rgb(230, 230, 230));
					} else {
						Log.d("ASCII", "Is not ASCII-valid: " + epcText);
					}
				} else {
					mLocatableEpcEditText.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
					mLocatableEpc = "";
				}
			}

			@Override
			public void afterTextChanged(Editable editable) {
				// If Locate TDT is ASCII
				if (mLocateTagDataTrans == 1) {
					mLocatableEpcEditText.setBackgroundColor(Color.rgb(230, 230, 230));

					String epcText = mLocatableEpcEditText.getText().toString();
					byte[] epcBytes = epcText.getBytes(StandardCharsets.UTF_8);

					if (TraceTagController.isValidAscii(epcBytes)) {
						mLocatableEpcEditText.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
						mLocatableEpc = mTraceController.asciiToHex(epcText);
						return;
					}
					else {
						mLocatableEpcEditText.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
						return;
					}
				}
				else {

					String tmp = mLocatableEpcEditText.getText().toString().replaceAll("[^a-fA-F_0-9]", "");

					if (!tmp.equals(mLocatableEpcEditText.getText().toString())) {
						mLocatableEpcEditText.setText(tmp);
						mLocatableEpcEditText.setSelection(mLocatableEpcEditText.getText().length());
					}

					if(mLocatableEpcEditText.getText().toString().length() == 0 ) {
						mLocatableEpcEditText.setBackgroundColor(Color.rgb(230,230,230));
						return;
						}

						if ((mLocatableEpcEditText.getText().toString().length() % 4) != 0) {
							mLocatableEpcEditText.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
							mLocatableEpc="";
						}
						else {
							mLocatableEpcEditText.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
							mLocatableEpc = mLocatableEpcEditText.getText().toString();
						}
				}
			}
		});

		mFoundTagsListViewAdapter.notifyDataSetChanged();

		if (mLocatableEpc != null)
			mLocatableEpcEditText.setText(mLocatableEpc);
		else
			mLocatableEpcEditText.setText(null);

		mAnimator = ObjectAnimator.ofInt(mProgressBar, "progress", mLastVal, mLastVal);
		mAnimator.setInterpolator(new LinearInterpolator());

		if (mAutoStart) {
			startTrace();
		}
		mAutoStart = false;
	}

	static public void setStartParams(String epc, boolean autostart)
	{
		mLocatableEpc = epc;
		mAutoStart = autostart;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		//super.onSaveInstanceState(null);
	}

	private void startTrace() {

		try {
			if (!mTraceController.isTracingTag())
			{
				if (mLocateTagDataTrans == 1) {
					String epcText = mLocatableEpcEditText.getText().toString();
					mLocatableEpc = mTraceController.asciiToHex(epcText);
				}
				if (mLocatableEpc == null)
					Toast.makeText(getActivity(), getString(R.string.locatable_epc_hint), Toast.LENGTH_SHORT).show();
				else if (!getNurApi().isConnected())
					Toast.makeText(getActivity(), getString(R.string.reader_connection_error), Toast.LENGTH_SHORT).show();
				else  if (mTraceController.startTagTrace(mLocatableEpc)) {
					mStartStopLocating.setText(getString(R.string.stop));
					mLocatableEpcEditText.setEnabled(false);
					mLocatableEpcEditText.setBackgroundColor(Color.rgb(230, 255, 230));
				}
				else
					Toast.makeText(getActivity(), "Invalid EPC", Toast.LENGTH_SHORT).show();
			}
		}
		catch (Exception ex) {
			Toast.makeText(getActivity(), getString(R.string.reader_error), Toast.LENGTH_SHORT).show();
		}
	}
	
	private void stopTrace() {
		if (mTraceController.isTracingTag())
		{
			mTraceController.stopTagTrace();
			mStartStopLocating.setText(getString(R.string.start));
			mLocatableEpcEditText.setEnabled(true);
			mLocatableEpcEditText.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
		}
	}
	
	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public boolean onFragmentBackPressed() {
		
		if (mTraceController.isTracingTag()) {
			stopTrace();
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		
		if (mTraceController.isTracingTag()) {
			stopTrace();
		}
	}
	
	@Override
	public NurApiListener getNurApiListener()
	{
		return mTraceController.getNurApiListener();
	}
}
