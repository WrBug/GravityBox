/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
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
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager.ColorInfo;
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager.IconManagerListener;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

public class ProgressBarView extends View implements 
                                            IconManagerListener, 
                                            StatusBarStateChangedListener,
                                            ProgressBarController.ProgressStateListener {
    private static final String TAG = "GB:ProgressBarView";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final int ANIM_DURATION = 400; // ms
    private static final int INDEX_CYCLER_FREQUENCY = 5000; // ms

    private Mode mMode;
    private boolean mAnimated;
    private ObjectAnimator mAnimator;
    private boolean mCentered;
    private int mHeightPx;
    private int mEdgeMarginPx;
    private int mStatusBarState;
    private ContainerType mContainerType;
    private ProgressBarController mCtrl;
    private int mCurrentIndex = 0;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public ProgressBarView(ContainerType containerType, ViewGroup container,
            XSharedPreferences prefs, ProgressBarController ctrl) {
        super(container.getContext());

        mContainerType = containerType;
        mCtrl = ctrl;

        mAnimated = prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_ANIMATED, true);
        mCentered = prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_CENTERED, false);
        mHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                prefs.getInt(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_THICKNESS, 1),
                getResources().getDisplayMetrics());
        mEdgeMarginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                prefs.getInt(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_MARGIN, 0),
                getResources().getDisplayMetrics());

        setScaleX(0f);
        setBackgroundColor(Color.WHITE);
        setVisibility(View.GONE);
        container.addView(this);

        mAnimator = new ObjectAnimator();
        mAnimator.setTarget(this);
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.setDuration(ANIM_DURATION);
        mAnimator.setRepeatCount(0);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.registerListener(this);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.unregisterListener(this);
        }
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) log("w=" + w + "; h=" + h);
        setPivotX(mCentered ? getWidth()/2f : 
            Utils.isRTL(getContext()) ? getWidth() : 0f);
    }

    @Override
    public void onProgressTrackingStarted(Mode mode) {
        mMode = mode;
        updatePosition();
        if (DEBUG) log("onProgressTrackingStarted: " + mContainerType +
                "; mMode=" + mMode);
    }

    @Override
    public void onProgressAdded(ProgressInfo pi) {
        resetIndexCycler(mCtrl.getList().size()-1);
        updateProgressView(true);
    }

    @Override
    public void onProgressUpdated(ProgressInfo pInfo) {
        if (pInfo.id.equals(getCurrentId())) {
            updateProgressView(false);
        }
    }

    @Override
    public void onProgressRemoved(String id) {
        resetIndexCycler(0);
        updateProgressView(true);
    }

    @Override
    public void onProgressTrackingStopped() {
        resetIndexCycler(0);
        updateProgressView(false);
    }

    @Override
    public void onProgressModeChanged(Mode mode) {
        mMode = mode;
        updatePosition();
        if (DEBUG) log("onModeChanged: " + mContainerType +
                "; mMode=" + mMode);
    }

    private Runnable mIndexCyclerRunnable = new Runnable() {
        @Override
        public void run() {
            final int oldIndex = mCurrentIndex++;
            if (mCurrentIndex >= mCtrl.getList().size()) mCurrentIndex = 0;
            if (DEBUG) log("IndexCycler: oldIndex=" + oldIndex + "; " +
                    "mCurrentIndex=" + mCurrentIndex);
            if (mCurrentIndex != oldIndex) {
                updateProgressView(true);
            }

            if (mCtrl.getList().size() > 0) {
                ProgressBarView.this.postDelayed(this, INDEX_CYCLER_FREQUENCY);
            }
        }
    };

    private String getCurrentId() {
        return (mCurrentIndex < mCtrl.getList().size() ? 
                ((ProgressInfo)mCtrl.getList().values().toArray()[mCurrentIndex]).id :
                null);
    }

    private void resetIndexCycler(int toIndex) {
        removeCallbacks(mIndexCyclerRunnable);
        mCurrentIndex = toIndex;
        if (mCtrl.getList().size() > 0 && isValidStatusBarState()) {
            postDelayed(mIndexCyclerRunnable, INDEX_CYCLER_FREQUENCY);
        }
    }

    private void updateProgressView(boolean fadeOutAndIn) {
        clearAnimation();
        if (isValidStatusBarState() && !mCtrl.getList().isEmpty()) {
            if (mAnimator.isStarted()) {
                mAnimator.cancel();
            }
            ProgressInfo pi = (ProgressInfo) mCtrl.getList().values().toArray()[mCurrentIndex];
            float newScaleX = pi.getFraction();
            if (DEBUG) log("updateProgressView: id='" + 
                    pi.id + "'; newScaleX=" + newScaleX);
            if (getVisibility() != View.VISIBLE) {
                fadeIn(newScaleX);
            } else if (fadeOutAndIn) {
                fadeOutAndIn(newScaleX);
            } else if (mAnimated) {
                animateScaleXTo(newScaleX);
            } else {
                setScaleX(newScaleX);
            }
        } else {
            removeCallbacks(mIndexCyclerRunnable);
            if (mAnimator.isStarted()) {
                mAnimator.end();
            }
            if (getVisibility() == View.VISIBLE) {
                fadeOut();
            }
        }
    }

    private void animateScaleXTo(float newScaleX) {
        if (mAnimator.isStarted()) {
            mAnimator.cancel();
        }
        mAnimator.setValues(PropertyValuesHolder.ofFloat("scaleX", getScaleX(), newScaleX));
        mAnimator.start();
        if (DEBUG) log("Animating to new scaleX: " + newScaleX);
    }

    private void fadeOutAndIn(final float newScaleX) {
        animate()
            .alpha(0f)
            .setDuration(ANIM_DURATION / 2)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    setScaleX(newScaleX);
                    animate()
                        .alpha(1f)
                        .setDuration(ANIM_DURATION / 2);
                }
            });
    }

    private void fadeIn(final float newScaleX) {
        setAlpha(0f);
        setScaleX(newScaleX);
        setVisibility(View.VISIBLE);
        animate()
            .alpha(1f)
            .setDuration(ANIM_DURATION);
    }

    private void fadeOut() {
        animate()
            .alpha(0f)
            .setDuration(ANIM_DURATION)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    setVisibility(View.GONE);
                    setScaleX(0f);
                    setAlpha(1f);
                }
            });
    }

    private void updatePosition() {
        if (mMode == Mode.OFF) return;

        MarginLayoutParams lp = null;
        if (mContainerType == ContainerType.STATUSBAR) {
            lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    mHeightPx);
            ((FrameLayout.LayoutParams)lp).gravity = mMode == Mode.TOP ? 
                    Gravity.TOP : Gravity.BOTTOM;
        } else if (mContainerType == ContainerType.KEYGUARD) {
            lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                    mHeightPx);
            if (mMode == Mode.TOP) {
                ((RelativeLayout.LayoutParams)lp).addRule(RelativeLayout.ALIGN_PARENT_TOP,
                        RelativeLayout.TRUE);
            } else {
                ((RelativeLayout.LayoutParams)lp).addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,
                        RelativeLayout.TRUE);
            }
        }

        if (lp != null) {
            lp.setMargins(0, mMode == Mode.TOP ? mEdgeMarginPx : 0,
                          0, mMode == Mode.BOTTOM ? mEdgeMarginPx : 0);
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
        if ((flags & StatusBarIconManager.FLAG_ICON_COLOR_CHANGED) != 0) {
            setBackgroundColor(colorInfo.coloringEnabled ?
                    colorInfo.iconColor[0] : Color.WHITE);
        } else if ((flags & StatusBarIconManager.FLAG_ICON_TINT_CHANGED) != 0) {
            setBackgroundColor(colorInfo.iconTint);
        }
        if ((flags & StatusBarIconManager.FLAG_ICON_ALPHA_CHANGED) != 0) {
            setAlpha(colorInfo.alphaTextAndBattery);
        }
    }

    @Override
    public void onStatusBarStateChanged(int oldState, int newState) {
        if (mStatusBarState != newState) {
            mStatusBarState = newState;
            resetIndexCycler(mCurrentIndex);
            if (isValidStatusBarState()) {
                updateProgressView(false);
            } else {
                setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onProgressPreferencesChanged(Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_STATUSBAR_DOWNLOAD_PROGRESS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_ANIMATED)) {
                mAnimated = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_ANIMATED, true);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_CENTERED)) {
                mCentered = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_CENTERED, false);
                setPivotX(mCentered ? getWidth()/2f : 
                    Utils.isRTL(getContext()) ? getWidth() : 0f);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_THICKNESS)) {
                mHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        intent.getIntExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_THICKNESS, 1),
                        getResources().getDisplayMetrics());
                updatePosition();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_MARGIN)) {
                mEdgeMarginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        intent.getIntExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_MARGIN, 0),
                        getResources().getDisplayMetrics());
                updatePosition();
            }
        }
    }
}
