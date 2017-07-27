/*
 * Copyright (C) 2013 The SlimRoms Project
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.ModStatusBar;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.Utils;
import com.wrbug.gravitybox.nougat.managers.SysUiManagers;
import com.wrbug.gravitybox.nougat.managers.GpsStatusMonitor;

import de.robv.android.xposed.XSharedPreferences;

public class LocationTileSlimkat extends QsTile implements GpsStatusMonitor.Listener {

    private static final Intent LOCATION_SETTINGS_INTENT = 
            new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);

    public static final Integer[] LOCATION_SETTINGS = new Integer[] {
        Settings.Secure.LOCATION_MODE_BATTERY_SAVING,
        Settings.Secure.LOCATION_MODE_SENSORS_ONLY,
        Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
    };

    private int mLastActiveMode;
    private Object mDetailAdapter;
    private List<Integer> mLocationList = new ArrayList<Integer>();
    private boolean mQuickMode;

    public LocationTileSlimkat(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mLastActiveMode = getLocationMode();
        if(mLastActiveMode == Settings.Secure.LOCATION_MODE_OFF) {
            mLastActiveMode = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
        }
    }

    @Override
    public void initPreferences() {
        mQuickMode = Utils.isOxygenOs35Rom() ? true :
                mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_LOCATION_TILE_QUICK_MODE, false);

        super.initPreferences();
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_LOCATION_TILE_QUICK_MODE)) {
                mQuickMode = intent.getBooleanExtra(GravityBoxSettings.EXTRA_LOCATION_TILE_QUICK_MODE, false);
            }
        }

        super.onBroadcastReceived(context, intent);
    }

    private void registerListener() {
        if (SysUiManagers.GpsMonitor != null) {
            SysUiManagers.GpsMonitor.registerListener(this);
            if (DEBUG) log(getKey() + ": Location Status Listener registered");
        }
    }

    private void unregisterListener() {
        if (SysUiManagers.GpsMonitor != null) {
            SysUiManagers.GpsMonitor.unregisterListener(this);
            if (DEBUG) log(getKey() + ": Location Status Listener unregistered");
        }
    }

    @Override
    public void setListening(boolean listening) {
        if (listening && mEnabled) {
            registerListener();
        } else {
            unregisterListener();
        }
    }

    private boolean isLocationEnabled() {
        return (getLocationMode() != Settings.Secure.LOCATION_MODE_OFF);
    }

    private int getLocationMode() {
        return (SysUiManagers.GpsMonitor == null ? 0 :
            SysUiManagers.GpsMonitor.getLocationMode());
    }

    private void setLocationMode(int mode) {
        if (SysUiManagers.GpsMonitor != null) {
            SysUiManagers.GpsMonitor.setLocationMode(mode);
        }
    }

    private void switchLocationMode() {
        int currentMode = getLocationMode();
        switch (currentMode) {
            case Settings.Secure.LOCATION_MODE_OFF:
                setLocationMode(Settings.Secure.LOCATION_MODE_BATTERY_SAVING);
                break;
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                setLocationMode(Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
                break;
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                setLocationMode(Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
                break;
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                setLocationMode(Settings.Secure.LOCATION_MODE_OFF);
                break;
        }
    }

    private void setLocationEnabled(boolean enabled) {
        if (SysUiManagers.GpsMonitor != null) {
            // Store last active mode if we are switching off
            // so we can restore it at the next enable
            if(!enabled) {
                mLastActiveMode = getLocationMode();
            }
            final int mode = enabled ? mLastActiveMode : Settings.Secure.LOCATION_MODE_OFF;
            SysUiManagers.GpsMonitor.setLocationMode(mode);
        }
    }

    @Override
    public void onLocationModeChanged(int mode) {
        if (DEBUG) log(getKey() + ": onLocationModeChanged: mode=" + mode);
        refreshState();
    }

    @Override
    public void onGpsEnabledChanged(boolean gpsEnabled) { }

    @Override
    public void onGpsFixChanged(boolean gpsFixed) { }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.booleanValue = true;
        mState.visible = true;
        int locationMode = getLocationMode();
        switch (locationMode) {
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_location_on);
                break;
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_location_battery_saving);
                break;
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_location_on);
                break;
            case Settings.Secure.LOCATION_MODE_OFF:
                mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_location_off);
                mState.booleanValue = false;
                break;
        }
        mState.label = GpsStatusMonitor.getModeLabel(mContext, locationMode);

        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        if (mQuickMode) {
            switchLocationMode();
        } else if (supportsDualTargets()) {
            setLocationEnabled(!isLocationEnabled());
        } else {
            showDetail(true);
        }
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        if (supportsDualTargets() || Utils.isOxygenOs35Rom()) {
            startSettingsActivity(LOCATION_SETTINGS_INTENT);
        } else if (mQuickMode) {
            showDetail(true);
        } else {
            setLocationEnabled(!isLocationEnabled());
        }
        return true;
    }

    @Override
    public boolean supportsHideOnChange() {
        return supportsDualTargets();
    }

    @Override
    public boolean handleSecondaryClick() {
        showDetail(true);
        return true;
    }

    @Override
    public void handleDestroy() {
        mDetailAdapter = null;
        mLocationList.clear();
        mLocationList = null;
        super.handleDestroy();
    }

    @Override
    public Object getDetailAdapter() {
        if (mDetailAdapter == null) {
            mDetailAdapter = QsDetailAdapterProxy.createProxy(
                    mContext.getClassLoader(), new LocationDetailAdapter());
        }
        return mDetailAdapter;
    }

    private class AdvancedLocationAdapter extends ArrayAdapter<Integer> {
        public AdvancedLocationAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_single_choice, mLocationList);
        }

        @SuppressLint("ViewHolder")
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            CheckedTextView label = (CheckedTextView) inflater.inflate(
                    android.R.layout.simple_list_item_single_choice, parent, false);
            label.setText(GpsStatusMonitor.getModeLabel(mContext, getItem(position)));
            return label;
        }
    }

    private class LocationDetailAdapter implements QsDetailAdapterProxy.Callback, AdapterView.OnItemClickListener {

        private AdvancedLocationAdapter mAdapter;
        private QsDetailItemsList mDetails;

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            setLocationMode((Integer) parent.getItemAtPosition(position));
        }

        @Override
        public int getTitle() {
            return mContext.getResources().getIdentifier("quick_settings_location_label",
                    "string", ModStatusBar.PACKAGE_NAME);
        }

        @Override
        public Boolean getToggleState() {
            boolean state = isLocationEnabled();
            rebuildLocationList(state);
            return state;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) throws Throwable {
            if (mDetails == null) {
                mDetails = QsDetailItemsList.create(context, parent);
                mDetails.setEmptyState(R.drawable.ic_qs_location_off,
                        GpsStatusMonitor.getModeLabel(mContext, Settings.Secure.LOCATION_MODE_OFF));
                mAdapter = new AdvancedLocationAdapter(context);
                mDetails.setAdapter(mAdapter);
    
                final ListView list = mDetails.getListView();
                list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
                list.setOnItemClickListener(this);
            }

            return mDetails.getView();
        }

        @Override
        public Intent getSettingsIntent() {
            return LOCATION_SETTINGS_INTENT;
        }

        @Override
        public void setToggleState(boolean state) {
            setLocationEnabled(state);
            rebuildLocationList(state);
            fireToggleStateChanged(state);
            if (!state) {
                showDetail(false);
            }
        }

        private void rebuildLocationList(boolean populate) {
            mLocationList.clear();
            if (populate) {
                mLocationList.addAll(Arrays.asList(LOCATION_SETTINGS));
                mDetails.getListView().setItemChecked(mAdapter.getPosition(
                        getLocationMode()), true);
            }
            mAdapter.notifyDataSetChanged();
        }
    }
}
