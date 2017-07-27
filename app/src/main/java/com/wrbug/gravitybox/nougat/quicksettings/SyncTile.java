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
import com.wrbug.gravitybox.nougat.GravityBoxService;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.GravityBoxResultReceiver.Receiver;

import de.robv.android.xposed.XSharedPreferences;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;

public class SyncTile extends QsTile {
    private Handler mHandler;
    private GravityBoxResultReceiver mReceiver;
    private boolean mSyncState;
    private Object mSyncHandle;

    public SyncTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mHandler = new Handler();
        mReceiver = new GravityBoxResultReceiver(mHandler);
        mReceiver.setReceiver(new Receiver() {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                if (resultCode == GravityBoxService.RESULT_SYNC_STATUS) {
                    final boolean oldState = mSyncState;
                    mSyncState = resultData.getBoolean(GravityBoxService.KEY_SYNC_STATUS);
                    if (mSyncState != oldState) { 
                        refreshState();
                        if (DEBUG) log(getKey() + ": onReceiveResult: mSyncState=" + mSyncState);
                    }
                }
            }
        });
    }

    @Override
    public void setListening(boolean listening) {
        if (listening && mEnabled) {
            mSyncHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, mSyncObserver);
            getSyncState();
            if (DEBUG) log(getKey() + ": sync status listener registered");
        } else if (mSyncHandle != null){
            ContentResolver.removeStatusChangeListener(mSyncHandle);
            mSyncHandle = null;
            if (DEBUG) log(getKey() + ": sync status listener unregistered");
        }
    }

    private void getSyncState() {
        Intent si = new Intent(mGbContext, GravityBoxService.class);
        si.setAction(GravityBoxService.ACTION_GET_SYNC_STATUS);
        si.putExtra("receiver", mReceiver);
        mGbContext.startService(si);
        if (DEBUG) log(getKey() + ": ACTION_GET_SYNC_STATUS sent");
    }

    private void toggleState() {
        Intent si = new Intent(mGbContext, GravityBoxService.class);
        si.setAction(GravityBoxService.ACTION_TOGGLE_SYNC);
        mGbContext.startService(si);
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        mState.booleanValue = mSyncState;
        if (mSyncState) {
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_sync_on);
            mState.label = mGbContext.getString(R.string.quick_settings_sync_on);
        } else {
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_sync_off);
            mState.label = mGbContext.getString(R.string.quick_settings_sync_off);
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
        startSettingsActivity(Settings.ACTION_SYNC_SETTINGS);
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mHandler = null;
        mReceiver = null;
        mSyncHandle = null;
    }

    private SyncStatusObserver mSyncObserver = new SyncStatusObserver() {
        public void onStatusChanged(int which) {
            // update state/view if something happened
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    getSyncState();
                }
            });
        }
    };
}
