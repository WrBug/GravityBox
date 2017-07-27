/*
 * Copyright (C) 2013 AOKP Project
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

package com.wrbug.gravitybox.nougat;

import java.io.File;

import com.wrbug.gravitybox.nougat.ModStatusBar.StatusBarState;
import com.wrbug.gravitybox.nougat.ModStatusBar.StatusBarStateChangedListener;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Display;
import android.view.WindowManager;
import android.view.Surface;
import android.widget.FrameLayout;

class NotificationWallpaper implements BroadcastSubReceiver,
                                       StatusBarStateChangedListener {
    private static final String TAG = "GB:NotificationWallpaper";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private FrameLayout mNotificationPanelView;
    private String mNotifBgImagePathPortrait;
    private String mNotifBgImagePathLandscape;
    private String mBgType;
    private int mColor;
    private float mAlpha;
    private Context mContext;
    private Drawable mDrawable;
    private int mStatusBarState = -1;
    private Drawable mBackupBg;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
        
    }

    public NotificationWallpaper(FrameLayout container, XSharedPreferences prefs) throws Throwable {
        mNotificationPanelView = container;
        mContext = mNotificationPanelView.getContext();
        mBackupBg = mNotificationPanelView.getBackground();

        Context gbContext = Utils.getGbContext(mContext);
        mNotifBgImagePathPortrait = gbContext.getFilesDir() + "/notifwallpaper";
        mNotifBgImagePathLandscape = gbContext.getFilesDir() + "/notifwallpaper_landscape";

        initPreferences(prefs);
        createHooks();
        prepareWallpaper();
    }

    private void initPreferences(XSharedPreferences prefs) {
        mBgType = prefs.getString(
                GravityBoxSettings.PREF_KEY_NOTIF_BACKGROUND,
                GravityBoxSettings.NOTIF_BG_DEFAULT);
        mColor = prefs.getInt(
                GravityBoxSettings.PREF_KEY_NOTIF_COLOR, Color.BLACK);
        setAlpha(prefs.getInt(
                GravityBoxSettings.PREF_KEY_NOTIF_BACKGROUND_ALPHA, 0));
    }

    private void createHooks() {
        XposedHelpers.findAndHookMethod(mNotificationPanelView.getClass(),
                "onConfigurationChanged", Configuration.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (mBgType.equals(GravityBoxSettings.NOTIF_BG_IMAGE)) {
                    if (DEBUG) log("onConfigurationChanged");
                    prepareWallpaper();
                    updateWallpaper();
                }
            }
        });
    }

    private void setAlpha(int alpha) {
        if (alpha < 0) alpha = 0;
        if (alpha > 100) alpha = 100;
        mAlpha = (float)alpha / (float)100;
    }

    private void prepareWallpaper() {
        if (mBgType.equals(GravityBoxSettings.NOTIF_BG_DEFAULT)) {
            mDrawable = null;
            return;
        }

        boolean isLandscape = false;
        File file = new File(mNotifBgImagePathPortrait);
        File fileLandscape = new File(mNotifBgImagePathLandscape);
        Display display = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int orientation = display.getRotation();
        switch(orientation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                isLandscape = false;
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                isLandscape = true;
                break;
        }

        if (mBgType.equals(GravityBoxSettings.NOTIF_BG_IMAGE) && file.exists()) {
            if (isLandscape && fileLandscape.exists()) {
                mDrawable = Drawable.createFromPath(mNotifBgImagePathLandscape);
            } else {
                mDrawable = Drawable.createFromPath(mNotifBgImagePathPortrait);
            }
        } else if (mBgType.equals(GravityBoxSettings.NOTIF_BG_COLOR)) {
            mDrawable = new ColorDrawable(mColor);
        }

        if (mDrawable != null) {
            mDrawable.setAlpha(mAlpha == 0 ? 255 : (int) ((1-mAlpha) * 255));
            if (DEBUG) log("Wallpaper prepared");
        }
    }

    private void updateWallpaper() {
        if (mStatusBarState == StatusBarState.SHADE && mDrawable != null) {
            mNotificationPanelView.setBackground(mDrawable);
            if (DEBUG) log("updateWallpaper: wallpaper set");
        } else {
            mNotificationPanelView.setBackground(mBackupBg);
            if (DEBUG) log("updateWallpaper: wallpaper unset");
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_NOTIF_BACKGROUND_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BG_TYPE)) {
                mBgType = intent.getStringExtra(GravityBoxSettings.EXTRA_BG_TYPE);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BG_COLOR)) {
                mColor = intent.getIntExtra(GravityBoxSettings.EXTRA_BG_COLOR, Color.BLACK);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BG_ALPHA)) {
                setAlpha(intent.getIntExtra(GravityBoxSettings.EXTRA_BG_ALPHA, 0));
            }
            prepareWallpaper();
            updateWallpaper();
        }
    }

    @Override
    public void onStatusBarStateChanged(int oldState, int newState) {
        if (mStatusBarState != newState) {
            mStatusBarState = newState;
            updateWallpaper();
        }
    }
}
