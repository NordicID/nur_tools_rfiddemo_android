package com.nordicid.rfiddemo;

import java.util.ArrayList;
import java.util.HashMap;

import com.nordicid.apptemplate.SubApp;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.os.Bundle;

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

public class NfcApp extends SubApp {

	final private String TAG = "NFC";
	private ListView mTagsListView;
	private SimpleAdapter mTagsListViewAdapter = null;
	private ArrayList<HashMap<String, String>> mTagSourceHash;
	private Button mClearButton = null;
	private TextView mNfcHdr;

	public NfcApp() {
		super();
		mTagSourceHash = new ArrayList<HashMap<String, String>>();
		Log.w(TAG,"NfcApp constructor" );
	}

	public void addNFCItem(String idhex,String iddec,String tech) {

		HashMap<String,String> hashMap=new HashMap<>();//create a hashmap to store the data in key value pair
		hashMap.put("idhex",idhex);
		hashMap.put("iddec",iddec);
		hashMap.put("tech",tech);
		if(mTagSourceHash.size()==1 && mTagSourceHash.get(0).get("idhex").startsWith("Ready"))
		{
			mTagSourceHash.clear();	//Clear introduction line
		}

		mTagSourceHash.add(hashMap);//add the hashmap into arrayList
		if(mTagsListViewAdapter != null)
			mTagsListViewAdapter.notifyDataSetChanged();
	}

	public void setNFCHashMapArray(ArrayList<HashMap<String, String>> newSrc) {
		mTagSourceHash = newSrc;
		if(mTagsListViewAdapter != null)
			mTagsListViewAdapter.notifyDataSetChanged();
	}

	@Override
	public String getAppName() {
		return "NFC";
	}

	@Override
	public int getTileIcon() {
		return R.drawable.ic_nfc;
	}

	@Override
	public int getLayout() {
		return R.layout.app_nfc;
	}

	@Override
	public void onViewCreated(View view, Bundle  savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		Log.w(TAG,"onViewCreated" );

		mClearButton = (Button) view.findViewById(R.id.buttonNfcClear);
		mNfcHdr = (TextView)view.findViewById(R.id.nfc_texthdr);
		mTagsListView = (ListView) view.findViewById(R.id.nfc_listview);

		mTagsListViewAdapter = new SimpleAdapter(
				getActivity(),
				mTagSourceHash,
				R.layout.nfclist_row,
				new String[] {"idhex","iddec","tech"},
				new int[] {R.id.nfcIDHexText, R.id.nfcIDDecText,R.id.nfcTechText});


		mTagsListView.setAdapter(mTagsListViewAdapter);
		mTagsListView.setCacheColorHint(0);




		mClearButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
			mTagsListView.setAdapter(null);
			mTagSourceHash = new ArrayList<HashMap<String, String>>();
			mTagsListViewAdapter.notifyDataSetChanged();
			}
		});

		NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(getContext());

		if (!mNfcAdapter.isEnabled()) {
			Toast.makeText(getActivity(), "NFC disabled!", Toast.LENGTH_LONG).show();
			mNfcHdr.setText("NFC adapter disabled");
			mNfcHdr.setTextColor(Color.RED);
			if(mTagSourceHash.size()==1 && mTagSourceHash.get(0).get("idhex").startsWith("Ready")) {
				mTagSourceHash.clear();
			}
		}
		else
		{
			mNfcHdr.setText("NFC tags");
			mNfcHdr.setTextColor(Color.BLACK);
			if(mTagSourceHash.size()==0) {
				//No items.
				addNFCItem("Ready to read NFC tags.", "Tap a NFC compatible tag","with the back of your device");
			}
		}

		mTagsListViewAdapter.notifyDataSetChanged();

	}

}
