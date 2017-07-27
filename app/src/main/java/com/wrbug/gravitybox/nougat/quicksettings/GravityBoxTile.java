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
import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.PhoneWrapper;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.Utils;

import de.robv.android.xposed.XSharedPreferences;
import android.content.Intent;

public class GravityBoxTile extends QsTile {

    public GravityBoxTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_gravitybox);
        mState.label = "GravityBox";
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        super.handleUpdateState(state, arg);
    }

    @Override
    public boolean supportsHideOnChange() {
        // starting activity collapses panel anyway
        return false;
    }

    @Override
    public void handleClick() {
        Intent i = new Intent();
        i.setClassName(GravityBox.PACKAGE_NAME, GravityBoxSettings.class.getName());
        startSettingsActivity(i);
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        Intent i = new Intent();
        i.setClassName(GravityBox.PACKAGE_NAME, TileOrderActivity.class.getName());
        i.putExtra(TileOrderActivity.EXTRA_HAS_MSIM_SUPPORT, PhoneWrapper.hasMsimSupport());
        i.putExtra(TileOrderActivity.EXTRA_IS_OOS_35_ROM, Utils.isOxygenOs35Rom());
        startSettingsActivity(i);
        return true;
    }
}
