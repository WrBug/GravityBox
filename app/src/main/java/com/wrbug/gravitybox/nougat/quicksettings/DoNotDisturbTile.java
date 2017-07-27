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

package com.wrbug.gravitybox.nougat.quicksettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.managers.SysUiManagers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class DoNotDisturbTile extends AospTile {
    public static final String AOSP_KEY = "dnd";

    public static final String CLASS_ZEN_MODE_CONFIG = "android.service.notification.ZenModeConfig";

    public static final int ZEN_MODE_OFF = 0;
    public static final int ZEN_MODE_IMPORTANT_INTERRUPTIONS = 1;
    public static final int ZEN_MODE_NO_INTERRUPTIONS = 2;
    public static final int ZEN_MODE_ALARMS = 3;

    private enum DurationMode { MANUAL, ALARM, CUSTOM };

    private static final class ZenMode {
        int value;
        boolean enabled = true;
        ZenMode(int val) { value = val; } 
    }

    private static final ZenMode[] ZEN_MODES = new ZenMode[] {
            new ZenMode(ZEN_MODE_OFF),
            new ZenMode(ZEN_MODE_IMPORTANT_INTERRUPTIONS),
            new ZenMode(ZEN_MODE_ALARMS),
            new ZenMode(ZEN_MODE_NO_INTERRUPTIONS)
    };

    private Object mZenCtrl;
    private Class<?> mZenModeConfigClass;
    private boolean mQuickMode;
    private DurationMode mDurationMode;
    private int mDuration;
    private boolean mClickOverrideBlocked;

    protected DoNotDisturbTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, "aosp_tile_dnd", tile, prefs, eventDistributor);
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }

    @Override
    public void initPreferences() {
        mQuickMode = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_DND_TILE_QUICK_MODE, false);

        Set<String> smodes = mPrefs.getStringSet(
                GravityBoxSettings.PREF_KEY_DND_TILE_ENABLED_MODES,
                new HashSet<String>(Arrays.asList(new String[] { "1", "2", "3" })));
        List<String> lmodes = new ArrayList<String>(smodes);
        Collections.sort(lmodes);
        int modes[] = new int[lmodes.size()];
        for (int i=0; i<lmodes.size(); i++) {
            modes[i] = Integer.valueOf(lmodes.get(i));
        }
        if (DEBUG) log(getKey() + ": onPreferenceInitialize: modes=" + Arrays.toString(modes));
        setEnabledModes(modes);

        mDurationMode = DurationMode.valueOf(mPrefs.getString(
                GravityBoxSettings.PREF_KEY_DND_TILE_DURATION_MODE, "MANUAL"));

        mDuration = mPrefs.getInt(GravityBoxSettings.PREF_KEY_DND_TILE_DURATION, 60);

        super.initPreferences();
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_DND_TILE_QUICK_MODE)) {
                mQuickMode = intent.getBooleanExtra(GravityBoxSettings.EXTRA_DND_TILE_QUICK_MODE, false);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_DND_TILE_ENABLED_MODES)) {
                int[] modes = intent.getIntArrayExtra(GravityBoxSettings.EXTRA_DND_TILE_ENABLED_MODES);
                if (DEBUG) log(getKey() + ": onBroadcastReceived: modes=" + Arrays.toString(modes));
                setEnabledModes(modes);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_DND_TILE_DURATION_MODE)) {
                mDurationMode = DurationMode.valueOf(intent.getStringExtra(
                        GravityBoxSettings.EXTRA_DND_TILE_DURATION_MODE));
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_DND_TILE_DURATION)) {
                mDuration = intent.getIntExtra(GravityBoxSettings.EXTRA_DND_TILE_DURATION, 60);
            }
        }

        super.onBroadcastReceived(context, intent);
    }

    private void setEnabledModes(int[] modes) {
        // disable all except OFF mode (always implicitly enabled)
        for (ZenMode zm : ZEN_MODES) {
            zm.enabled = (zm.value == ZEN_MODE_OFF);
        }

        // enable only those present in the list
        if (modes != null && modes.length > 0) {
            for (int i=0; i<modes.length; i++) {
                int index = findIndexForMode(modes[i]);
                ZenMode zm = index < ZEN_MODES.length ? ZEN_MODES[index] : null;
                if (zm != null) {
                    zm.enabled = true;
                }
            }
        }
    }

    @Override
    public boolean handleLongClick() {
        if (mQuickMode) {
            if (getZenMode() == ZEN_MODE_OFF) {
                mClickOverrideBlocked = true;
                XposedHelpers.callMethod(mTile, "handleClick");
            } else {
                setZenMode(ZEN_MODE_OFF);
            }
        } else {
            startSettingsActivity(Settings.ACTION_SOUND_SETTINGS);
        }
        return true;
    }

    @Override
    protected boolean onBeforeHandleClick() {
        if (!mQuickMode) return false;
        if (mClickOverrideBlocked) {
            mClickOverrideBlocked = false;
            return false;
        }

        int currentIndex = findIndexForMode(getZenMode());
        final int startIndex = currentIndex;
        do {
            if (++currentIndex >= ZEN_MODES.length) {
                currentIndex = 0;
            }
        } while(!ZEN_MODES[currentIndex].enabled &&
                currentIndex != startIndex);

        if (currentIndex != startIndex) {
            setZenMode(ZEN_MODES[currentIndex].value);
            super.handleClick();
            return true;
        }

        return false;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mZenCtrl = null;
        mZenModeConfigClass = null;
        mDurationMode = null;
    }

    private int findIndexForMode(int zenMode) {
        for (int i = 0; i < ZEN_MODES.length; i++) {
            if (ZEN_MODES[i].value == zenMode)
                return i;
        }
        return 0;
    }

    private Object getZenCtrl() {
        if (mZenCtrl == null) {
            mZenCtrl = XposedHelpers.getObjectField(mTile, "mController");
        }
        return mZenCtrl;
    }

    private int getZenMode() {
        try {
            return (int) XposedHelpers.callMethod(getZenCtrl(), "getZen");
        } catch (Throwable t) {
            XposedBridge.log(t);
            return ZEN_MODE_OFF;
        }
    }

    private void setZenMode(int mode) {
        try {
            XposedHelpers.callMethod(getZenCtrl(), "setZen", mode, getCondition(mode), "GravityBox");
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private Uri getCondition(int zenMode) {
        if (zenMode == ZEN_MODE_OFF) return null;

        switch (mDurationMode) {
            default:
            case MANUAL: return null;
            case ALARM: return getTimeUntilNextAlarmCondition();
            case CUSTOM: return getTimeCondition();
        }
    }

    private Class<?> getZenModeConfigClass() {
        if (mZenModeConfigClass == null) {
            mZenModeConfigClass = XposedHelpers.findClass(CLASS_ZEN_MODE_CONFIG,
                    mContext.getClassLoader());
        }
        return mZenModeConfigClass;
    }

    private Uri getTimeCondition() {
        try {
            Object condition = XposedHelpers.callStaticMethod(getZenModeConfigClass(), "toTimeCondition",
                    mContext, mDuration, SysUiManagers.KeyguardMonitor.getCurrentUserId());
            return (Uri) XposedHelpers.getObjectField(condition, "id");
        } catch (Throwable t) {
            XposedBridge.log(t);
            return null;
        }
    }

    private Uri getTimeUntilNextAlarmCondition() {
        try {
            GregorianCalendar weekRange = new GregorianCalendar();
            final long now = weekRange.getTimeInMillis();
            setToMidnight(weekRange);
            weekRange.roll(Calendar.DATE, 6);
            final long nextAlarmMs = (long) XposedHelpers.callMethod(mZenCtrl, "getNextAlarm");
            if (nextAlarmMs > 0) {
                GregorianCalendar nextAlarm = new GregorianCalendar();
                nextAlarm.setTimeInMillis(nextAlarmMs);
                setToMidnight(nextAlarm);
                if (weekRange.compareTo(nextAlarm) >= 0) {
                    Object condition = XposedHelpers.callStaticMethod(getZenModeConfigClass(), "toNextAlarmCondition",
                            mContext, now, nextAlarmMs, SysUiManagers.KeyguardMonitor.getCurrentUserId());
                    return (Uri) XposedHelpers.getObjectField(condition, "id");
                }
            }
            return null;
        } catch (Throwable t) {
            XposedBridge.log(t);
            return null;
        }
    }

    private void setToMidnight(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
