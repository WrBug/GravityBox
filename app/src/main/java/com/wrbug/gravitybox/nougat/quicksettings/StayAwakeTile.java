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
import java.util.concurrent.TimeUnit;

import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.ModQsTiles;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.Utils;

import de.robv.android.xposed.XSharedPreferences;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

public class StayAwakeTile extends QsTile {
    private static final int NEVER_SLEEP = Integer.MAX_VALUE;
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static ScreenTimeout[] SCREEN_TIMEOUT = new ScreenTimeout[] {
        new ScreenTimeout(15000, R.string.stay_awake_15s),
        new ScreenTimeout(30000, R.string.stay_awake_30s),
        new ScreenTimeout(60000, R.string.stay_awake_1m),
        new ScreenTimeout(120000, R.string.stay_awake_2m),
        new ScreenTimeout(300000, R.string.stay_awake_5m),
        new ScreenTimeout(600000, R.string.stay_awake_10m),
        new ScreenTimeout(1800000, R.string.stay_awake_30m),
        new ScreenTimeout(NEVER_SLEEP, R.string.stay_awake_on),
    };

    private SettingsObserver mSettingsObserver;
    private int mCurrentTimeout;
    private int mPreviousTimeout;
    private int mDefaultTimeout;
    private List<ScreenTimeout> mModeList = new ArrayList<>();
    private Object mDetailAdapter;
    private boolean mQuickMode;
    private boolean mAutoReset;

    private static class ScreenTimeout {
        final int mMillis;
        final int mLabelResId;
        boolean mEnabled;

        public ScreenTimeout(int millis, int labelResId) {
            mMillis = millis;
            mLabelResId = labelResId;
            mEnabled = false;
        }
    }

