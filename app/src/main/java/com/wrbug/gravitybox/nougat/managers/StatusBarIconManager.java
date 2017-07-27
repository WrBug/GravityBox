/*
 * Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.wrbug.gravitybox.nougat.managers;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wrbug.gravitybox.nougat.BroadcastSubReceiver;
import com.wrbug.gravitybox.nougat.BuildConfig;
import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.Utils;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;

public class StatusBarIconManager implements BroadcastSubReceiver {
    private static final String TAG = "GB:StatusBarIconManager";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    public static final int SI_MODE_STOCK = 1;
    public static final int SI_MODE_DISABLED = 2;

    public static final int JELLYBEAN = 0;
    public static final int LOLLIPOP = 1;

    public static final int FLAG_COLORING_ENABLED_CHANGED = 1 << 0;
    public static final int FLAG_SIGNAL_ICON_MODE_CHANGED = 1 << 1;
    public static final int FLAG_ICON_COLOR_CHANGED = 1 << 2;
    public static final int FLAG_ICON_COLOR_SECONDARY_CHANGED = 1 << 3;
    public static final int FLAG_DATA_ACTIVITY_COLOR_CHANGED = 1 << 4;
    public static final int FLAG_ICON_STYLE_CHANGED = 1 << 5;
    public static final int FLAG_ICON_ALPHA_CHANGED = 1 << 6;
    public static final int FLAG_ICON_TINT_CHANGED = 1 << 7;
    private static final int FLAG_ALL = 0xFF;

    private Context mContext;
    private Resources mGbResources;
    private Resources mSystemUiRes;
    private Map<String, Integer[]> mBasicIconIds;
    private Map<String, SoftReference<Drawable>> mIconCache;
    private ColorInfo mColorInfo;
    private List<IconManagerListener> mListeners;

    public interface IconManagerListener {
        void onIconManagerStatusChanged(int flags, ColorInfo colorInfo);
    }

    public static class ColorInfo {
        public boolean coloringEnabled;
        public boolean wasColoringEnabled;
        public int defaultIconColor;
        public int[] iconColor;
        public int defaultDataActivityColor;
        public int[] dataActivityColor;
        public int signalIconMode;
        public int iconStyle;
        public float alphaSignalCluster;
        public float alphaTextAndBattery;
        public int iconTint;
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    protected StatusBarIconManager(Context context, XSharedPreferences prefs) throws Throwable {
        mContext = context;
        mSystemUiRes = mContext.getResources();
        Context gbContext = Utils.getGbContext(mContext);
        mGbResources = gbContext.getResources();

        Map<String, Integer[]> basicIconMap = new HashMap<String, Integer[]>();
        basicIconMap.put("stat_sys_data_bluetooth", new Integer[] 
                { R.drawable.stat_sys_data_bluetooth, null });
        basicIconMap.put("stat_sys_data_bluetooth_connected", new Integer[] {
                R.drawable.stat_sys_data_bluetooth_connected, null });
        basicIconMap.put("stat_sys_alarm", new Integer[] {
                R.drawable.stat_sys_alarm_jb, null });
        basicIconMap.put("stat_sys_ringer_vibrate", new Integer[] { 
                R.drawable.stat_sys_ringer_vibrate_jb, null });
        basicIconMap.put("stat_sys_ringer_silent", new Integer[] {
                R.drawable.stat_sys_ringer_silent_jb, null });
        basicIconMap.put("stat_sys_headset_with_mic", new Integer[] {
                R.drawable.stat_sys_headset_with_mic_jb, null });
        basicIconMap.put("stat_sys_headset_without_mic", new Integer[] {
                R.drawable.stat_sys_headset_without_mic_jb, null });
        basicIconMap.put("stat_sys_dnd", new Integer[] { null, null });
        basicIconMap.put("stat_sys_location", new Integer[] { null, null });
        basicIconMap.put("stat_sys_cast", new Integer[] { null, null });
        basicIconMap.put("stat_sys_hotspot", new Integer[] { null, null });
        mBasicIconIds = Collections.unmodifiableMap(basicIconMap);

        mIconCache = new HashMap<String, SoftReference<Drawable>>();

        initColorInfo();

        mListeners = new ArrayList<IconManagerListener>();

        setIconColor(prefs.getInt(GravityBoxSettings.PREF_KEY_STATUSBAR_ICON_COLOR, 
                getDefaultIconColor()));
        try {
            int iconStyle = Integer.valueOf(prefs.getString(
                    GravityBoxSettings.PREF_KEY_STATUS_ICON_STYLE, "1"));
            setIconStyle(iconStyle);
        } catch(NumberFormatException nfe) {
            log("Invalid value for PREF_KEY_STATUS_ICON_STYLE preference");
        }
        setIconColor(1, prefs.getInt(GravityBoxSettings.PREF_KEY_STATUSBAR_ICON_COLOR_SECONDARY,
                        getDefaultIconColor()));
        setDataActivityColor(
                prefs.getInt(GravityBoxSettings.PREF_KEY_STATUSBAR_DATA_ACTIVITY_COLOR, 
                        mGbResources.getInteger(R.integer.signal_cluster_data_activity_icon_color)));
        setDataActivityColor(1, 
                prefs.getInt(GravityBoxSettings.PREF_KEY_STATUSBAR_DATA_ACTIVITY_COLOR_SECONDARY, 
                        mGbResources.getInteger(R.integer.signal_cluster_data_activity_icon_color)));
        try {
            int signalIconMode = Integer.valueOf(prefs.getString(
                    GravityBoxSettings.PREF_KEY_STATUSBAR_SIGNAL_COLOR_MODE, "1"));
            setSignalIconMode(signalIconMode);
        } catch (NumberFormatException nfe) {
            log("Invalid value for PREF_KEY_STATUSBAR_SIGNAL_COLOR_MODE preference");
        }
        setColoringEnabled(prefs.getBoolean(
                GravityBoxSettings.PREF_KEY_STATUSBAR_ICON_COLOR_ENABLE, false));
    }

    private void initColorInfo() {
        mColorInfo = new ColorInfo();
        mColorInfo.coloringEnabled = false;
        mColorInfo.wasColoringEnabled = false;
        mColorInfo.defaultIconColor = getDefaultIconColor();
        mColorInfo.iconColor = new int[2];
        mColorInfo.defaultDataActivityColor = mGbResources.getInteger(
                R.integer.signal_cluster_data_activity_icon_color);
        mColorInfo.dataActivityColor = new int[2];
        mColorInfo.signalIconMode = SI_MODE_STOCK;
        mColorInfo.iconStyle = LOLLIPOP;
        mColorInfo.alphaSignalCluster = 1;
        mColorInfo.alphaTextAndBattery = 1;
        mColorInfo.iconTint = Color.WHITE;
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_STATUSBAR_COLOR_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_SB_ICON_COLOR)) {
                setIconColor(intent.getIntExtra(
                        GravityBoxSettings.EXTRA_SB_ICON_COLOR, getDefaultIconColor()));
            } else if (intent.hasExtra(GravityBoxSettings.EXTRA_SB_ICON_STYLE)) {
                setIconStyle(intent.getIntExtra(GravityBoxSettings.EXTRA_SB_ICON_STYLE, 0));
            } else if (intent.hasExtra(GravityBoxSettings.EXTRA_SB_ICON_COLOR_SECONDARY)) {
                setIconColor(1, intent.getIntExtra(
                        GravityBoxSettings.EXTRA_SB_ICON_COLOR_SECONDARY, 
                        getDefaultIconColor()));
            } else if (intent.hasExtra(GravityBoxSettings.EXTRA_SB_DATA_ACTIVITY_COLOR)) {
                setDataActivityColor(intent.getIntExtra(
                        GravityBoxSettings.EXTRA_SB_DATA_ACTIVITY_COLOR, 
                        mColorInfo.defaultDataActivityColor));
            } else if (intent.hasExtra(GravityBoxSettings.EXTRA_SB_DATA_ACTIVITY_COLOR_SECONDARY)) {
                setDataActivityColor(1, intent.getIntExtra(
                        GravityBoxSettings.EXTRA_SB_DATA_ACTIVITY_COLOR_SECONDARY, 
                        mColorInfo.defaultDataActivityColor));
            } else if (intent.hasExtra(GravityBoxSettings.EXTRA_SB_ICON_COLOR_ENABLE)) {
                setColoringEnabled(intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_SB_ICON_COLOR_ENABLE, false));
                if (DEBUG) log("Icon colors master switch set to: " + isColoringEnabled());
            } else if (intent.hasExtra(GravityBoxSettings.EXTRA_SB_SIGNAL_COLOR_MODE)) {
                setSignalIconMode(intent.getIntExtra(
                        GravityBoxSettings.EXTRA_SB_SIGNAL_COLOR_MODE,
                        StatusBarIconManager.SI_MODE_STOCK));
            }
        }
    }

    public void registerListener(IconManagerListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
            listener.onIconManagerStatusChanged(FLAG_ALL, mColorInfo);
        }
    }

    public void unregisterListener(IconManagerListener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    private void notifyListeners(int flags) {
        for (IconManagerListener listener : mListeners) {
            listener.onIconManagerStatusChanged(flags, mColorInfo);
        }
    }

    public void refreshState() {
        notifyListeners(FLAG_ALL);
    }

    public void setColoringEnabled(boolean enabled) {
        mColorInfo.wasColoringEnabled = mColorInfo.coloringEnabled;
        if (mColorInfo.coloringEnabled != enabled) {
            mColorInfo.coloringEnabled = enabled;
            clearCache();
            notifyListeners(FLAG_COLORING_ENABLED_CHANGED | FLAG_ICON_COLOR_CHANGED);
        }
    }

    public boolean isColoringEnabled() {
        return mColorInfo.coloringEnabled;
    }

    public int getDefaultIconColor() {
        return Color.WHITE;
    }

    public void setSignalIconMode(int mode) {
        if (mColorInfo.signalIconMode != mode) {
            mColorInfo.signalIconMode = mode;
            clearCache();
            notifyListeners(FLAG_SIGNAL_ICON_MODE_CHANGED);
        }
    }

    public int getSignalIconMode() {
        return mColorInfo.signalIconMode;
    }

    public int getIconColor(int index) {
        return mColorInfo.iconColor[index];
    }

    public int getIconColor() {
        return getIconColor(0);
    }

    public int getDataActivityColor() {
        return getDataActivityColor(0);
    }

    public int getDataActivityColor(int index) {
        return mColorInfo.dataActivityColor[index];
    }

    public void setIconColor(int index, int color) {
        if (mColorInfo.iconColor[index] != color) {
            mColorInfo.iconColor[index] = color;
            clearCache();
            notifyListeners(index == 0 ?
                    FLAG_ICON_COLOR_CHANGED : FLAG_ICON_COLOR_SECONDARY_CHANGED);
        }
    }

    public void setIconColor(int color) {
        setIconColor(0, color);
    }

    public void setDataActivityColor(int index, int color) {
        if (mColorInfo.dataActivityColor[index] != color) {
            mColorInfo.dataActivityColor[index] = color;
            notifyListeners(FLAG_DATA_ACTIVITY_COLOR_CHANGED);
        }
    }

    public void setDataActivityColor(int color) {
        setDataActivityColor(0, color);
    }

    public void setIconStyle(int style) {
        if((style == JELLYBEAN || style == LOLLIPOP) &&
                mColorInfo.iconStyle != style) {
            mColorInfo.iconStyle = style;
            clearCache();
            notifyListeners(FLAG_ICON_STYLE_CHANGED);
        }
    }

    public void setIconAlpha(float alphaSignalCluster, float alphaTextAndBattery) {
        if (mColorInfo.alphaSignalCluster != alphaSignalCluster ||
                mColorInfo.alphaTextAndBattery != alphaTextAndBattery) {
            mColorInfo.alphaSignalCluster = alphaSignalCluster;
            mColorInfo.alphaTextAndBattery = alphaTextAndBattery;
            notifyListeners(FLAG_ICON_ALPHA_CHANGED);
        }
    }

    public void setIconTint(int iconTint) {
        if (mColorInfo.coloringEnabled) return;
        if (mColorInfo.iconTint != iconTint) {
            mColorInfo.iconTint = iconTint;
            notifyListeners(FLAG_ICON_TINT_CHANGED);
        }
    }

    public Drawable applyColorFilter(int index, Drawable drawable, PorterDuff.Mode mode) {
        if (drawable != null) {
            drawable.setColorFilter(mColorInfo.iconColor[index], mode);
        }
        return drawable;
    }

    public Drawable applyColorFilter(int index, Drawable drawable) {
        return applyColorFilter(index, drawable, PorterDuff.Mode.SRC_IN);
    }

    public Drawable applyColorFilter(Drawable drawable) {
        return applyColorFilter(0, drawable, PorterDuff.Mode.SRC_IN);
    }

    public Drawable applyColorFilter(Drawable drawable, PorterDuff.Mode mode) {
        return applyColorFilter(0, drawable, mode);
    }

    public Drawable applyDataActivityColorFilter(int index, Drawable drawable) {
        drawable.setColorFilter(mColorInfo.dataActivityColor[index], PorterDuff.Mode.SRC_IN);
        return drawable;
    }

    public Drawable applyDataActivityColorFilter(Drawable drawable) {
        return applyDataActivityColorFilter(0, drawable);
    }

    public void clearCache() {
        mIconCache.clear();
        if (DEBUG) log("Cache cleared");
    }

    private Drawable getCachedDrawable(String key) {
        if (mIconCache.containsKey(key)) {
            if (DEBUG) log("getCachedDrawable('" + key + "') - cached drawable found");
            return mIconCache.get(key).get();
        }
        return null;
    }

    private void setCachedDrawable(String key, Drawable d) {
        mIconCache.put(key, new SoftReference<Drawable>(d));
        if (DEBUG) log("setCachedDrawable('" + key + "') - storing to cache");
    }

    public Drawable getBasicIcon(int resId) {
        if (resId == 0) return null;

        try {
            String key = mSystemUiRes.getResourceEntryName(resId);
            if (!mBasicIconIds.containsKey(key)) {
                if (DEBUG) log("getBasicIcon: no record for key: " + key);
                return null;
            }

            if (mColorInfo.coloringEnabled) {
                Drawable d = getCachedDrawable(key);
                if (d != null) return d;
                if (mBasicIconIds.get(key)[mColorInfo.iconStyle] != null) {
                    d = mGbResources.getDrawable(mBasicIconIds.get(key)[mColorInfo.iconStyle]).mutate();
                    d = applyColorFilter(d);
                } else {
                    d = mSystemUiRes.getDrawable(resId).mutate();
                    d = applyColorFilter(d, PorterDuff.Mode.SRC_ATOP);
                }
                setCachedDrawable(key, d);
                if (DEBUG) log("getBasicIcon: returning drawable for key: " + key);
                return d;
            } else if (mColorInfo.wasColoringEnabled) {
                return mSystemUiRes.getDrawable(resId);
            } else {
                return null;
            }
        } catch (Throwable t) {
            log("getBasicIcon: " + t.getMessage());
            return null;
        }
    }
}
