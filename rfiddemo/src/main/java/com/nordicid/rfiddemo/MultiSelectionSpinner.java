package com.nordicid.rfiddemo;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Toast;


public class MultiSelectionSpinner extends Spinner {
    String[] _items = null;
    boolean[] mSelection = null;
    boolean[] mSelectionAtStart = null;
    String _itemsAtStart = null;

    ArrayAdapter<String> simple_adapter;

    AlertDialog mAlertDialog;

    OnMultiChoiceClickListener mMultiSelListener = new OnMultiChoiceClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
            if (mSelection != null && which < mSelection.length) {
                if (!mIsMultiSel) {
                    if (isChecked) {
                        for (int n=0; n<mSelection.length; n++) {
                            boolean val = (n == which);
                            ((AlertDialog) dialog).getListView().setItemChecked(n, val);
                            mSelection[n] = val;
                        }
                    } else {
                        ((AlertDialog) dialog).getListView().setItemChecked(which, true);
                        mSelection[which] = true;
                    }
                } else {
                    mSelection[which] = isChecked;
                }
                simple_adapter.clear();
                simple_adapter.add(buildSelectedItemString());
            } else {
                throw new IllegalArgumentException(
                        "Argument 'which' is out of bounds.");
            }
        }
    };

    public void setMultiSel(boolean isMultiSel) {
        this.mIsMultiSel = isMultiSel;
    }

    boolean mIsMultiSel = true;

    public MultiSelectionSpinner(Context context) {
        super(context);

        simple_adapter = new ArrayAdapter<String>(context,
                android.R.layout.simple_spinner_item);
        super.setAdapter(simple_adapter);
    }

    public MultiSelectionSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);

        simple_adapter = new ArrayAdapter<String>(context,
                android.R.layout.simple_spinner_item);
        super.setAdapter(simple_adapter);
    }

    Drawable getProgressBarIndeterminate() {
        final int[] attrs = {android.R.attr.indeterminateDrawable};
        final int attrs_indeterminateDrawable_index = 0;
        TypedArray a = getContext().obtainStyledAttributes(android.R.style.Widget_ProgressBar, attrs);
        try {
            return a.getDrawable(attrs_indeterminateDrawable_index);
        } finally {
            a.recycle();
        }
    }

    Runnable mFetchContentRunnable = null;
    boolean mFetchContentResult = false;

    public void setFetchContentRunnable(Runnable getContent) {
        mFetchContentRunnable = getContent;
    }

    public void setFetchContentResult(boolean res) {
        mFetchContentResult = res;
    }

    private class FetchContentAsyncTask extends AsyncTask<Void, Integer, Boolean> {
        ProgressDialog mProgressDialog;

        @Override
        protected Boolean doInBackground(Void... params) {
            mFetchContentRunnable.run();
            return mFetchContentResult;
        }

        @Override
        protected void onPreExecute() {
            mFetchContentResult = false;
            mProgressDialog = ProgressDialog.show(Main.getInstance(),
                    "Loading...",
                    "Please wait");
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            mProgressDialog.dismiss();
            if (result) {
                showAlertDialog();
            } else {
                Toast.makeText(Main.getInstance(), "Error fetching content", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
        }
    }

    void showAlertDialog()
    {
        simple_adapter.clear();
        simple_adapter.add(buildSelectedItemString());

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

        _itemsAtStart = getSelectedItemsAsString();
        builder.setMultiChoiceItems(_items, mSelection, mMultiSelListener);

        builder.setPositiveButton("Submit", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                System.arraycopy(mSelection, 0, mSelectionAtStart, 0, mSelection.length);

                if (MultiSelectionSpinner.this.getOnItemSelectedListener() != null)
                    MultiSelectionSpinner.this.getOnItemSelectedListener().onItemSelected(null, null, 0, 0);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                simple_adapter.clear();
                simple_adapter.add(_itemsAtStart);
                System.arraycopy(mSelectionAtStart, 0, mSelection, 0, mSelectionAtStart.length);
            }
        });

        mAlertDialog = builder.create();
        mAlertDialog.show();
    }

    @Override
    public boolean performClick() {
        if (mFetchContentRunnable != null) {
            FetchContentAsyncTask task = new FetchContentAsyncTask();
            task.execute();
        } else {
            showAlertDialog();
        }

        return true;
    }

    @Override
    public void setAdapter(SpinnerAdapter adapter) {
        throw new RuntimeException(
                "setAdapter is not supported by MultiSelectSpinner.");
    }

    public void setItems(String[] items) {
        _items = items;
        mSelection = new boolean[_items.length];
        mSelectionAtStart = new boolean[_items.length];
        Arrays.fill(mSelection, false);
        mSelection[0] = true;
        mSelectionAtStart[0] = true;

        if (Looper.myLooper() == Looper.getMainLooper()) {
            simple_adapter.clear();
            simple_adapter.add(_items[0]);
        }
    }

    public void setItems(List<String> items) {
        setItems(items.toArray(new String[items.size()]));
    }

    public void setSelection(String[] selection) {
        for (int i = 0; i < mSelection.length; i++) {
            mSelection[i] = false;
            mSelectionAtStart[i] = false;
        }
        for (String sel : selection) {
            for (int j = 0; j < _items.length; ++j) {
                if (_items[j].equals(sel)) {
                    mSelection[j] = true;
                    mSelectionAtStart[j] = true;
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            simple_adapter.clear();
            simple_adapter.add(buildSelectedItemString());
        }
    }

    public void setSelection(List<String> selection) {
        setSelection(selection.toArray(new String[selection.size()]));
    }

    @Override
	public void setSelection(int index) {
        setSelectionI(Arrays.asList(index));
    }

    public void setSelection(int[] selectedIndices) {
        for (int i = 0; i < mSelection.length; i++) {
            mSelection[i] = false;
            mSelectionAtStart[i] = false;
        }
        for (int index : selectedIndices) {
            if (index >= 0 && index < mSelection.length) {
                mSelection[index] = true;
                mSelectionAtStart[index] = true;
            } else {
                throw new IllegalArgumentException("Index " + index
                        + " is out of bounds.");
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            simple_adapter.clear();
            simple_adapter.add(buildSelectedItemString());
        }
    }

    public void setSelectionI(List<Integer> selectedIndices) {
        for (int i = 0; i < mSelection.length; i++) {
            mSelection[i] = false;
            mSelectionAtStart[i] = false;
        }
        for (int index : selectedIndices) {
            if (index >= 0 && index < mSelection.length) {
                mSelection[index] = true;
                mSelectionAtStart[index] = true;
            } else {
                throw new IllegalArgumentException("Index " + index
                        + " is out of bounds.");
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            simple_adapter.clear();
            simple_adapter.add(buildSelectedItemString());
        }
    }

    public List<String> getSelectedStrings() {
        List<String> selection = new LinkedList<String>();
        for (int i = 0; i < _items.length; ++i) {
            if (mSelection[i]) {
                selection.add(_items[i]);
            }
        }
        return selection;
    }

    public List<Integer> getSelectedIndices() {
        List<Integer> selection = new LinkedList<Integer>();
        for (int i = 0; i < _items.length; ++i) {
            if (mSelection[i]) {
                selection.add(i);
            }
        }
        return selection;
    }

    private String buildSelectedItemString() {
        StringBuilder sb = new StringBuilder();
        boolean foundOne = false;

        for (int i = 0; i < _items.length; ++i) {
            if (mSelection[i]) {
                if (foundOne) {
                    sb.append(", ");
                }
                foundOne = true;

                sb.append(_items[i]);
            }
        }
        if (!foundOne) {
            sb.append(_items[0]);
        }
        return sb.toString();
    }

    public String getSelectedItemsAsString() {
        StringBuilder sb = new StringBuilder();
        boolean foundOne = false;

        for (int i = 0; i < _items.length; ++i) {
            if (mSelection[i]) {
                if (foundOne) {
                    sb.append(", ");
                }
                foundOne = true;
                sb.append(_items[i]);
            }
        }
        return sb.toString();
    }

}