/*
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wrbug.gravitybox.nougat;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

import com.wrbug.gravitybox.nougat.ModStatusBar.ContainerType;
import com.wrbug.gravitybox.nougat.ModStatusBar.StatusBarState;
import com.wrbug.gravitybox.nougat.ModStatusBar.StatusBarStateChangedListener;
import com.wrbug.gravitybox.nougat.ProgressBarController.Mode;
import com.wrbug.gravitybox.nougat.ProgressBarController.ProgressInfo;
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager;
import com.wrbug.gravitybox.nougat.managers.SysUiManagers;
import com.wrbug.gravitybox.nougat.managers.BatteryInfoManager.BatteryData;
import com.wrbug.gravitybox.nougat.managers.BatteryInfoManager.BatteryStatusListener;
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager.ColorInfo;
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager.IconManagerListener;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

public class BatteryBarView extends View implements IconManagerListener, 
                                                    BroadcastSubReceiver,
                                                    BatteryStatusListener,
                                                    ProgressBarController.ProgressStateListener,
                                                    StatusBarStateChangedListener {
    private static final String TAG = "GB:BatteryBarView";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final int ANIM_DURATION = 1500;

    private enum Position { TOP, BOTTOM };

    private boolean mEnabled;
    private Position mPosition;
    private int mMarginPx;
    private int mHeightPx;
    private boolean mAnimateCharge;
    private boolean mDynaColor;
    private int mColor;
    private int mColorLow;
    private int mColorCritical;
    private int mColorCharging;
    private int mTintColor = Color.WHITE;
    private int mLevel;
    private boolean mCharging;
    private ObjectAnimator mChargingAnimator;
    private boolean mHiddenByProgressBar;
    private boolean mCentered;
    private int mStatusBarState;
    private ContainerType mContainerType;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public BatteryBarView(ContainerType containerType, ViewGroup container, XSharedPreferences prefs) {
        super(container.getContext());

        mContainerType = containerType;

        mEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_BAR_SHOW, false);
        mPosition = Position.valueOf(prefs.getString(
                GravityBoxSettings.PREF_KEY_BATTERY_BAR_POSITION, "TOP"));
        mMarginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                prefs.getInt(GravityBoxSettings.PREF_KEY_BATTERY_BAR_MARGIN, 0),
                getResources().getDisplayMetrics());
        mHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                prefs.getInt(GravityBoxSettings.PREF_KEY_BATTERY_BAR_THICKNESS, 2),
                getResources().getDisplayMetrics());
        mAnimateCharge = prefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_BAR_CHARGE_ANIM, false);
        mDynaColor = prefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_BAR_DYNACOLOR, true);
        mColor = prefs.getInt(GravityBoxSettings.PREF_KEY_BATTERY_BAR_COLOR, Color.WHITE);
        mColorLow = prefs.getInt(GravityBoxSettings.PREF_KEY_BATTERY_BAR_COLOR_LOW, 0xffffa500);
        mColorCritical = prefs.getInt(GravityBoxSettings.PREF_KEY_BATTERY_BAR_COLOR_CRITICAL, Color.RED);
        mColorCharging = prefs.getInt(GravityBoxSettings.PREF_KEY_BATTERY_BAR_COLOR_CHARGING, Color.GREEN);
        mCentered = prefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_BAR_CENTERED, false);

        container.addView(this);

        setScaleX(0f);
        setVisibility(View.GONE);
        updatePosition();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mEnabled) {
            setListeners();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unsetListeners();
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) log("w=" + w + "; h=" + h);
        setPivotX(mCentered ? w/2f : 0f);
    }

    @Override
    public void onBatteryStatusChanged(BatteryData batteryData) {
        if (mLevel != batteryData.level || mCharging != batteryData.charging) {
            mLevel = batteryData.level;
            mCharging = batteryData.charging;
            if (DEBUG) log("onBatteryStatusChanged: level=" + mLevel +
                    "; charging=" + mCharging);
            update();
        }
    }

    private void setListeners() {
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.registerListener(this);
        }
        if (SysUiManagers.BatteryInfoManager != null) {
            SysUiManagers.BatteryInfoManager.registerListener(this);
        }
    }

    private void unsetListeners() {
        if (SysUiManagers.BatteryInfoManager != null) {
            SysUiManagers.BatteryInfoManager.unregisterListener(this);
        }
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.unregisterListener(this);
        }
    }

    private void update() {
        if (mEnabled && !mHiddenByProgressBar && isValidStatusBarState()) {
            setVisibility(View.VISIBLE);
            if (mDynaColor) {
                int cappedLevel = Math.min(Math.max(mLevel, 15), 90);
                float hue = (cappedLevel - 15) * 1.6f;
                setBackgroundColor(Color.HSVToColor(0xff, new float[]{ hue, 1.f, 1.f }));
            } else {
                int color = (mColor == Color.WHITE ? mTintColor : mColor);
                if (mCharging) {
                    color = mColorCharging;
                } else if (mLevel <= 5) {
                    color = mColorCritical;
                } else if (mLevel <= 15) {
                    color = mColorLow;
                }
                setBackgroundColor(color);
            }
            if (mAnimateCharge && mCharging && mLevel < 100) {
                startAnimation();
            } else {
                stopAnimation();
                final float newScale = mLevel/100f;
                if (Math.abs(getScaleX() - newScale) > 0.02f) {
                    animateScaleTo(newScale);
                } else {
                    setScaleX(newScale);
                }
            }
        } else {
            stopAnimation();
            setVisibility(View.GONE);
        }
    }

    private void animateScaleTo(float newScale) {
        ObjectAnimator a = ObjectAnimator.ofFloat(this, "scaleX", getScaleX(), newScale);
        a.setInterpolator(new DecelerateInterpolator());
        a.setDuration(ANIM_DURATION);
        a.setRepeatCount(0);
        a.start();
        if (DEBUG) log("Animating to current level");
    }

    private void startAnimation() {
        stopAnimation();
        mChargingAnimator = ObjectAnimator.ofFloat(this, "scaleX", mLevel/100f, 1f);
        mChargingAnimator.setInterpolator(new AccelerateInterpolator());
        mChargingAnimator.setDuration(ANIM_DURATION);
        mChargingAnimator.setRepeatCount(Animation.INFINITE);
        mChargingAnimator.start();
    }

    private void stopAnimation() {
        if (mChargingAnimator != null) {
            mChargingAnimator.cancel();
            mChargingAnimator = null;
        }
    }

    private void updatePosition() {
        MarginLayoutParams lp = null;
        if (mContainerType == ContainerType.STATUSBAR) {
            lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    mHeightPx);
            ((FrameLayout.LayoutParams)lp).gravity = mPosition == Position.TOP ? 
                Gravity.TOP : Gravity.BOTTOM;
        } else if (mContainerType == ContainerType.KEYGUARD) {
            lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                    mHeightPx);
            if (mPosition == Position.TOP) {
                ((RelativeLayout.LayoutParams)lp).addRule(RelativeLayout.ALIGN_PARENT_TOP,
                        RelativeLayout.TRUE);
            } else {
                ((RelativeLayout.LayoutParams)lp).addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,
                        RelativeLayout.TRUE);
            }
        }

        if (lp != null) {
            lp.setMargins(0, mPosition == Position.TOP ? mMarginPx : 0,
                        0, mPosition == Position.BOTTOM ? mMarginPx : 0);
            setLayoutParams(lp);
        }
    }

    private boolean isValidStatusBarState() {
        return ((mContainerType == ContainerType.STATUSBAR &&
                    mStatusBarState == StatusBarState.SHADE) ||
                (mContainerType == ContainerType.KEYGUARD &&
                    mStatusBarState == StatusBarState.KEYGUARD));
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if ((flags & StatusBarIconManager.FLAG_ICON_ALPHA_CHANGED) != 0) {
            setAlpha(colorInfo.alphaTextAndBattery);
        }
        if ((flags & StatusBarIconManager.FLAG_ICON_TINT_CHANGED) != 0) {
            mTintColor = colorInfo.iconTint;
            update();
        }
    }

    @Override
    public void onProgressTrackingStarted(Mode mode) {
        onProgressModeChanged(mode);
    }

    @Override
    public void onProgressTrackingStopped() {
        if (mHiddenByProgressBar) {
            mHiddenByProgressBar = false;
            update();
        }
    }

    @Override
    public void onProgressModeChanged(Mode mode) {
        mHiddenByProgressBar = 
                ((mode == Mode.TOP && mPosition == Position.TOP) ||
                 (mode == Mode.BOTTOM && mPosition == Position.BOTTOM));
        update();
    }

    @Override
    public void onStatusBarStateChanged(int oldState, int newState) {
        if (mStatusBarState != newState) {
            mStatusBarState = newState;
            update();
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_BATTERY_BAR_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_SHOW)) {
                mEnabled = intent.getBooleanExtra(GravityBoxSettings.EXTRA_BBAR_SHOW, false);
                if (mEnabled) {
                    setListeners();
                } else {
                    unsetListeners();
                    setScaleX(0f);
                }
                update();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_POSITION)) {
                mPosition = Position.valueOf(intent.getStringExtra(
                        GravityBoxSettings.EXTRA_BBAR_POSITION));
                updatePosition();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_MARGIN)) {
                mMarginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        intent.getIntExtra(GravityBoxSettings.EXTRA_BBAR_MARGIN, 0),
                        getResources().getDisplayMetrics());
                updatePosition();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_THICKNESS)) {
                mHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        intent.getIntExtra(GravityBoxSettings.EXTRA_BBAR_THICKNESS, 2),
                        getResources().getDisplayMetrics());
                updatePosition();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_DYNACOLOR)) {
                mDynaColor = intent.getBooleanExtra(GravityBoxSettings.EXTRA_BBAR_DYNACOLOR, true);
                update();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_COLOR)) {
                mColor = intent.getIntExtra(GravityBoxSettings.EXTRA_BBAR_COLOR, Color.WHITE);
                update();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_COLOR_LOW)) {
                mColorLow = intent.getIntExtra(GravityBoxSettings.EXTRA_BBAR_COLOR_LOW, 0xffffa500);
                update();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_COLOR_CRITICAL)) {
                mColorCritical = intent.getIntExtra(GravityBoxSettings.EXTRA_BBAR_COLOR_CRITICAL, Color.RED);
                update();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_CHARGE_ANIM)) {
                mAnimateCharge = intent.getBooleanExtra(GravityBoxSettings.EXTRA_BBAR_CHARGE_ANIM, false);
                update();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_CENTERED)) {
                mCentered = intent.getBooleanExtra(GravityBoxSettings.EXTRA_BBAR_CENTERED, false);
                setPivotX(mCentered ? getWidth()/2f : 0f);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_COLOR_CHARGING)) {
                mColorCharging = intent.getIntExtra(GravityBoxSettings.EXTRA_BBAR_COLOR_CHARGING, Color.GREEN);
                update();
            }
        }
    }

    @Override
    public void onProgressUpdated(ProgressInfo pInfo) { }

    @Override
    public void onProgressAdded(ProgressInfo pi) { }

    @Override
    public void onProgressRemoved(String id) { }

    @Override
    public void onProgressPreferencesChanged(Intent intent) { }
}
