/*
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
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

import com.wrbug.gravitybox.nougat.ConnectivityServiceWrapper;
import com.wrbug.gravitybox.nougat.GravityBoxResultReceiver;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.GravityBoxResultReceiver.Receiver;

import de.robv.android.xposed.XSharedPreferences;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;

public class NfcTile extends QsTile {
    private static final String ACTION_ADAPTER_STATE_CHANGED = 
            "android.nfc.action.ADAPTER_STATE_CHANGED";
    private static final String EXTRA_STATE = "android.nfc.extra.ADAPTER_STATE";

    private Handler mHandler;
    private GravityBoxResultReceiver mReceiver;
    private int mNfcState = ConnectivityServiceWrapper.NFC_STATE_UNKNOWN;
    private boolean mIsReceiving;

    private BroadcastReceiver mStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int newState = intent.getIntExtra(EXTRA_STATE,
                    ConnectivityServiceWrapper.NFC_STATE_UNKNOWN);
            if (mNfcState != newState) {
                mNfcState = newState;
                refreshState();
            }
        }
    };

    public NfcTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mHandler = new Handler();
        mReceiver = new GravityBoxResultReceiver(mHandler);
        mReceiver.setReceiver(new Receiver() {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                if (resultData != null && resultData.containsKey("nfcState")) {
                    int newState = resultData.getInt("nfcState");
                    if (mNfcState != newState) {
                        mNfcState = newState;
                        refreshState();
                    }
                }
            }
        });
    }

    private void registerNfcReceiver() {
        if (!mIsReceiving) {
            IntentFilter intentFilter = new IntentFilter(ACTION_ADAPTER_STATE_CHANGED);
            mContext.registerReceiver(mStateChangeReceiver, intentFilter);
            mIsReceiving = true;
            getNfcState();
            if (DEBUG) log(getKey() + ": registerNfcReceiver");
        }
    }

    private void unregisterNfcReceiver() {
        if (mIsReceiving) {
            mContext.unregisterReceiver(mStateChangeReceiver);
            mIsReceiving = false;
            if (DEBUG) log(getKey() + ": unregisterNfcReceiver");
        }
    }

    @Override
    public void setListening(boolean listening) {
        if (listening && mEnabled) {
            registerNfcReceiver();
        } else {
            unregisterNfcReceiver();
        }
    }

    protected void toggleState() {
        switch (mNfcState) {
            case ConnectivityServiceWrapper.NFC_STATE_ON:
                mNfcState = ConnectivityServiceWrapper.NFC_STATE_TURNING_OFF;
                refreshState();
                mContext.sendBroadcast(new Intent(ConnectivityServiceWrapper.ACTION_TOGGLE_NFC));
                break;
            case ConnectivityServiceWrapper.NFC_STATE_OFF:
                mNfcState = ConnectivityServiceWrapper.NFC_STATE_TURNING_ON;
                refreshState();
                mContext.sendBroadcast(new Intent(ConnectivityServiceWrapper.ACTION_TOGGLE_NFC));
                break;
        }
    }

    private void getNfcState() {
        Intent i = new Intent(ConnectivityServiceWrapper.ACTION_GET_NFC_STATE);
        i.putExtra("receiver", mReceiver);
        mContext.sendBroadcast(i);
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        mState.booleanValue = false;
        switch (mNfcState) {
        case ConnectivityServiceWrapper.NFC_STATE_ON:
            mState.booleanValue = true;
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_nfc_on);
            mState.label = mGbContext.getString(R.string.quick_settings_nfc_on);
            break;
        case ConnectivityServiceWrapper.NFC_STATE_OFF:
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_nfc_off);
            mState.label = mGbContext.getString(R.string.quick_settings_nfc_off);
            break;
        case ConnectivityServiceWrapper.NFC_STATE_TURNING_ON:
        case ConnectivityServiceWrapper.NFC_STATE_TURNING_OFF:
        default:
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_nfc_trans);
            mState.label = "----";
        }

        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        toggleState();
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        startSettingsActivity(Settings.ACTION_NFC_SETTINGS);
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mHandler = null;
        mReceiver = null;
    }
}
