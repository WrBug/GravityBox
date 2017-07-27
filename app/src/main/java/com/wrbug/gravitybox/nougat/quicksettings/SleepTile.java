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

import com.wrbug.gravitybox.nougat.ModHwKeys;
import com.wrbug.gravitybox.nougat.R;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;

public class SleepTile extends QsTile {

    public SleepTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_sleep);
        mState.label = mGbContext.getString(R.string.qs_tile_sleep);
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        try {
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            XposedHelpers.callMethod(pm, "goToSleep", SystemClock.uptimeMillis());
        } catch(Throwable t) {
            log(getKey() + ": Error calling PowerManager goToSleep(): ");
            XposedBridge.log(t);
        }
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        Intent intent = new Intent(ModHwKeys.ACTION_SHOW_POWER_MENU);
        mContext.sendBroadcast(intent);
        collapsePanels();
        return true;
    }
}
