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

import de.robv.android.xposed.XSharedPreferences;

import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.managers.SysUiManagers;
import com.wrbug.gravitybox.nougat.managers.BatteryInfoManager.BatteryData;
import com.wrbug.gravitybox.nougat.managers.BatteryInfoManager.BatteryStatusListener;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;

public class BatteryTile extends QsTile {

    private boolean mIsReceiving;
    private BatteryData mBatteryData;
    private BatteryView mBatteryView;
    private boolean mShowPercentage;
    private boolean mSaverIndicate;
    private String mTempUnit;
    private boolean mSwapActions;
    private boolean mShowTemp;
    private boolean mShowVoltage;

    public BatteryTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);
    }

    private BatteryStatusListener mBatteryStatusListener = new BatteryStatusListener() {
        @Override
        public void onBatteryStatusChanged(BatteryData batteryData) {
            mBatteryData = batteryData;
            if (DEBUG) log("mBatteryData=" + mBatteryData.toString());
            refreshState();
        }
    };

    private void registerReceiver() {
        if (mIsReceiving) return;
        if (SysUiManagers.BatteryInfoManager != null) {
            SysUiManagers.BatteryInfoManager.registerListener(mBatteryStatusListener);
            if (DEBUG) log(getKey() + ": registerReceiver: battery status listener registered");
        }
        mIsReceiving = true;
    }

    private void unregisterReceiver() {
        if (mIsReceiving) {
            if (SysUiManagers.BatteryInfoManager != null) {
                SysUiManagers.BatteryInfoManager.unregisterListener(mBatteryStatusListener);
                if (DEBUG) log(getKey() + ": unregisterReceiver: battery status listener unregistered");
            }
            mIsReceiving = false;
        }
    }

    @Override
    protected void initPreferences() {
        super.initPreferences();

        mShowPercentage = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_TILE_PERCENTAGE, false);
        mSaverIndicate = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_TILE_SAVER_INDICATE, false);
        mTempUnit = mPrefs.getString(GravityBoxSettings.PREF_KEY_BATTERY_TILE_TEMP_UNIT, "C");
        mSwapActions = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_TILE_SWAP_ACTIONS, false);
        mShowTemp = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_TILE_TEMP, true);
        mShowVoltage = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_TILE_VOLTAGE, true);
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        super.onBroadcastReceived(context, intent);

        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_TILE_PERCENTAGE)) {
                mShowPercentage = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_BATTERY_TILE_PERCENTAGE, false);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_TILE_SAVER_INDICATE)) {
                mSaverIndicate = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_BATTERY_TILE_SAVER_INDICATE, false);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_TILE_TEMP_UNIT)) {
                mTempUnit = intent.getStringExtra(
                        GravityBoxSettings.EXTRA_BATTERY_TILE_TEMP_UNIT);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_TILE_SWAP_ACTIONS)) {
                mSwapActions = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_BATTERY_TILE_SWAP_ACTIONS, false);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_TILE_TEMP)) {
                mShowTemp = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_BATTERY_TILE_TEMP, true);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_TILE_VOLTAGE)) {
                mShowVoltage = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_BATTERY_TILE_VOLTAGE, true);
            }
        }
    }

    @Override
    public View onCreateIcon() {
        mBatteryView = new BatteryView(mContext);
        return mBatteryView;
    }

    @Override
    public void setListening(boolean listening) {
        if (listening && mEnabled) {
            registerReceiver();
        } else {
            unregisterReceiver();
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        if (mBatteryData == null) {
            mState.visible = false;
            if (DEBUG) log(getKey() + ": handleUpdateState: battery data is null");
        } else {
            mState.visible = true;
            mState.label = "";
            if (mShowTemp && mShowVoltage) {
                mState.label = String.format("%.1f\u00b0%s, %dmV",
                        mBatteryData.getTemp(mTempUnit), mTempUnit, mBatteryData.voltage);
            } else if (mShowTemp) {
                mState.label = String.format("%.1f\u00b0%s",
                        mBatteryData.getTemp(mTempUnit), mTempUnit);
            } else if (mShowVoltage) {
                mState.label = String.format("%dmV", mBatteryData.voltage);
            }
            if (mBatteryData.charging && mShowPercentage) {
                if (mState.label.isEmpty()) {
                    mState.label = String.format("%d%%", mBatteryData.level);
                } else {
                    mState.label = String.format("%d%%, %s", mBatteryData.level, mState.label);
                }
            }
            if (mState.label.isEmpty()) {
                mState.label = mGbContext.getString(R.string.qs_tile_battery);
            }
        }
        if (mBatteryView != null) {
            mBatteryView.postInvalidate();
        }
        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        if (mSwapActions) {
            startSettingsActivity(Intent.ACTION_POWER_USAGE_SUMMARY);
        } else {
            togglePowerSaving();
        }
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        if (mSwapActions) {
            togglePowerSaving();
        } else {
            startSettingsActivity(Intent.ACTION_POWER_USAGE_SUMMARY);
        }
        return true;
    }

    private void togglePowerSaving() {
        if (SysUiManagers.BatteryInfoManager != null) {
            SysUiManagers.BatteryInfoManager.togglePowerSaving();
        }
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mBatteryStatusListener = null;
        mBatteryData = null;
        mBatteryView = null;
    }

    private class BatteryView extends ImageView {
        private static final boolean SINGLE_DIGIT_PERCENT = false;
        private static final boolean SHOW_100_PERCENT = false;

        private final int[] LEVELS = new int[] { 4, 15, 100 };
        private final int[] COLORS = new int[] { 0xFFFF3300, 0xFFFF3300, 0xFFFFFFFF };
        private static final int BOLT_COLOR = 0xB2000000;
        private static final int FULL = 96;
        private static final int EMPTY = 4;
        private static final int COLOR_BATTERY_SAVER_DEFAULT = 0xfff4511e;

        private static final float SUBPIXEL = 0.4f;  // inset rects for softer edges

        private int[] mColors;
        private Integer mBatterySaverColor;

        private Paint mFramePaint, mBatteryPaint, mWarningTextPaint, mTextPaint, mBoltPaint;
        private int mButtonHeight;
        private float mTextHeight, mWarningTextHeight;

        private int mHeight;
        private int mWidth;
        private String mWarningString;
        private int mChargeColor;
        private final float[] mBoltPoints;
        private final Path mBoltPath = new Path();

        private final RectF mFrame = new RectF();
        private final RectF mButtonFrame = new RectF();
        private final RectF mClipFrame = new RectF();
        private final Rect mBoltFrame = new Rect();

        public BatteryView(Context context) {
            super(context);

            mWarningString = "!";

            mFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mFramePaint.setDither(true);
            mFramePaint.setStrokeWidth(0);
            mFramePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mFramePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP));

            mBatteryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mBatteryPaint.setDither(true);
            mBatteryPaint.setStrokeWidth(0);
            mBatteryPaint.setStyle(Paint.Style.FILL_AND_STROKE);

            mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Typeface font = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
            mTextPaint.setTypeface(font);
            mTextPaint.setTextAlign(Paint.Align.CENTER);

            mWarningTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            font = Typeface.create("sans-serif", Typeface.BOLD);
            mWarningTextPaint.setTypeface(font);
            mWarningTextPaint.setTextAlign(Paint.Align.CENTER);

            mBoltPaint = new Paint();
            mBoltPaint.setAntiAlias(true);
            mBoltPoints = loadBoltPoints();
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            setColor(Color.WHITE);

            setId(android.R.id.icon);
            setScaleType(ScaleType.CENTER_INSIDE);

            int padding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2,
                    getResources().getDisplayMetrics()));
            this.setPadding(0, padding, 0, padding);
        }

        private void setColor(int mainColor) {
            COLORS[COLORS.length-1] = mainColor;

            final int N = LEVELS.length;
            mColors = new int[2*N];
            for (int i=0; i<N; i++) {
                mColors[2*i] = LEVELS[i];
                mColors[2*i+1] = COLORS[i];
            }

            mTextPaint.setColor(Color.BLACK);
            mWarningTextPaint.setColor(COLORS[0]);
            mBoltPaint.setColor(BOLT_COLOR);
            mChargeColor = mainColor;
            invalidate();
        }

        private int getBatterySaverColor() {
            if (mBatterySaverColor != null) return mBatterySaverColor;
            mBatterySaverColor = COLOR_BATTERY_SAVER_DEFAULT;
            try {
                Resources res = getResources();
                int resId = res.getIdentifier("battery_saver_mode_color", "color", "android");
                if (resId != 0) {
                    mBatterySaverColor = res.getColor(resId);
                }
            } catch (Throwable t) { /* well... */ }
            return mBatterySaverColor;
        }

        private float[] loadBoltPoints() {
            final int[] pts = new int[] { 73,0,392,0,201,259,442,259,4,703,157,334,0,334 };
            int maxX = 0, maxY = 0;
            for (int i = 0; i < pts.length; i += 2) {
                maxX = Math.max(maxX, pts[i]);
                maxY = Math.max(maxY, pts[i + 1]);
            }
            final float[] ptsF = new float[pts.length];
            for (int i = 0; i < pts.length; i += 2) {
                ptsF[i] = (float)pts[i] / maxX;
                ptsF[i + 1] = (float)pts[i + 1] / maxY;
            }
            return ptsF;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final Resources res = getResources();
            int width = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    13, res.getDisplayMetrics()) * mScalingFactor);
            setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            mHeight = h;
            mWidth = w;
            mWarningTextPaint.setTextSize(h * 0.75f);
            mWarningTextHeight = -mWarningTextPaint.getFontMetrics().ascent;
        }

        private int getColorForLevel(int percent) {
            int thresh, color = 0;
            for (int i=0; i<mColors.length; i+=2) {
                thresh = mColors[i];
                color = mColors[i+1];
                if (percent <= thresh) return color;
            }
            return color;
        }

        @Override
        public void draw(Canvas c) {
            if (mBatteryData == null || mBatteryData.level < 0) return;

            float drawFrac = (float) mBatteryData.level / 100f;
            final int pt = getPaddingTop();
            final int pl = getPaddingLeft();
            final int pr = getPaddingRight();
            final int pb = getPaddingBottom();
            int height = mHeight - pt - pb;
            int width = mWidth - pl - pr;

            mButtonHeight = (int) (height * 0.12f);

            mFrame.set(0, 0, width, height);
            mFrame.offset(pl, pt);

            mButtonFrame.set(
                    mFrame.left + width * 0.25f,
                    mFrame.top,
                    mFrame.right - width * 0.25f,
                    mFrame.top + mButtonHeight + 5 /*cover frame border of intersecting area*/);

            mButtonFrame.top += SUBPIXEL;
            mButtonFrame.left += SUBPIXEL;
            mButtonFrame.right -= SUBPIXEL;

            mFrame.top += mButtonHeight;
            mFrame.left += SUBPIXEL;
            mFrame.top += SUBPIXEL;
            mFrame.right -= SUBPIXEL;
            mFrame.bottom -= SUBPIXEL;

            // first, draw the battery shape
            mFramePaint.setColor(mSaverIndicate && mBatteryData.isPowerSaving ? 
                    getBatterySaverColor() : COLORS[COLORS.length-1]);
            mFramePaint.setAlpha(102);
            c.drawRect(mFrame, mFramePaint);

            // fill 'er up
            final int color = mBatteryData.charging ? 
                    mChargeColor : mSaverIndicate && mBatteryData.isPowerSaving ?
                            getBatterySaverColor() : getColorForLevel(mBatteryData.level);
            mBatteryPaint.setColor(color);

            if (mBatteryData.level >= FULL) {
                drawFrac = 1f;
            } else if (mBatteryData.level <= EMPTY) {
                drawFrac = 0f;
            }

            c.drawRect(mButtonFrame, drawFrac == 1f ? mBatteryPaint : mFramePaint);

            mClipFrame.set(mFrame);
            mClipFrame.top += (mFrame.height() * (1f - drawFrac));

            c.save(Canvas.CLIP_SAVE_FLAG);
            c.clipRect(mClipFrame);
            c.drawRect(mFrame, mBatteryPaint);
            c.restore();

            if (mBatteryData.charging) {
                // draw the bolt
                final int bl = (int)(mFrame.left + mFrame.width() / 4.5f);
                final int bt = (int)(mFrame.top + mFrame.height() / 6f);
                final int br = (int)(mFrame.right - mFrame.width() / 7f);
                final int bb = (int)(mFrame.bottom - mFrame.height() / 10f);
                if (mBoltFrame.left != bl || mBoltFrame.top != bt
                        || mBoltFrame.right != br || mBoltFrame.bottom != bb) {
                    mBoltFrame.set(bl, bt, br, bb);
                    mBoltPath.reset();
                    mBoltPath.moveTo(
                            mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                            mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
                    for (int i = 2; i < mBoltPoints.length; i += 2) {
                        mBoltPath.lineTo(
                                mBoltFrame.left + mBoltPoints[i] * mBoltFrame.width(),
                                mBoltFrame.top + mBoltPoints[i + 1] * mBoltFrame.height());
                    }
                    mBoltPath.lineTo(
                            mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                            mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
                }
                c.drawPath(mBoltPath, mBoltPaint);
            } else if (mBatteryData.level <= EMPTY) {
                final float x = mWidth * 0.5f;
                final float y = (mHeight + mWarningTextHeight) * 0.48f;
                c.drawText(mWarningString, x, y, mWarningTextPaint);
            } else if (mShowPercentage && !(mBatteryData.level == 100 && !SHOW_100_PERCENT)) {
                mTextPaint.setTextSize(height *
                        (SINGLE_DIGIT_PERCENT ? 0.75f
                                : (mBatteryData.level == 100 ? 0.38f : 0.5f)));
                mTextPaint.setColor(mBatteryData.level <= 40 ? Color.WHITE : Color.BLACK);
                mTextHeight = -mTextPaint.getFontMetrics().ascent;

                final String str = String.valueOf(SINGLE_DIGIT_PERCENT ? 
                        (mBatteryData.level/10) : mBatteryData.level);
                final float x = mWidth * 0.5f;
                final float y = (mHeight + mTextHeight) * 0.47f;
                c.drawText(str,
                        x,
                        y,
                        mTextPaint);
            }
        }
    }
}
