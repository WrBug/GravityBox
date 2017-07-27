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

import com.wrbug.gravitybox.nougat.PhoneWrapper;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.adapters.IIconListAdapterItem;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

public class NetworkModeShortcut extends AMultiShortcut {
    protected static final String ACTION =  PhoneWrapper.ACTION_CHANGE_NETWORK_TYPE;

    public NetworkModeShortcut(Context context) {
        super(context);
    }

    @Override
    public String getText() {
        return mContext.getString(R.string.shortcut_network_mode);
    }

    @Override
    public Drawable getIconLeft() {
        return mResources.getDrawable(R.drawable.shortcut_network_mode, null);
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
    protected List<IIconListAdapterItem> getShortcutList() {
        final List<IIconListAdapterItem> list = new ArrayList<IIconListAdapterItem>();
        list.add(new ShortcutItem(mContext, R.string.network_mode_1,
                R.drawable.shortcut_network_mode_2g, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                                PhoneWrapper.NT_GSM_ONLY);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.network_mode_3,
                R.drawable.shortcut_network_mode_2g3g, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                                PhoneWrapper.NT_GSM_WCDMA_AUTO);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.network_mode_0,
                R.drawable.shortcut_network_mode_3g2g, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                                PhoneWrapper.NT_WCDMA_PREFERRED);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.network_mode_2,
                R.drawable.shortcut_network_mode_3g, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                                PhoneWrapper.NT_WCDMA_ONLY);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.network_mode_4,
                R.drawable.shortcut_network_mode_cdma_evdo, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                                PhoneWrapper.NT_CDMA_EVDO);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.network_mode_5,
                R.drawable.shortcut_network_mode_cdma, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                                PhoneWrapper.NT_CDMA_ONLY);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.network_mode_6,
                R.drawable.shortcut_network_mode_evdo, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                                PhoneWrapper.NT_EVDO_ONLY);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.network_mode_7,
                R.drawable.shortcut_network_mode_2g3g, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                                PhoneWrapper.NT_GLOBAL);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.network_mode_8,
                R.drawable.shortcut_network_mode_lte_cdma, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                                PhoneWrapper.NT_LTE_CDMA_EVDO);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.network_mode_9,
                R.drawable.shortcut_network_mode_lte_gsm, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                                PhoneWrapper.NT_LTE_GSM_WCDMA);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.network_mode_10,
                R.drawable.shortcut_network_mode_lte_global, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                                PhoneWrapper.NT_LTE_CMDA_EVDO_GSM_WCDMA);
                    }
                }));

        return list;
    }

    public static void launchAction(final Context context, Intent intent) {
        Intent launchIntent = new Intent(ACTION);
        launchIntent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                intent.getIntExtra(PhoneWrapper.EXTRA_NETWORK_TYPE, 0));
        context.sendBroadcast(launchIntent);
    }
}
