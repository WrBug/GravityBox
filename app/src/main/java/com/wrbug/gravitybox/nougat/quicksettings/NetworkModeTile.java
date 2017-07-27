/*
 * Copyright (C) 2016 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.wrbug.gravitybox.nougat.quicksettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.ModQsTiles;
import com.wrbug.gravitybox.nougat.PhoneWrapper;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.Utils;

import de.robv.android.xposed.XSharedPreferences;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

public class NetworkModeTile extends QsTile {

    private static class NetworkMode {
        int value;
        boolean enabled;
        int labelRes;
        int iconRes;
        NetworkMode(int v, int l, int i) {
            value = v;
            labelRes = l;
            iconRes = i;
        }
    }

    private static NetworkMode[] MODES = new NetworkMode[] {
            new NetworkMode(0, R.string.network_mode_0, R.drawable.ic_qs_3g2g_on),
            new NetworkMode(1, R.string.network_mode_1, R.drawable.ic_qs_2g_on),
            new NetworkMode(2, R.string.network_mode_2, R.drawable.ic_qs_3g_on),
            new NetworkMode(3, R.string.network_mode_3, R.drawable.ic_qs_2g3g_on),
            new NetworkMode(4, R.string.network_mode_4, R.drawable.ic_qs_2g3g_on),
            new NetworkMode(5, R.string.network_mode_5, R.drawable.ic_qs_2g_on),
            new NetworkMode(6, R.string.network_mode_6, R.drawable.ic_qs_3g_on),
            new NetworkMode(7, R.string.network_mode_7, R.drawable.ic_qs_2g3g_on),
            new NetworkMode(8, R.string.network_mode_8, R.drawable.ic_qs_lte),
            new NetworkMode(9, R.string.network_mode_9, R.drawable.ic_qs_lte),
            new NetworkMode(10, R.string.network_mode_10, R.drawable.ic_qs_lte),
            new NetworkMode(11, R.string.network_mode_11, R.drawable.ic_qs_lte),
            new NetworkMode(12, R.string.network_mode_12, R.drawable.ic_qs_lte),
            new NetworkMode(13, R.string.network_mode_13, R.drawable.ic_qs_3g_on),
            new NetworkMode(14, R.string.network_mode_14, R.drawable.ic_qs_3g_on),
            new NetworkMode(15, R.string.network_mode_15, R.drawable.ic_qs_lte),
            new NetworkMode(16, R.string.network_mode_16, R.drawable.ic_qs_2g3g_on),
            new NetworkMode(17, R.string.network_mode_17, R.drawable.ic_qs_lte),
            new NetworkMode(18, R.string.network_mode_18, R.drawable.ic_qs_2g3g_on),
            new NetworkMode(19, R.string.network_mode_19, R.drawable.ic_qs_lte),
            new NetworkMode(20, R.string.network_mode_20, R.drawable.ic_qs_lte),
            new NetworkMode(21, R.string.network_mode_21, R.drawable.ic_qs_2g3g_on),
            new NetworkMode(22, R.string.network_mode_22, R.drawable.ic_qs_lte)
    };

    private int mNetworkType;
    private boolean mIsMsim;
    private int mSimSlot = 0;
    private boolean mIsReceiving;
    private List<NetworkMode> mModeList = new ArrayList<>();
    private Object mDetailAdapter;
    private boolean mQuickMode;

    public NetworkModeTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mIsMsim = PhoneWrapper.hasMsimSupport();
    }

    @Override
    public void initPreferences() {
        super.initPreferences();

        Set<String> smodes = mPrefs.getStringSet(
                GravityBoxSettings.PREF_KEY_NM_TILE_ENABLED_MODES,
                new HashSet<String>(Arrays.asList(new String[] { "0", "1", "2", "10" })));
        List<String> lmodes = new ArrayList<String>(smodes);
        Collections.sort(lmodes);
        int modes[] = new int[lmodes.size()];
        for (int i=0; i<lmodes.size(); i++) {
            modes[i] = Integer.valueOf(lmodes.get(i));
        }
        if (DEBUG) log(getKey() + ": onPreferenceInitialize: modes=" + Arrays.toString(modes));
        setEnabledModes(modes);

        mQuickMode = Utils.isOxygenOs35Rom() ? true :
                mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_NM_TILE_QUICK_MODE, false);

        if (mIsMsim) {
            try {
                mSimSlot = Integer.valueOf(mPrefs.getString(
                        GravityBoxSettings.PREF_KEY_QS_NETWORK_MODE_SIM_SLOT, "0"));
            } catch (NumberFormatException nfe) {
                log(getKey() + ": Invalid value for SIM Slot preference: " + nfe.getMessage());
            }
            if (DEBUG) log(getKey() + ": mSimSlot = " + mSimSlot);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        super.onBroadcastReceived(context, intent);

        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_NM_TILE_ENABLED_MODES)) {
                int[] modes = intent.getIntArrayExtra(GravityBoxSettings.EXTRA_NM_TILE_ENABLED_MODES);
                if (DEBUG) log(getKey() + ": onBroadcastReceived: modes=" + Arrays.toString(modes));
                setEnabledModes(modes);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_NM_TILE_QUICK_MODE)) {
                mQuickMode = intent.getBooleanExtra(GravityBoxSettings.EXTRA_NM_TILE_QUICK_MODE, false);
            }
        }

        if (intent.getAction().equals(PhoneWrapper.ACTION_NETWORK_TYPE_CHANGED)) {
            String tag = intent.getStringExtra(PhoneWrapper.EXTRA_RECEIVER_TAG);
            if (tag == null || tag.equals(TAG)) {
                int phoneId = intent.getIntExtra(PhoneWrapper.EXTRA_PHONE_ID, 0);
                if (phoneId == mSimSlot) {
                    mNetworkType = intent.getIntExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                            PhoneWrapper.getDefaultNetworkType());
                    if (DEBUG) log(getKey() + ": ACTION_NETWORK_TYPE_CHANGED: mNetworkType = " + mNetworkType);
                    if (mIsReceiving) {
                        refreshState();
                    }
                }
            }
        }

        if (mIsMsim && intent.getAction().equals(
                GravityBoxSettings.ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED)) {
            mSimSlot = intent.getIntExtra(GravityBoxSettings.EXTRA_SIM_SLOT, 0);
            if (DEBUG) log(getKey() + ": received ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED broadcast: " +
                                "mSimSlot = " + mSimSlot);
        }
    }

    private void setEnabledModes(int[] modes) {
        // disable all first
        for (NetworkMode nm : MODES) {
            nm.enabled = false;
        }

        // enable only those present in the list
        if (modes != null && modes.length > 0) {
            for (int i=0; i<modes.length; i++) {
                NetworkMode nm = findNetworkMode(modes[i]);
                if (nm != null) {
                    nm.enabled = true;
                }
            }
        }
    }

    private int findIndexForMode(int mode) {
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].value == mode)
                return i;
        }
        return -1;
    }

    private NetworkMode findNetworkMode(int mode) {
        int index = findIndexForMode(mode);
        return index >= 0 && index < MODES.length ? MODES[index] : null;
    }

    @Override
    public void setListening(boolean listening) {
        if (DEBUG) log(getKey() + ": setListening(" + listening + ")");
        if (listening && mEnabled) {
            if (mIsReceiving)
                return;
            if (DEBUG) log(getKey() + ": mNetworkType=" + mNetworkType + "; DefaultNetworkType=" + 
                    PhoneWrapper.getDefaultNetworkType());
            mIsReceiving = true;
            Intent intent = new Intent(PhoneWrapper.ACTION_GET_CURRENT_NETWORK_TYPE);
            intent.putExtra(PhoneWrapper.EXTRA_PHONE_ID, mSimSlot);
            intent.putExtra(PhoneWrapper.EXTRA_RECEIVER_TAG, TAG);
            mContext.sendBroadcast(intent);
        } else {
            mIsReceiving = false;
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;

        NetworkMode nm = findNetworkMode(mNetworkType);
        if (nm != null) {
            mState.label = stripLabel(mGbContext.getString(nm.labelRes));
            mState.icon = mGbContext.getDrawable(nm.iconRes);
        } else {
            mState.label = mGbContext.getString(R.string.network_mode_unknown);
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_unexpected_network);
        }

        if (mIsMsim) {
            mState.label += " (" + String.valueOf(mSimSlot+1) + ")";
        }

        super.handleUpdateState(state, arg);
    }

    @Override
    public boolean supportsHideOnChange() {
        return false;
    }

    private void setNetworkMode(int mode) {
        Intent i = new Intent(PhoneWrapper.ACTION_CHANGE_NETWORK_TYPE);
        i.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE, mode);
        mContext.sendBroadcast(i);
    }

    private void switchNetworkMode() {
        int currentIndex = findIndexForMode(mNetworkType);
        final int startIndex = currentIndex;
        do {
            if (++currentIndex >= MODES.length) {
                currentIndex = 0;
            }
        } while(!MODES[currentIndex].enabled &&
                currentIndex != startIndex);

        if (currentIndex != startIndex) {
            setNetworkMode(MODES[currentIndex].value);
        }

        super.handleClick();
    }

    private String stripLabel(String label) {
        if (label == null) return null;

        int index = label.lastIndexOf("(");
        return index > 0 ? label.substring(0, index-1) : label;
    }

    @Override
    public void handleClick() {
        if (mQuickMode) {
            switchNetworkMode();
        } else {
            showDetail(true);
        }
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        if (mQuickMode && !Utils.isOxygenOs35Rom()) {
            showDetail(true);
        } else if (mIsMsim) {
            Intent intent = new Intent(GravityBoxSettings.ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED);
            intent.putExtra(GravityBoxSettings.EXTRA_SIM_SLOT, mSimSlot == 0 ? 1 : 0);
            mContext.sendBroadcast(intent);
        } else {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName("com.android.phone", "com.android.phone.Settings");
            startSettingsActivity(intent);
        }
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mModeList.clear();
        mModeList = null;
        mDetailAdapter = null;
    }

    @Override
    public Object getDetailAdapter() {
        if (mDetailAdapter == null) {
            mDetailAdapter = QsDetailAdapterProxy.createProxy(
                    mContext.getClassLoader(), new ModeDetailAdapter());
        }
        return mDetailAdapter;
    }

    private class ModeAdapter extends ArrayAdapter<NetworkMode> {
        public ModeAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_single_choice, mModeList);
        }

        @SuppressLint("ViewHolder")
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            CheckedTextView label = (CheckedTextView) inflater.inflate(
                    android.R.layout.simple_list_item_single_choice, parent, false);
            NetworkMode nm = getItem(position);
            label.setText(mGbContext.getString(nm.labelRes));
            return label;
        }
    }

    private class ModeDetailAdapter implements QsDetailAdapterProxy.Callback, AdapterView.OnItemClickListener {

        private ModeAdapter mAdapter;
        private QsDetailItemsList mDetails;

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            setNetworkMode(((NetworkMode) parent.getItemAtPosition(position)).value);
        }

        @Override
        public int getTitle() {
            return ModQsTiles.RES_IDS.NM_TITLE;
        }

        @Override
        public Boolean getToggleState() {
            rebuildModeList();
            return null;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) throws Throwable {
            if (mDetails == null) {
                mDetails = QsDetailItemsList.create(context, parent);
                mDetails.setEmptyState(0, null);
                mAdapter = new ModeAdapter(context);
                mDetails.setAdapter(mAdapter);
    
                final ListView list = mDetails.getListView();
                list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
                list.setOnItemClickListener(this);
            }

            return mDetails.getView();
        }

        @Override
        public Intent getSettingsIntent() {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName("com.android.phone", "com.android.phone.Settings");
            return intent;
        }

        @Override
        public void setToggleState(boolean state) {
            // noop
        }

        private void rebuildModeList() {
            mModeList.clear();
            NetworkMode selected = null;
            for (NetworkMode nm : MODES) {
                if (nm.enabled) {
                    mModeList.add(nm);
                    if (nm.value == mNetworkType) {
                        selected = nm;
                    }
                }
            }
            if (selected != null) {
                mDetails.getListView().setItemChecked(mAdapter.getPosition(selected), true);
            }
            mAdapter.notifyDataSetChanged();
        }
    }
}
