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

package com.wrbug.gravitybox.nougat.ledcontrol;

import java.util.Locale;

import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.adapters.IBaseListAdapterItem;
import com.wrbug.gravitybox.nougat.ledcontrol.LedSettings.ActiveScreenMode;
import com.wrbug.gravitybox.nougat.ledcontrol.LedSettings.HeadsUpMode;
import com.wrbug.gravitybox.nougat.ledcontrol.LedSettings.LedMode;
import com.wrbug.gravitybox.nougat.ledcontrol.LedSettings.Visibility;
import com.wrbug.gravitybox.nougat.ledcontrol.LedSettings.VisibilityLs;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.Ringtone;
import android.media.RingtoneManager;

public class LedListItem implements IBaseListAdapterItem {

    private Context mContext;
    private ApplicationInfo mAppInfo;
    private String mAppName;
    private Drawable mAppIcon;
    private LedSettings mLedSettings;
    private PackageManager mPkgManager;

    protected LedListItem(Context context, ApplicationInfo appInfo) {
        mContext = context;
        mAppInfo = appInfo;
        mPkgManager = mContext.getPackageManager();
        mAppName = (String) mAppInfo.loadLabel(mPkgManager);
        mLedSettings = LedSettings.deserialize(mContext, appInfo.packageName);
    }

    protected ApplicationInfo getAppInfo() {
        return mAppInfo;
    }

    protected String getAppName() {
        return mAppName;
     }

    protected String getAppDesc() {
        if (!isEnabled()) {
            LedSettings defLs = LedSettings.getDefault(mContext);
            return defLs.getEnabled() ? mContext.getString(R.string.lc_defaults_apply) :
                    mContext.getString(R.string.lc_disabled);
        } else {
            String buf = "LED: ";
            if (mLedSettings.getLedMode() == LedMode.OFF) {
                buf += mContext.getString(R.string.lc_led_mode_off);
            } else if (mLedSettings.getLedMode() == LedMode.ORIGINAL) {
                buf += mContext.getString(R.string.lc_led_mode_original);
            } else {
                buf += String.format(Locale.getDefault(),
                    "%s=%dms", mContext.getString(R.string.lc_item_summary_on),
                        mLedSettings.getLedOnMs());
                buf += String.format(Locale.getDefault(),
                    "; %s=%dms", mContext.getString(R.string.lc_item_summary_off),
                    mLedSettings.getLedOffMs());
            }
            if (mLedSettings.getSoundOverride()) {
                if (mLedSettings.getSoundUri() == null) {
                    buf += "; " + mContext.getString(R.string.lc_notif_sound_none);
                } else {
                    Ringtone r = RingtoneManager.getRingtone(mContext, mLedSettings.getSoundUri());
                    if (r != null) {
                        buf += "; " + r.getTitle(mContext);
                    }
                }
            }
            if (mLedSettings.getSoundOnlyOnce()) {
                buf += "; " + mContext.getString(R.string.pref_lc_notif_sound_only_once_title);
            }
            if (mLedSettings.getInsistent()) {
                buf += "; " + mContext.getString(R.string.pref_lc_notif_insistent_title);
            }
            if (mLedSettings.getVibrateOverride()) {
                buf += "; " + mContext.getString(R.string.pref_lc_vibrate_override_title);
            }
            if (LedSettings.isActiveScreenMasterEnabled(mContext)) {
                buf += "; AS: " + getActiveScreenModeTitle(mLedSettings.getActiveScreenMode());
            }
            if (LedSettings.isHeadsUpEnabled(mContext)) {
                buf += "; HUP: " + getHeadsUpModeTitle(mLedSettings.getHeadsUpMode());
            }
            if (LedSettings.isQuietHoursEnabled(mContext) &&
                    mLedSettings.getQhIgnore()) {
                buf += "; " + mContext.getString(R.string.pref_lc_qh_ignore_title);
            }
            if (mLedSettings.getVisibility() != Visibility.DEFAULT) {
                buf += "; " + mContext.getString(R.string.pref_lc_notif_visibility_title) +
                        ": " + getVisibilityTitle(mLedSettings.getVisibility());
            }
            if (mLedSettings.getVisibilityLs() != VisibilityLs.DEFAULT) {
                buf += "; " + mContext.getString(R.string.pref_lc_notif_visibility_ls_title) +
                        ": " + getVisibilityLsTitle(mLedSettings.getVisibilityLs());
            }
            if (mLedSettings.getHidePersistent()) {
                buf += "; " + mContext.getString(R.string.pref_lc_notif_hide_persistent_title);
            }
            if (mLedSettings.getOngoing()) {
                buf += "; " + mContext.getString(R.string.lc_item_summary_ongoing);
            }
            return buf;
        }
    }

    private String getActiveScreenModeTitle(ActiveScreenMode asMode) {
        switch (asMode) {
            case DISABLED:
                return mContext.getString(R.string.lc_active_screen_mode_disabled);
            case DO_NOTHING: 
                return mContext.getString(R.string.lc_active_screen_mode_nothing);
            default:
                return "N/A";
        }
    }

    private String getHeadsUpModeTitle(HeadsUpMode hupMode) {
        switch (hupMode) {
            case DEFAULT:
                return mContext.getString(R.string.lc_heads_up_default);
            case ALWAYS:
                return mContext.getString(R.string.lc_heads_up_always);
            case IMMERSIVE:
                return mContext.getString(R.string.lc_heads_up_immersive);
            case OFF:
                return mContext.getString(R.string.lc_heads_up_off);
            default:
                return "N/A";
        }
    }

    private String getVisibilityTitle(Visibility vis) {
        switch (vis) {
            case DEFAULT:
                return mContext.getString(R.string.lc_notif_visibility_default);
            case PUBLIC: 
                return mContext.getString(R.string.lc_notif_visibility_public);
            case PRIVATE:
                return mContext.getString(R.string.lc_notif_visibility_private);
            case SECRET:
                return mContext.getString(R.string.lc_notif_visibility_secret);
            default:
                return "N/A";
        }
    }

    private String getVisibilityLsTitle(VisibilityLs vis) {
        switch (vis) {
            case DEFAULT:
                return mContext.getString(R.string.lc_notif_visibility_ls_default);
            case CLEARABLE: 
                return mContext.getString(R.string.lc_notif_visibility_ls_clearable);
            case PERSISTENT:
                return mContext.getString(R.string.lc_notif_visibility_ls_persistent);
            case ALL:
                return mContext.getString(R.string.lc_notif_visibility_ls_all);
            default:
                return "N/A";
        }
    }

    protected Drawable getAppIcon() {
        if (mAppIcon == null) {
            try {
                mAppIcon = mAppInfo.loadIcon(mPkgManager);
            } catch (Throwable t) {
                t.printStackTrace(); 
                System.gc(); 
            }
        }

        return mAppIcon;
    }

    protected LedSettings getLedSettings() {
        return mLedSettings;
    }

    protected void refreshLedSettings() {
        mLedSettings = LedSettings.deserialize(mContext, mAppInfo.packageName);
    }

    protected boolean isEnabled() {
        return mLedSettings.getEnabled();
    }

    protected void setEnabled(boolean enabled) {
        mLedSettings.setEnabled(enabled);
        mLedSettings.serialize();
    }

    @Override
    public String getText() {
        return getAppName();
    }

    @Override
    public String getSubText() {
        return getAppDesc();
    }
}
