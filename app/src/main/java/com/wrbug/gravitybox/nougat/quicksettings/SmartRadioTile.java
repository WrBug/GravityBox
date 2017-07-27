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

import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.ModSmartRadio;
import com.wrbug.gravitybox.nougat.R;

import de.robv.android.xposed.XSharedPreferences;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;

public class SmartRadioTile extends QsTile {

    private boolean mSmartRadioEnabled;
    private ModSmartRadio.State mSmartRadioState;
    private SettingsObserver mSettingsObserver;

    public SmartRadioTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mSettingsObserver = new SettingsObserver(new Handler());
    }

    @Override
    public void setListening(boolean listening) {
        if (listening && mEnabled) {
            getCurrentState();
            mSettingsObserver.observe();
            if (DEBUG) log(getKey() + ": observer registered");
        } else {
            mSettingsObserver.unobserve();
            if (DEBUG) log(getKey() + ": observer unregistered");
        }
    }

    private void getCurrentState() {
        mSmartRadioEnabled = Settings.System.getInt(mContext.getContentResolver(),
                ModSmartRadio.SETTING_SMART_RADIO_ENABLED, 1) == 1;
        String state = Settings.System.getString(mContext.getContentResolver(), 
                ModSmartRadio.SETTING_SMART_RADIO_STATE);
        mSmartRadioState = ModSmartRadio.State.valueOf(state == null ? "UNKNOWN" : state);
        if (DEBUG) log(getKey() + ": getCurrentState: mSmartRadioEnabled=" + mSmartRadioEnabled +
                "; mSmartRadioState=" + mSmartRadioState);
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_SMART_RADIO_ENABLE, false);
        mState.booleanValue = mSmartRadioEnabled;
        if (mSmartRadioEnabled) {
            mState.label = mGbContext.getString(R.string.quick_settings_smart_radio_on);
            mState.icon = mSmartRadioState == ModSmartRadio.State.POWER_SAVING ?
                    mGbContext.getDrawable(R.drawable.ic_qs_smart_radio_on) : 
                        mGbContext.getDrawable(R.drawable.ic_qs_smart_radio_on_normal);
        } else {
            mState.label = mGbContext.getString(R.string.quick_settings_smart_radio_off);
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_smart_radio_off);
        }

        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        Intent i = new Intent(ModSmartRadio.ACTION_TOGGLE_SMART_RADIO);
        mContext.sendBroadcast(i);
        super.handleClick();
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mSettingsObserver = null;
        mSmartRadioState = null;
    }

    class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        public void observe() {
            ContentResolver cr = mContext.getContentResolver();
            cr.registerContentObserver(Settings.System.getUriFor(
                   ModSmartRadio.SETTING_SMART_RADIO_ENABLED), false, this);
            cr.registerContentObserver(Settings.System.getUriFor(
                   ModSmartRadio.SETTING_SMART_RADIO_STATE), false, this);
        }

        public void unobserve() {
            ContentResolver cr = mContext.getContentResolver();
            cr.unregisterContentObserver(this);
        }

        @Override 
        public void onChange(boolean selfChange) {
            getCurrentState();
            refreshState();
            if (DEBUG) log(getKey() + ": refreshState called");
        }
    }
}
