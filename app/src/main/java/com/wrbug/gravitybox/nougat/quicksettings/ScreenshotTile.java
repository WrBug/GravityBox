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
import com.wrbug.gravitybox.nougat.ScreenRecordingService;

import de.robv.android.xposed.XSharedPreferences;
import android.content.Intent;

public class ScreenshotTile extends QsTile {

    public ScreenshotTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_screenshot);
        mState.label = mGbContext.getString(R.string.qs_tile_screenshot);
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        super.handleUpdateState(state, arg);
    }

    @Override
    public boolean supportsHideOnChange() {
        // we collapse panels ourselves
        return false;
    }

    @Override
    public void handleClick() {
        collapsePanels();
        Intent intent = new Intent(ModHwKeys.ACTION_SCREENSHOT);
        intent.putExtra(ModHwKeys.EXTRA_SCREENSHOT_DELAY_MS, 1000L);
        mContext.sendBroadcast(intent);
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        collapsePanels();
        try {
            Intent intent = new Intent(mGbContext, ScreenRecordingService.class);
            intent.setAction(ScreenRecordingService.ACTION_TOGGLE_SCREEN_RECORDING);
            mGbContext.startService(intent);
        } catch (Throwable t) {
            log(getKey() + ": Error toggling screen recording: " + t.getMessage());
        } 
        return true;
    }
}
