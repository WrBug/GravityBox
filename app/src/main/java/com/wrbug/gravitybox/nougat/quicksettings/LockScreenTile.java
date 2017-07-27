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

import com.wrbug.gravitybox.nougat.R;

import de.robv.android.xposed.XSharedPreferences;
import android.provider.Settings;

public class LockScreenTile extends QsTile {

    public LockScreenTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);
    }

    private void toggleLockscreenState() {
        mKgMonitor.setKeyguardDisabled(!mKgMonitor.isKeyguardDisabled());
        refreshState();
    }

    @Override
    public void initPreferences() {
        super.initPreferences();
        mSecured = true;
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        if (mKgMonitor.isKeyguardDisabled()) {
            mState.booleanValue = false;
            mState.label = mGbContext.getString(R.string.quick_settings_lock_screen_off);
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_lock_screen_off);
        } else {
            mState.booleanValue = true;
            mState.label = mGbContext.getString(R.string.quick_settings_lock_screen_on);
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_lock_screen_on);
        }

        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        toggleLockscreenState();
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        startSettingsActivity(Settings.ACTION_SECURITY_SETTINGS);
        return true;
    }

    @Override
    public void handleDestroy() {
        if (mKgMonitor.isKeyguardDisabled()) {
            mKgMonitor.setKeyguardDisabled(false);
        }
        super.handleDestroy();
    }
}
