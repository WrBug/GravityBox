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

import com.wrbug.gravitybox.nougat.GravityBox;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.Utils;
import com.wrbug.gravitybox.nougat.ledcontrol.QuietHours;
import com.wrbug.gravitybox.nougat.ledcontrol.QuietHoursActivity;
import com.wrbug.gravitybox.nougat.managers.SysUiManagers;
import com.wrbug.gravitybox.nougat.managers.StatusbarQuietHoursManager.QuietHoursListener;

import de.robv.android.xposed.XSharedPreferences;
import android.content.Intent;

public class QuietHoursTile extends QsTile implements QuietHoursListener {

    private QuietHours mQh;

    public QuietHoursTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mQh = SysUiManagers.QuietHoursManager.getQuietHours();
            if (mEnabled && shouldShow()) {
                SysUiManagers.QuietHoursManager.registerListener(this);
                if (DEBUG) log(getKey() + ": QuietHours listener registered");
            }
        } else {
            SysUiManagers.QuietHoursManager.unregisterListener(this);
            if (DEBUG) log(getKey() + ": QuietHours listener unregistered");
        }
    }

    @Override
    public void onQuietHoursChanged() {
        mQh = SysUiManagers.QuietHoursManager.getQuietHours();
        refreshState();
    }

    @Override
    public void onTimeTick() {
        refreshState();
    }

    private boolean shouldShow() {
        return (mQh != null &&
                !mQh.uncLocked && mQh.enabled);
    }

    private void toggleState() {
        if (mQh == null) return;

        switch (mQh.mode) {
            case ON:
                if (Utils.isAppInstalled(mContext, QuietHours.PKG_WEARABLE_APP)) {
                    SysUiManagers.QuietHoursManager.setMode(QuietHours.Mode.WEAR);
                } else {
                    SysUiManagers.QuietHoursManager.setMode(QuietHours.Mode.OFF);
                }
                break;
            case AUTO:
                SysUiManagers.QuietHoursManager.setMode(mQh.quietHoursActive() ? 
                        QuietHours.Mode.OFF : QuietHours.Mode.ON);
                break;
            case OFF:
                SysUiManagers.QuietHoursManager.setMode(QuietHours.Mode.ON);
                break;
            case WEAR:
                SysUiManagers.QuietHoursManager.setMode(QuietHours.Mode.OFF);
                break;
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        if (mQh != null) {
            switch (mQh.mode) {
                case ON: 
                    mState.booleanValue = true;
                    mState.label = mGbContext.getString(R.string.quick_settings_quiet_hours_on);
                    mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_quiet_hours_on);
                    break;
                case OFF:
                    mState.booleanValue = false;
                    mState.label = mGbContext.getString(R.string.quick_settings_quiet_hours_off);
                    mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_quiet_hours_off);
                    break;
                case WEAR:
                    mState.booleanValue = true;
                    mState.label = mGbContext.getString(R.string.quick_settings_quiet_hours_wear);
                    mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_quiet_hours_wear);
                    break;
                case AUTO:
                    mState.booleanValue = mQh.quietHoursActive();
                    mState.label = mGbContext.getString(R.string.quick_settings_quiet_hours_auto);
                    mState.icon = mState.booleanValue ?
                            mGbContext.getDrawable(R.drawable.ic_qs_quiet_hours_auto_on) : 
                                mGbContext.getDrawable(R.drawable.ic_qs_quiet_hours_auto_off);
                    break;
            }
            mState.visible = shouldShow();
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
        if (mQh != null) {
            if (mQh.mode != QuietHours.Mode.AUTO) {
                SysUiManagers.QuietHoursManager.setMode(QuietHours.Mode.AUTO);
            } else {
                Intent i = new Intent();
                i.setClassName(GravityBox.PACKAGE_NAME, QuietHoursActivity.class.getName());
                startSettingsActivity(i);
            }
        }
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mQh = null;
    }
}
