/*
 * Copyright (C) 2016 Peter Gregus for GravityBox Project (C3C076@xda)
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
import com.wrbug.gravitybox.nougat.Utils;
import com.wrbug.gravitybox.nougat.adapters.IIconListAdapterItem;
import com.wrbug.gravitybox.nougat.ledcontrol.QuietHours;
import com.wrbug.gravitybox.nougat.ledcontrol.QuietHoursActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

public class QuietHoursShortcut extends AMultiShortcut {
    protected static final String ACTION =  ModHwKeys.ACTION_TOGGLE_QUIET_HOURS;

    public QuietHoursShortcut(Context context) {
        super(context);
    }

    @Override
    public String getText() {
        return mContext.getString(R.string.lc_quiet_hours);
    }

    @Override
    public Drawable getIconLeft() {
        return mResources.getDrawable(R.drawable.shortcut_quiet_hours_auto, null);
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
        list.add(new ShortcutItem(mContext, R.string.shortcut_quiet_hours_toggle, 
                R.drawable.shortcut_quiet_hours, null));
        list.add(new ShortcutItem(mContext, R.string.quiet_hours_on, 
                R.drawable.shortcut_quiet_hours_enable, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(QuietHoursActivity.EXTRA_QH_MODE,
                                QuietHours.Mode.ON.toString());
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.quiet_hours_off, 
                R.drawable.shortcut_quiet_hours_disable, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(QuietHoursActivity.EXTRA_QH_MODE,
                                QuietHours.Mode.OFF.toString());
                    }
                }));
        if (Utils.isAppInstalled(mContext, QuietHours.PKG_WEARABLE_APP)) {
            list.add(new ShortcutItem(mContext, R.string.quiet_hours_wear,
                    R.drawable.shortcut_quiet_hours_wear, new ExtraDelegate() {
                        @Override
                        public void addExtraTo(Intent intent) {
                            intent.putExtra(QuietHoursActivity.EXTRA_QH_MODE,
                                    QuietHours.Mode.WEAR.toString());
                        }
                    }));
        }
        list.add(new ShortcutItem(mContext, R.string.quiet_hours_auto, 
                R.drawable.shortcut_quiet_hours_auto, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(QuietHoursActivity.EXTRA_QH_MODE,
                                QuietHours.Mode.AUTO.toString());
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
