/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
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

import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.adapters.IIconListAdapterItem;
import com.wrbug.gravitybox.nougat.managers.SubscriptionManager;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

public class SimSettingsShortcut extends AMultiShortcut {
    protected static final String ACTION =  SubscriptionManager.ACTION_CHANGE_DEFAULT_SIM_SLOT;

    public SimSettingsShortcut(Context context) {
        super(context);
    }

    @Override
    public String getText() {
        return mContext.getString(R.string.shortcut_sm_title);
    }

    @Override
    public Drawable getIconLeft() {
        return mResources.getDrawable(R.drawable.shortcut_sm_settings, null);
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
        final List<IIconListAdapterItem> list = new ArrayList<>();
        list.add(new ShortcutItem(mContext, R.string.shortcut_sm_voice_sim_1,
                R.drawable.shortcut_sm_voice_sim_1, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(SubscriptionManager.EXTRA_SUB_TYPE, "VOICE");
                        intent.putExtra(SubscriptionManager.EXTRA_SIM_SLOT, 0);
                    }
            }));
        list.add(new ShortcutItem(mContext, R.string.shortcut_sm_voice_sim_2,
                R.drawable.shortcut_sm_voice_sim_2, new ExtraDelegate() {
                @Override
                public void addExtraTo(Intent intent) {
                    intent.putExtra(SubscriptionManager.EXTRA_SUB_TYPE, "VOICE");
                    intent.putExtra(SubscriptionManager.EXTRA_SIM_SLOT, 1);
                }
        }));
        list.add(new ShortcutItem(mContext, R.string.shortcut_sm_voice_sim_ask,
                R.drawable.shortcut_sm_voice_sim_ask, new ExtraDelegate() {
                @Override
                public void addExtraTo(Intent intent) {
                    intent.putExtra(SubscriptionManager.EXTRA_SUB_TYPE, "VOICE");
                    intent.putExtra(SubscriptionManager.EXTRA_SIM_SLOT, 2);
                }
        }));
        list.add(new ShortcutItem(mContext, R.string.shortcut_sm_voice_sim_ui,
                R.drawable.shortcut_sm_voice_sim_ui, new ExtraDelegate() {
                @Override
                public void addExtraTo(Intent intent) {
                    intent.putExtra(SubscriptionManager.EXTRA_SUB_TYPE, "VOICE");
                }
        }));
        list.add(new ShortcutItem(mContext, R.string.shortcut_sm_sms_sim_1,
                R.drawable.shortcut_sm_sms_sim_1, new ExtraDelegate() {
                @Override
                public void addExtraTo(Intent intent) {
                    intent.putExtra(SubscriptionManager.EXTRA_SUB_TYPE, "SMS");
                    intent.putExtra(SubscriptionManager.EXTRA_SIM_SLOT, 0);
                }
        }));
        list.add(new ShortcutItem(mContext, R.string.shortcut_sm_sms_sim_2,
                R.drawable.shortcut_sm_sms_sim_2, new ExtraDelegate() {
                @Override
                public void addExtraTo(Intent intent) {
                    intent.putExtra(SubscriptionManager.EXTRA_SUB_TYPE, "SMS");
                    intent.putExtra(SubscriptionManager.EXTRA_SIM_SLOT, 1);
                }
        }));
        list.add(new ShortcutItem(mContext, R.string.shortcut_sm_sms_sim_ui,
                R.drawable.shortcut_sm_sms_sim_ui, new ExtraDelegate() {
                @Override
                public void addExtraTo(Intent intent) {
                    intent.putExtra(SubscriptionManager.EXTRA_SUB_TYPE, "SMS");
                }
        }));
        list.add(new ShortcutItem(mContext, R.string.shortcut_sm_data_sim_1,
                R.drawable.shortcut_sm_data_sim_1, new ExtraDelegate() {
                @Override
                public void addExtraTo(Intent intent) {
                    intent.putExtra(SubscriptionManager.EXTRA_SUB_TYPE, "DATA");
                    intent.putExtra(SubscriptionManager.EXTRA_SIM_SLOT, 0);
                }
        }));
        list.add(new ShortcutItem(mContext, R.string.shortcut_sm_data_sim_2,
                R.drawable.shortcut_sm_data_sim_2, new ExtraDelegate() {
                @Override
                public void addExtraTo(Intent intent) {
                    intent.putExtra(SubscriptionManager.EXTRA_SUB_TYPE, "DATA");
                    intent.putExtra(SubscriptionManager.EXTRA_SIM_SLOT, 1);
                }
        }));
        list.add(new ShortcutItem(mContext, R.string.shortcut_sm_data_sim_ui,
                R.drawable.shortcut_sm_data_sim_ui, new ExtraDelegate() {
                @Override
                public void addExtraTo(Intent intent) {
                    intent.putExtra(SubscriptionManager.EXTRA_SUB_TYPE, "DATA");
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