    public StayAwakeTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_stayawake_on);
        mSettingsObserver = new SettingsObserver(new Handler());

        getCurrentState();
        mPreviousTimeout = mCurrentTimeout == NEVER_SLEEP ?
                FALLBACK_SCREEN_TIMEOUT_VALUE : mCurrentTimeout;
    }

    private void getCurrentState() {
        mCurrentTimeout = Settings.System.getInt(mContext.getContentResolver(), 
                Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE);
        if (DEBUG) log(getKey() + ": getCurrentState: mCurrentTimeout=" + mCurrentTimeout);
    }

    @Override
    public void setListening(boolean listening) {
        if (listening && mEnabled) {
            final int prevTimeout = mCurrentTimeout;
            getCurrentState();
            // this means user most likely changed screen timeout in Android Display settings
            // so we better reset our stored default value if non-zero
            if (prevTimeout != mCurrentTimeout && mDefaultTimeout > 0) {
                mDefaultTimeout = mCurrentTimeout;
            }
            mSettingsObserver.observe();
            if (DEBUG) log(getKey() + ": observer registered");
        } else {
            mSettingsObserver.unobserve();
            if (DEBUG) log(getKey() + ": observer unregistered");
        }
    }

    @Override
    public void initPreferences() {
        super.initPreferences();

        Set<String> smodes = mPrefs.getStringSet(
                GravityBoxSettings.PREF_STAY_AWAKE_TILE_MODE,
                new HashSet<String>(Arrays.asList(new String[] { "0", "1", "2", "3", "4", "5", "6", "7" })));
        List<String> lmodes = new ArrayList<String>(smodes);
        Collections.sort(lmodes);
        int modes[] = new int[lmodes.size()];
        for (int i = 0; i < lmodes.size(); i++) {
            modes[i] = Integer.valueOf(lmodes.get(i));
        }
        if (DEBUG) log(getKey() + ": initPreferences: modes=" + modes);
        updateSettings(modes);

        mQuickMode = Utils.isOxygenOs35Rom() ? true :
                mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_STAY_AWAKE_TILE_QUICK_MODE, false);
        mAutoReset = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_STAY_AWAKE_TILE_AUTO_RESET, false);
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        super.onBroadcastReceived(context, intent);

        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_SA_MODE)) {
                int[] modes = intent.getIntArrayExtra(GravityBoxSettings.EXTRA_SA_MODE);
                if (DEBUG) log(getKey() + ": onBroadcastReceived: modes=" + modes);
                updateSettings(modes);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_SA_QUICK_MODE)) {
                mQuickMode = intent.getBooleanExtra(GravityBoxSettings.EXTRA_SA_QUICK_MODE, false);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_SA_AUTO_RESET)) {
                mAutoReset = intent.getBooleanExtra(GravityBoxSettings.EXTRA_SA_AUTO_RESET, false);
            }
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            if (mAutoReset && mDefaultTimeout > 0) {
                setScreenOffTimeout(mDefaultTimeout);
            }
            mDefaultTimeout = 0;
            if (DEBUG) log(getKey() + ": screen turned off");
        }
    }

    private void updateSettings(int[] modes) {
        for (ScreenTimeout s : SCREEN_TIMEOUT) {
            s.mEnabled = (s.mMillis == NEVER_SLEEP);
        }
        for (int i=0; i<modes.length; i++) {
            int index = modes[i];
            ScreenTimeout s = index < SCREEN_TIMEOUT.length ? SCREEN_TIMEOUT[index] : null;
            if (s != null) {
                s.mEnabled = true;
            }
        }
    }

    private void setScreenOffTimeout(int millis) {
        if (millis == NEVER_SLEEP && mCurrentTimeout != NEVER_SLEEP) {
            mPreviousTimeout = mCurrentTimeout;
        }
        if (mAutoReset && mDefaultTimeout == 0) {
            mDefaultTimeout = mCurrentTimeout == NEVER_SLEEP ?
                    FALLBACK_SCREEN_TIMEOUT_VALUE : mCurrentTimeout;
            if (DEBUG) log(getKey() + ": mDefaultTimeout=" + mDefaultTimeout);
        }
        mCurrentTimeout = millis;
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, mCurrentTimeout);
    }

    private void toggleStayAwake() {
        int currentIndex = getIndexFromValue(mCurrentTimeout);
        if (currentIndex == -1) currentIndex = SCREEN_TIMEOUT.length-1;
        final int startIndex = currentIndex;
        do {
            if (++currentIndex >= SCREEN_TIMEOUT.length) {
                currentIndex = 0;
            }
        } while(!SCREEN_TIMEOUT[currentIndex].mEnabled &&
                    startIndex != currentIndex);
        if (DEBUG) log(getKey() + ": mCurrentTimeoutIndex = " + currentIndex);

        if (startIndex != currentIndex) {
            setScreenOffTimeout(SCREEN_TIMEOUT[currentIndex].mMillis);
        }
    }

    private int getIndexFromValue(int value) {
        for (int i = 0; i < SCREEN_TIMEOUT.length; i++) {
            if (SCREEN_TIMEOUT[i].mMillis == value) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        int currentIndex = getIndexFromValue(mCurrentTimeout);
        if (currentIndex == -1) {
            mState.label = mCurrentTimeout == NEVER_SLEEP ?
                    mGbContext.getString(R.string.stay_awake_on) :
                    String.format("%ds", TimeUnit.MILLISECONDS.toSeconds(mCurrentTimeout));
        } else {
            mState.label = mGbContext.getString(SCREEN_TIMEOUT[currentIndex].mLabelResId);
        }

        super.handleUpdateState(state, arg);
    }

    @Override
    public boolean supportsHideOnChange() {
        return false;
    }

    @Override
    public void handleClick() {
        if (mQuickMode) {
            toggleStayAwake();
        } else {
            showDetail(true);
        }
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        if (mCurrentTimeout == NEVER_SLEEP) {
            setScreenOffTimeout(mPreviousTimeout);
        } else {
            setScreenOffTimeout(NEVER_SLEEP);
        }
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mSettingsObserver = null;
        mModeList.clear();
        mModeList = null;
        mDetailAdapter = null;
    }

    class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT), 
                    false, this);
        }

        void unobserve() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            getCurrentState();
            refreshState();
        }
    }

    @Override
    public Object getDetailAdapter() {
        if (mDetailAdapter == null) {
            mDetailAdapter = QsDetailAdapterProxy.createProxy(
                    mContext.getClassLoader(), new ModeDetailAdapter());
        }
        return mDetailAdapter;
    }

    private class ModeAdapter extends ArrayAdapter<ScreenTimeout> {
        public ModeAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_single_choice, mModeList);
        }

        @SuppressLint("ViewHolder")
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            CheckedTextView label = (CheckedTextView) inflater.inflate(
                    android.R.layout.simple_list_item_single_choice, parent, false);
            ScreenTimeout st = getItem(position);
            label.setText(mGbContext.getString(st.mLabelResId));
            return label;
        }
    }

    private class ModeDetailAdapter implements QsDetailAdapterProxy.Callback, AdapterView.OnItemClickListener {

        private ModeAdapter mAdapter;
        private QsDetailItemsList mDetails;

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            ScreenTimeout st = (ScreenTimeout) parent.getItemAtPosition(position);
            setScreenOffTimeout(st.mMillis);
        }

        @Override
        public int getTitle() {
            return ModQsTiles.RES_IDS.SA_TITLE;
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
            return new Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS);
        }

        @Override
        public void setToggleState(boolean state) {
            // noop
        }

        private void rebuildModeList() {
            mDetails.getListView().clearChoices();
            mModeList.clear();
            int index = getIndexFromValue(mCurrentTimeout);
            ScreenTimeout current = index >= 0 ? SCREEN_TIMEOUT[index] : null;
            ScreenTimeout selected = null;
            for (ScreenTimeout st : SCREEN_TIMEOUT) {
                if (st.mEnabled) {
                    mModeList.add(st);
                    if (st == current) {
                        selected = st;
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
