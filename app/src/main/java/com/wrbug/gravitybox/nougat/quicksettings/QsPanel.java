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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.wrbug.gravitybox.nougat.BroadcastSubReceiver;
import com.wrbug.gravitybox.nougat.BuildConfig;
import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.ModHwKeys;
import com.wrbug.gravitybox.nougat.ModQsTiles;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.Utils;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class QsPanel implements BroadcastSubReceiver {
    private static final String TAG = "GB:QsPanel";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String CLASS_QS_PANEL = "com.android.systemui.qs.QSPanel";
    private static final String CLASS_BRIGHTNESS_CTRL = "com.android.systemui.settings.BrightnessController";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private XSharedPreferences mPrefs;
    private ViewGroup mQsPanel;
    private int mNumColumns;
    private int mScaleCorrection;
    private View mBrightnessSlider;
    private boolean mHideBrightness;
    private boolean mBrightnessIconEnabled;
    private ImageView mBrightnessIcon;

    public QsPanel(XSharedPreferences prefs, ClassLoader classLoader) {
        mPrefs = prefs;

        initPreferences();
        createHooks(classLoader);

        if (DEBUG) log("QsPanel wrapper created");
    }

    private void initPreferences() {
        mNumColumns = Utils.isOxygenOs35Rom() ? 0 : Integer.valueOf(mPrefs.getString(
                GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_TILES_PER_ROW, "0"));
        mScaleCorrection =Utils.isOxygenOs35Rom() ? 0 : mPrefs.getInt(GravityBoxSettings.PREF_KEY_QS_SCALE_CORRECTION, 0);
        mHideBrightness = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_HIDE_BRIGHTNESS, false);
        mBrightnessIconEnabled = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_QS_BRIGHTNESS_ICON, false);
        if (DEBUG) log("initPreferences: mNumColumns=" + mNumColumns +
                "; mHideBrightness=" + mHideBrightness +
                "; mBrightnessIconEnabled=" + mBrightnessIconEnabled);
    }

    public void setEventDistributor(QsTileEventDistributor eventDistributor) {
        eventDistributor.registerBroadcastSubReceiver(this);   
    }
    
    public void updateResources() {
        try {
            if (mQsPanel != null) {
                XposedHelpers.callMethod(mQsPanel, "updateResources");
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QS_COLS)) {
                mNumColumns = intent.getIntExtra(GravityBoxSettings.EXTRA_QS_COLS, 0);
                updateResources();
                if (DEBUG) log("onBroadcastReceived: mNumColumns=" + mNumColumns);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QS_SCALE_CORRECTION)) {
                mScaleCorrection = intent.getIntExtra(GravityBoxSettings.EXTRA_QS_SCALE_CORRECTION, 0);
                updateResources();
                if (DEBUG) log("onBroadcastReceived: mScaleCorrection=" + mScaleCorrection);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QS_HIDE_BRIGHTNESS)) {
                mHideBrightness = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_QS_HIDE_BRIGHTNESS, false);
                updateResources();
                if (DEBUG) log("onBroadcastReceived: mHideBrightness=" + mHideBrightness);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QS_BRIGHTNESS_ICON)) {
                mBrightnessIconEnabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_QS_BRIGHTNESS_ICON, false);
                if (DEBUG) log("onBroadcastReceived: mBrightnessIconEnabled=" + mBrightnessIconEnabled);
            }
        } 
    }

    public static float getScalingFactor(int numColumns, int correctionPercent) {
        if (Utils.isOxygenOs35Rom())
            return 1;

        float correction = (float)correctionPercent / 100f;
        switch (numColumns) {
            default:
            case 0: return 1f + correction;
            case 3: return 1f + correction;
            case 4: return 0.85f + correction;
            case 5: return 0.75f + correction;
            case 6: return 0.65f + correction;
        }
    }

    private View getBrightnessSlider() {
        if (mBrightnessSlider != null) return mBrightnessSlider;

        ViewGroup bv = (ViewGroup)XposedHelpers.getObjectField(mQsPanel, "mBrightnessView");
        if (Utils.isOxygenOs35Rom()) {
            mBrightnessSlider = bv;
        } else {
            int resId = mQsPanel.getResources().getIdentifier("brightness_slider", "id",
                    mQsPanel.getContext().getPackageName());
            if (resId != 0) {
                mBrightnessSlider = bv.findViewById(resId);
            }
        }
        return mBrightnessSlider;
    }

    private View.OnClickListener mBrightnessIconOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(ModHwKeys.ACTION_TOGGLE_AUTO_BRIGHTNESS);
            v.getContext().sendBroadcast(intent);
        }
    };

    private View.OnLongClickListener mBrightnessIconOnLongClick = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            try {
                Intent intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                Object host = XposedHelpers.getObjectField(mQsPanel, "mHost");
                XposedHelpers.callMethod(host, "startActivityDismissingKeyguard", intent);
                return true;
            } catch (Throwable t) {
                XposedBridge.log(t);
                return false;
            }
        }
    };

    private int getDualTileCount() {
        if (Utils.isOxygenOs35Rom())
            return 0;

        int count = 0;
        List<String> dualTiles = new ArrayList<>();
        List<String> enabledTiles = new ArrayList<>();
        try {
            dualTiles.addAll(Arrays.asList(
                    mPrefs.getString(TileOrderActivity.PREF_KEY_TILE_DUAL,
                            "aosp_tile_wifi,aosp_tile_bluetooth").split(",")));
            enabledTiles.addAll(Arrays.asList(
                    mPrefs.getString(TileOrderActivity.PREF_KEY_TILE_ENABLED,
                    TileOrderActivity.getDefaultTileList(
                            Utils.getGbContext(mQsPanel.getContext()))).split(",")));
        } catch (Throwable t) { /* ignore */ }

        for (String tileKey : enabledTiles) {
            if (dualTiles.contains(tileKey))
                count++;
        }

        return count;
    }

    private void createHooks(final ClassLoader classLoader) {
        try {
            Class<?> classQsPanel = XposedHelpers.findClass(CLASS_QS_PANEL, classLoader);

            XposedHelpers.findAndHookMethod(classQsPanel, "updateResources",
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mQsPanel == null) {
                        mQsPanel = (ViewGroup) param.thisObject;
                    }
                    boolean shouldInvalidate = false;

                    // brighntess slider
                    View bs = getBrightnessSlider();
                    if (bs != null) {
                        final int vis = mHideBrightness ? View.GONE : View.VISIBLE; 
                        if (bs.getVisibility() != vis) {
                            bs.setVisibility(vis);
                            shouldInvalidate = true;
                        }
                    }

                    // tiles per row
                    if (mNumColumns != 0) {
                        shouldInvalidate = true;
                        XposedHelpers.setIntField(mQsPanel, "mColumns", mNumColumns);
                        if (DEBUG) log("updateResources: Updated number of columns per row");
                        final float factor = getScalingFactor(mNumColumns, mScaleCorrection);
                        if (factor != 1f) {
                            int ch = XposedHelpers.getIntField(mQsPanel, "mCellHeight");
                            XposedHelpers.setIntField(mQsPanel, "mCellHeight", Math.round(ch*factor));
                            int cw = XposedHelpers.getIntField(mQsPanel, "mCellWidth");
                            XposedHelpers.setIntField(mQsPanel, "mCellWidth", Math.round(cw*factor));
                            int lch = XposedHelpers.getIntField(mQsPanel, "mLargeCellHeight");
                            XposedHelpers.setIntField(mQsPanel, "mLargeCellHeight", Math.round(lch*factor));
                            int lcw = XposedHelpers.getIntField(mQsPanel, "mLargeCellWidth");
                            XposedHelpers.setIntField(mQsPanel, "mLargeCellWidth", Math.round(lcw*factor));
                            int dualTileUnderlap = XposedHelpers.getIntField(mQsPanel, "mDualTileUnderlap");
                            XposedHelpers.setIntField(mQsPanel, "mDualTileUnderlap",
                                    Math.round(dualTileUnderlap*factor));
                            if (DEBUG) log("updateResources: scaling applied with factor=" + factor);
                        }
                    }

                    final int dualTileCount = getDualTileCount();
                    // reduce size of the first row if there aren't any dual tiles
                    if (dualTileCount == 0 && !Utils.isOxygenOs35Rom()) {
                        XposedHelpers.setIntField(mQsPanel, "mLargeCellHeight",
                                XposedHelpers.getIntField(mQsPanel, "mCellHeight"));
                        XposedHelpers.setIntField(mQsPanel, "mLargeCellWidth",
                                XposedHelpers.getIntField(mQsPanel, "mCellWidth"));
                        shouldInvalidate = true;
                        if (DEBUG) log("updateResources: Updated first row dimensions: all tiles non-dual");
                    // apply additional width reduction to nicely fit 3 dual tiles in one row
                    } else if (dualTileCount > 2 && mNumColumns < 5) {
                        int lcw = XposedHelpers.getIntField(mQsPanel, "mLargeCellWidth");
                        XposedHelpers.setIntField(mQsPanel, "mLargeCellWidth", Math.round(lcw*0.75f));
                        shouldInvalidate = true;
                        if (DEBUG) log("updateResources: Applied additional reduction to dual tile width");
                    }

                    // invalidate if changes made
                    if (shouldInvalidate) {
                        mQsPanel.postInvalidate();
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(CLASS_BRIGHTNESS_CTRL, classLoader,
                    "updateIcon", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (DEBUG) log("BrightnessController: updateIcon");
                    ImageView icon = (ImageView) XposedHelpers.getObjectField(param.thisObject, "mIcon");
                    if (icon != null) {
                        if (mBrightnessIcon == null) {
                            mBrightnessIcon = icon;
                            icon.setOnClickListener(mBrightnessIconOnClick);
                            icon.setOnLongClickListener(mBrightnessIconOnLongClick);
                            icon.setBackground(Utils.getGbContext(
                                    mBrightnessIcon.getContext()).getDrawable(
                                            R.drawable.ripple));
                        }
                        if (mBrightnessIconEnabled && !mHideBrightness) {
                            boolean automatic = (boolean) param.args[0];
                            int resId = icon.getResources().getIdentifier(
                                    (automatic ? "ic_qs_brightness_auto_on" : "ic_qs_brightness_auto_off"),
                                    "drawable", ModQsTiles.PACKAGE_NAME);
                            if (resId != 0) {
                                icon.setImageResource(resId);
                            }
                            icon.setVisibility(View.VISIBLE);
                        } else {
                            icon.setVisibility(View.GONE);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
