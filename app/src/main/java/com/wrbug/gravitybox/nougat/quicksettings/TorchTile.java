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

import com.wrbug.gravitybox.nougat.GravityBoxResultReceiver;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.TorchService;
import com.wrbug.gravitybox.nougat.GravityBoxResultReceiver.Receiver;

import de.robv.android.xposed.XSharedPreferences;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;

public class TorchTile extends QsTile {
    private int mTorchStatus = TorchService.TORCH_STATUS_OFF;
    private boolean mIsReceiving;
    private GravityBoxResultReceiver mReceiver;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(TorchService.ACTION_TORCH_STATUS_CHANGED) &&
                    intent.hasExtra(TorchService.EXTRA_TORCH_STATUS)) {
                mTorchStatus = intent.getIntExtra(TorchService.EXTRA_TORCH_STATUS,
                        TorchService.TORCH_STATUS_OFF);
                refreshState();
            }
        }
    };

    public TorchTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mReceiver = new GravityBoxResultReceiver(new Handler());
        mReceiver.setReceiver(new Receiver() {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                final int oldState = mTorchStatus;
                mTorchStatus = resultData.getInt(TorchService.EXTRA_TORCH_STATUS);
                if (mTorchStatus != oldState) { 
                    refreshState();
                    if (DEBUG) log(getKey() + ": onReceiveResult: mTorchStatus=" + mTorchStatus);
                }
            }
        });
    }

    private void registerReceiver() {
        if (!mIsReceiving) {
            IntentFilter intentFilter = new IntentFilter(
                    TorchService.ACTION_TORCH_STATUS_CHANGED);
            mContext.registerReceiver(mBroadcastReceiver, intentFilter);
            mIsReceiving = true;
            if (DEBUG) log(getKey() + ": receiver registered");
        }
    }

    private void unregisterReceiver() {
        if (mIsReceiving) {
            mContext.unregisterReceiver(mBroadcastReceiver);
            mIsReceiving = false;
            if (DEBUG) log(getKey() + ": unreceiver registered");
        }
    }

    @Override
    public void setListening(boolean listening) {
        if (listening && mEnabled) {
            registerReceiver();
            getTorchState();
        } else {
            unregisterReceiver();
        }
    }

    private void getTorchState() {
        Intent si = new Intent(mGbContext, TorchService.class);
        si.setAction(TorchService.ACTION_TORCH_GET_STATUS);
        si.putExtra("receiver", mReceiver);
        mGbContext.startService(si);
        if (DEBUG) log(getKey() + ": ACTION_TORCH_GET_STATUS sent");
    }

    private void toggleState() {
        Intent si = new Intent(mGbContext, TorchService.class);
        si.setAction(TorchService.ACTION_TOGGLE_TORCH);
        mGbContext.startService(si);
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        mState.booleanValue = mTorchStatus == TorchService.TORCH_STATUS_ON;
        if (mTorchStatus == TorchService.TORCH_STATUS_ON) {
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_torch_on);
            mState.label = mGbContext.getString(R.string.quick_settings_torch_on);
        } else {
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_torch_off);
            mState.label = mGbContext.getString(R.string.quick_settings_torch_off);
        }

        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        toggleState();
        super.handleClick();
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mReceiver = null;
    }
}
