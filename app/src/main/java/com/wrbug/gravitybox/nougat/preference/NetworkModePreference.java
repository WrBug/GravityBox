/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wrbug.gravitybox.nougat.preference;

import java.util.ArrayList;
import java.util.List;

import com.wrbug.gravitybox.nougat.PhoneWrapper;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.adapters.IIconListAdapterItem;
import com.wrbug.gravitybox.nougat.adapters.IconListAdapter;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class NetworkModePreference extends DialogPreference implements OnItemClickListener {
    private static final String TAG = "GB:NetworkModePreference";

    private Context mContext;
    private ListView mListView;
    private String mDefaultSummaryText;
    private Resources mResources;

    public NetworkModePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mResources = mContext.getResources();
        mDefaultSummaryText = (String) getSummary();

        setDialogLayoutResource(R.layout.network_mode_preference);
        setPositiveButtonText(null);
    }

    @Override
    protected void onBindDialogView(View view) {
        mListView = (ListView) view.findViewById(R.id.icon_list);
        mListView.setOnItemClickListener(this);

        super.onBindView(view);

        setData();
    }

    @Override
    public void onDismiss(DialogInterface dialog) { }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, -1);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            int value = getPersistedInt(-1);
            String summary = getSummaryFromValue(value);
            setSummary(summary);
        } else {
            setValue(-1);
            setSummary(mDefaultSummaryText);
        }
    } 

    public void setDefaultSummary(String summary) {
        mDefaultSummaryText = summary;
    }

    private void setData() {
        final List<IIconListAdapterItem> list = new ArrayList<IIconListAdapterItem>();
        list.add(new NetworkModeItem(R.drawable.shortcut_unknown, -1));
        list.add(new NetworkModeItem(R.drawable.shortcut_network_mode_2g, PhoneWrapper.NT_GSM_ONLY));
        list.add(new NetworkModeItem(R.drawable.shortcut_network_mode_2g3g, PhoneWrapper.NT_GSM_WCDMA_AUTO));
        list.add(new NetworkModeItem(R.drawable.shortcut_network_mode_3g2g, PhoneWrapper.NT_WCDMA_PREFERRED));
        list.add(new NetworkModeItem(R.drawable.shortcut_network_mode_3g, PhoneWrapper.NT_WCDMA_ONLY));
        list.add(new NetworkModeItem(R.drawable.shortcut_network_mode_cdma_evdo, PhoneWrapper.NT_CDMA_EVDO));
        list.add(new NetworkModeItem(R.drawable.shortcut_network_mode_cdma, PhoneWrapper.NT_CDMA_ONLY));
        list.add(new NetworkModeItem(R.drawable.shortcut_network_mode_evdo, PhoneWrapper.NT_EVDO_ONLY));
        list.add(new NetworkModeItem(R.drawable.shortcut_network_mode_2g3g, PhoneWrapper.NT_GLOBAL));
        list.add(new NetworkModeItem(R.drawable.shortcut_network_mode_lte_cdma, PhoneWrapper.NT_LTE_CDMA_EVDO));
        list.add(new NetworkModeItem(R.drawable.shortcut_network_mode_lte_gsm, PhoneWrapper.NT_LTE_GSM_WCDMA));
        list.add(new NetworkModeItem(R.drawable.shortcut_network_mode_lte_global, 
                PhoneWrapper.NT_LTE_CMDA_EVDO_GSM_WCDMA));

        mListView.setAdapter(new IconListAdapter(mContext, list));
        ((IconListAdapter)mListView.getAdapter()).notifyDataSetChanged();
    }

    private void setValue(int value){
        persistInt(value);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        NetworkModeItem item = (NetworkModeItem) parent.getItemAtPosition(position);
        final int networkMode = item.getNetworkMode();
        setValue(networkMode);
        setSummary(getSummaryFromValue(networkMode));
        getDialog().dismiss();
    }

    private String getSummaryFromValue(int value) {
        return (value == -1 ? 
                mDefaultSummaryText : PhoneWrapper.getNetworkModeNameFromValue(value));
    }

    class NetworkModeItem implements IIconListAdapterItem {
        private int mIconResId;
        private int mNetworkMode;

        public NetworkModeItem(int iconResId, int networkMode) {
            mIconResId = iconResId;
            mNetworkMode = networkMode;
        }

        @Override
        public String getText() {
            return mNetworkMode == -1 ? 
                    mContext.getString(R.string.smart_radio_action_undefined) :
                        PhoneWrapper.getNetworkModeNameFromValue(mNetworkMode);
        }

        @Override
        public String getSubText() {
            return null;
        }

        @Override
        public Drawable getIconLeft() {
            return mIconResId == 0 ? null : mResources.getDrawable(mIconResId);
        }

        @Override
        public Drawable getIconRight() {
            return null;
        }

        public int getNetworkMode() {
            return mNetworkMode;
        }
    }
}
