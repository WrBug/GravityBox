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

import com.wrbug.gravitybox.nougat.ModHwKeys;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.adapters.IIconListAdapterItem;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

public class RingerModeShortcut extends AMultiShortcut {
    protected static final String ACTION =  ModHwKeys.ACTION_SET_RINGER_MODE;

    public static final int MODE_RING = 0;
    public static final int MODE_RING_VIBRATE = 1;
    public static final int MODE_SILENT = 2;
    public static final int MODE_VIBRATE = 3;

    public RingerModeShortcut(Context context) {
        super(context);
    }

    @Override
    public String getText() {
        return mContext.getString(R.string.shortcut_ringer_mode);
    }

    @Override
    public Drawable getIconLeft() {
        return mResources.getDrawable(R.drawable.shortcut_ringer_ring_vibrate, null);
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
    protected List<IIconListAdapterItem> getShortcutList() {
        final List<IIconListAdapterItem> list = new ArrayList<IIconListAdapterItem>();
        list.add(new ShortcutItem(mContext, R.string.ringer_mode_sound, 
                R.drawable.shortcut_ringer_ring, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(ModHwKeys.EXTRA_RINGER_MODE, MODE_RING);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.ringer_mode_sound_vibrate,
                R.drawable.shortcut_ringer_ring_vibrate, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(ModHwKeys.EXTRA_RINGER_MODE, MODE_RING_VIBRATE);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.dnd_tile_em_alarms,
                R.drawable.shortcut_ringer_silent, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(ModHwKeys.EXTRA_RINGER_MODE, MODE_SILENT);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.ringer_mode_vibrate,
                R.drawable.shortcut_ringer_vibrate, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(ModHwKeys.EXTRA_RINGER_MODE, MODE_VIBRATE);
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
