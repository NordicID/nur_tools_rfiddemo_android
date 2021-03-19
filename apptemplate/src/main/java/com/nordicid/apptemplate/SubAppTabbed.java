package com.nordicid.apptemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;

import com.nordicid.nurapi.NurApi;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.util.Log;
import android.view.View;

public class SubAppTabbed extends SubApp {

	protected static final Field sChildFragmentManagerField; //For a workaround if nested fragments used.

	private SubAppTabbedPagerAdapter mPagerAdapter;
	protected ViewPager mPager;
	
	protected int mPagerID = -1;
	protected ArrayList<Fragment> mFragments = new ArrayList<Fragment>();
	protected ArrayList<String> mFragmentNames = new ArrayList<String>();

	public SubAppTabbed() {
		super();
	}
	
	protected int onGetFragments(ArrayList<Fragment> fragments, ArrayList<String> fragmentNames) throws Exception
	{
		throw new Exception("Not implemented");
	}

	protected String onGetPreferredTab()
	{
		return "";
	}

	@Override
	public void onResume() {
		super.onResume();

		//Log.d("SubAppTabbed", "onResume");

		final String preferredTab = onGetPreferredTab();

		if(!preferredTab.isEmpty())
		{
			final int tabIndex = mFragmentNames.indexOf(preferredTab);

			if (tabIndex >= 0) {
				AppTemplate.getAppTemplate().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Log.d("TAB","PreferredTab=" + preferredTab +  " index=" + tabIndex);
						mPager.setCurrentItem(tabIndex, false);
					}
				});
			}
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		// Log.d("SubAppTabbed", "onViewCreated");

		try {
			//if (mPagerID == -1)
			//{
				mFragments.clear();
				mFragmentNames.clear();
				mPagerID = onGetFragments(mFragments, mFragmentNames);	
			//}
			mPagerAdapter = new SubAppTabbedPagerAdapter(getChildFragmentManager());
			mPager = (ViewPager) view.findViewById(mPagerID);
			mPager.setAdapter(mPagerAdapter);



		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	//Custom pager adapter class
	public class SubAppTabbedPagerAdapter extends FragmentStatePagerAdapter {

		public SubAppTabbedPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		//which fragment should be visible
		@Override
		public Fragment getItem(int position) {
			if (position < mFragments.size())
				return mFragments.get(position);
			return null;
		}
		
		//titles
		@Override
		public CharSequence getPageTitle(int position) {
			if (position < mFragmentNames.size())
				return mFragmentNames.get(position);
			return null;
		}

		@Override
		public int getCount() {
			return mFragmentNames.size();
		}

		
		@Override
		public void restoreState(Parcelable arg0, ClassLoader arg1) {
			/*Does nothing else that prevents crash. "Restore" the subapps 
			 state in the "host fragment". SubApps only draws the UI  */	
		}

	}
	
	/////Workaround when using nested fragments/////
	static {
        Field f = null;
        try {
            f = Fragment.class.getDeclaredField("mChildFragmentManager");
            f.setAccessible(true);
        } catch (NoSuchFieldException e) {}
        sChildFragmentManagerField = f;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (sChildFragmentManagerField != null) {
            try {
                sChildFragmentManagerField.set(this, null);
            } catch (Exception e) {}
        }
    }
    /////////////////////////////////////////////////
}
