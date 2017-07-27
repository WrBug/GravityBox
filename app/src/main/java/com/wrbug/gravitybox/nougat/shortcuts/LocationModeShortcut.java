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

package com.wrbug.gravitybox.nougat.shortcuts;

import java.util.ArrayList;
import java.util.List;

import com.wrbug.gravitybox.nougat.ConnectivityServiceWrapper;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.adapters.IIconListAdapterItem;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.provider.Settings;

public class LocationModeShortcut extends AMultiShortcut {
    protected static final String ACTION =  ConnectivityServiceWrapper.ACTION_SET_LOCATION_MODE;

    public LocationModeShortcut(Context context) {
        super(context);
    }

    @Override
    public String getText() {
        return mContext.getString(R.string.shortcut_location_mode);
    }

    @Override
    public Drawable getIconLeft() {
        return mResources.getDrawable(R.drawable.shortcut_gps_high, null);
    }

    @Override
    protected String getAction() {
        return ACTION;
    }

    @Override
    protected String getShortcutName() {
        return getText();
    }

    @Override
    protected boolean supportsToast() {
        return true;
    }

    @Override
    public List<IIconListAdapterItem> getShortcutList() {
        final List<IIconListAdapterItem> list = new ArrayList<IIconListAdapterItem>();
        list.add(new ShortcutItem(mContext, R.string.location_mode_high_accuracy, 
                    R.drawable.shortcut_gps_high, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(ConnectivityServiceWrapper.EXTRA_LOCATION_MODE,
                                Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
                    }
                }));
        list.add(new ShortcutItem(mContext,R.string.location_mode_battery_saving,
                R.drawable.shortcut_gps_saving, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(ConnectivityServiceWrapper.EXTRA_LOCATION_MODE,
                                Settings.Secure.LOCATION_MODE_BATTERY_SAVING);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.location_mode_device_only,
                R.drawable.shortcut_gps_sensors, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(ConnectivityServiceWrapper.EXTRA_LOCATION_MODE,
                                Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.location_mode_off,
                R.drawable.shortcut_gps_off, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(ConnectivityServiceWrapper.EXTRA_LOCATION_MODE,
                                Settings.Secure.LOCATION_MODE_OFF);
                    }
                }));
        return list;
    }

    public static void launchAction(final Context context, Intent intent) {
        Intent launchIntent = new Intent(ACTION);
        launchIntent.putExtras(intent.getExtras());
        context.sendBroadcast(launchIntent);
    }
}
