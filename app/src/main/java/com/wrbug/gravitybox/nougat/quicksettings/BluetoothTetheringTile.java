/*
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

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.util.concurrent.atomic.AtomicReference;

import com.wrbug.gravitybox.nougat.R;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

public class BluetoothTetheringTile extends QsTile {
    private static final int BT_PROFILE_PAN = 5;
    public static final String ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED";

    private String[] mTetherableBluetoothRegexs;
    private AtomicReference<Object> mBluetoothPan;
    private boolean mBluetoothEnableForTether;
    private boolean mIsListening;

    public BluetoothTetheringTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mBluetoothPan = new AtomicReference<>();
    }

    private String[] getTetherableBluetoothRegexs() {
        if (mTetherableBluetoothRegexs == null) {
            try {
                ConnectivityManager cm = (ConnectivityManager)
                        mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                return (String[]) XposedHelpers.callMethod(cm, "getTetherableBluetoothRegexs");
            } catch (Throwable t) { 
                return new String[0];
            }
        }
        return mTetherableBluetoothRegexs;
    }

    private String[] getTetheringErroredIfaces() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            return (String[]) XposedHelpers.callMethod(cm, "getTetheringErroredIfaces");
        } catch (Throwable t) { 
            return new String[0];
        }
    }

    private BluetoothProfile.ServiceListener mProfileServiceListener =
            new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (DEBUG) log("mProfileServiceListener: onServiceConnected");
            mBluetoothPan.set(proxy);
            if (mBluetoothEnableForTether) {
                setTethering(true);
                mBluetoothEnableForTether = false;
            }
            refreshState();
        }
        @Override
        public void onServiceDisconnected(int profile) {
            if (DEBUG) log("mProfileServiceListener: onServiceDisconnected");
            unregisterServiceListener();
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                if (DEBUG) log("Bluetooth state changed: state=" + state +
                        "; mBluetoothEnableForTether=" + mBluetoothEnableForTether);
                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        registerServiceListener();
                        break;
                    case BluetoothAdapter.STATE_OFF:
                    case BluetoothAdapter.ERROR:
                        unregisterServiceListener();
                        break;
                    default:
                        // ignore transition states
                }
            }
            refreshState();
        }
    };

    private void registerListeners() {
        if (!mIsListening) {
            registerServiceListener();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_TETHER_STATE_CHANGED);
            intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            mContext.registerReceiver(mBroadcastReceiver, intentFilter);
            mIsListening = true;
            if (DEBUG) log("listeners registered");
        }
    }

    private void unregisterListeners() {
        if (mIsListening) {
            unregisterServiceListener();
            mContext.unregisterReceiver(mBroadcastReceiver);
            mIsListening = false;
            if (DEBUG) log("listeners unregistered");
        }
    }

    private void registerServiceListener() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.getState() == BluetoothAdapter.STATE_ON &&
                mBluetoothPan.get() == null) {
            adapter.getProfileProxy(mContext, mProfileServiceListener,
                    BT_PROFILE_PAN);
            if (DEBUG) log("Service listener registered");
        }
    }

    private void unregisterServiceListener() {
        mBluetoothEnableForTether = false;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && mBluetoothPan.get() != null) {
            adapter.closeProfileProxy(BT_PROFILE_PAN, (BluetoothProfile) mBluetoothPan.get());
            mBluetoothPan.set(null);
            if (DEBUG) log("Service listener unregistered");
        }
    }

    private boolean isTetheringOn() {
        try {
            Object pan = mBluetoothPan.get();
            return (pan != null &&
                    (boolean) XposedHelpers.callMethod(pan, "isTetheringOn"));
        } catch (Throwable t) {
            XposedBridge.log(t);
            return false;
        }
    }

    private boolean isInErrorState(int btState) {
        if (btState == BluetoothAdapter.ERROR)
            return true;
        for (String s : getTetheringErroredIfaces()) {
            for (String regex : getTetherableBluetoothRegexs()) {
                if (s.matches(regex)) return true;
            }
        }
        return false;
    }

    private void setTethering(boolean enabled) {
        try {
            Object pan = mBluetoothPan.get();
            if (pan != null) {
                XposedHelpers.callMethod(pan, "setBluetoothTethering", enabled);
                if (DEBUG) log("setTethering: enabled=" + enabled);
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @Override
    public void setListening(boolean listening) {
        if (listening && mEnabled) {
            registerListeners();
        } else {
            unregisterListeners();
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        mState.booleanValue = false;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        int btState = adapter == null ? BluetoothAdapter.ERROR : adapter.getState();
        if (isInErrorState(btState)) {
            mState.label = mGbContext.getString(R.string.qs_tile_bt_tethering_error);
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_bt_tethering_off);
        } else if (btState == BluetoothAdapter.STATE_TURNING_ON ||
                btState == BluetoothAdapter.STATE_TURNING_OFF) {
            mState.label = "---";
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_bt_tethering_off);
        } else if (btState == BluetoothAdapter.STATE_ON && isTetheringOn()) {
            mState.label = mGbContext.getString(R.string.qs_tile_bt_tethering_on);
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_bt_tethering_on);
            mState.booleanValue = true;
        } else {
            mState.label = mGbContext.getString(R.string.qs_tile_bt_tethering_off);
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_bt_tethering_off);
        }

        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        if (mBluetoothEnableForTether)
            return;

        if (isTetheringOn()) {
            setTethering(false);
        } else {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                if (adapter.getState() == BluetoothAdapter.STATE_OFF) {
                    mBluetoothEnableForTether = true;
                    adapter.enable();
                } else if (adapter.getState() == BluetoothAdapter.STATE_ON) {
                    setTethering(true);
                }
            }
        }
        refreshState();
    }

    @Override
    public boolean handleLongClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
        startSettingsActivity(intent);
        return true;
    }

    @Override
    public void handleDestroy() {
        mTetherableBluetoothRegexs = null;
        mBluetoothPan = null;
        super.handleDestroy();
    }
}
