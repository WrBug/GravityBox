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

package com.wrbug.gravitybox.nougat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager;
import com.wrbug.gravitybox.nougat.managers.SysUiManagers;
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager.ColorInfo;
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager.IconManagerListener;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.style.RelativeSizeSpan;
import android.view.Display;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.widget.TextView;

public class StatusbarClock implements IconManagerListener, BroadcastSubReceiver {
    private static final String TAG = "GB:StatusbarClock";
    private static final boolean DEBUG = false;

    private TextView mClock;
    private int mDefaultClockColor;
    private int mOriginalPaddingLeft;
    private boolean mAmPmHide;
    private String mClockShowDate = "disabled";
    private int mClockShowDow = GravityBoxSettings.DOW_DISABLED;
    private boolean mClockHidden;
    private float mDowSize;
    private float mAmPmSize;
    private boolean mShowSeconds;
    private SimpleDateFormat mSecondsFormat;
    private Handler mSecondsHandler;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public StatusbarClock(XSharedPreferences prefs) {
        mClockShowDate = prefs.getString(GravityBoxSettings.PREF_KEY_STATUSBAR_CLOCK_DATE, "disabled");
        mClockShowDow = Integer.valueOf(
                prefs.getString(GravityBoxSettings.PREF_KEY_STATUSBAR_CLOCK_DOW, "0"));
        mAmPmHide = prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_CLOCK_AMPM_HIDE, false);
        mClockHidden = prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_CLOCK_HIDE, false);
        mDowSize = prefs.getInt(GravityBoxSettings.PREF_KEY_STATUSBAR_CLOCK_DOW_SIZE, 70) / 100f;
        mAmPmSize = prefs.getInt(GravityBoxSettings.PREF_KEY_STATUSBAR_CLOCK_AMPM_SIZE, 70) / 100f;
        mShowSeconds = prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_CLOCK_SHOW_SECONDS, false);
    }

    public TextView getClock() {
        return mClock;
    }

    public void resetOriginalPaddingLeft() {
        if (mClock != null) {
            mClock.setPadding(mOriginalPaddingLeft, 0, 0, 0);
        }
    }

    public void setClock(TextView clock) {
        if (clock == null) throw new IllegalArgumentException("Clock cannot be null");

        mClock = clock;
        mDefaultClockColor = mClock.getCurrentTextColor();
        mOriginalPaddingLeft = mClock.getPaddingLeft();

        // use this additional field to identify the instance of Clock that resides in status bar
        XposedHelpers.setAdditionalInstanceField(mClock, "sbClock", true);

        // hide explicitly if desired
        if (mClockHidden) {
            mClock.setVisibility(View.GONE);
        }

        // update seconds handler when attached state changes
        mClock.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                updateSecondsHandler();
            }
            @Override
            public void onViewDetachedFromWindow(View v) {
                updateSecondsHandler();
            }
        });

        hookGetSmallTime();
    }

    private void updateClock() {
        try {
            XposedHelpers.callMethod(mClock, "updateClock");
        } catch (Throwable t) {
            log("Error in updateClock: " + t.getMessage());
        }
    }

    private void updateSecondsHandler() {
        if (mClock == null) return;

        if (mShowSeconds && mClock.getDisplay() != null) {
            mSecondsHandler = new Handler();
            if (mClock.getDisplay().getState() == Display.STATE_ON && !mClockHidden) {
                mSecondsHandler.postAtTime(mSecondTick,
                        SystemClock.uptimeMillis() / 1000 * 1000 + 1000);
            }
        } else if (mSecondsHandler != null) {
            mSecondsHandler.removeCallbacks(mSecondTick);
            mSecondsHandler = null;
            updateClock();
        }
    }

    public void setClockVisibility(boolean show) {
        if (mClock != null) {
            mClock.setVisibility(show && !mClockHidden ? View.VISIBLE : View.GONE);
            if (mClock.getVisibility() == View.VISIBLE) { 
                if (mSecondsHandler != null) {
                    mSecondsHandler.postAtTime(mSecondTick,
                            SystemClock.uptimeMillis() / 1000 * 1000 + 1000);
                }
            } else if (mSecondsHandler != null) {
                mSecondsHandler.removeCallbacks(mSecondTick);
            }
        }
    }

    public void setClockVisibility() {
        setClockVisibility(true);
    }

    public void updateColor(int color) {
        if (mClock != null) {
            mClock.setTextColor(color);
        }
    }

    public void refreshColor() {
        if (SysUiManagers.IconManager != null &&
                SysUiManagers.IconManager.isColoringEnabled()) {
            updateColor(SysUiManagers.IconManager.getIconColor());
        }
    }

    private void hookGetSmallTime() {
        try {
            XposedHelpers.findAndHookMethod(mClock.getClass(), "getSmallTime", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // is this a status bar Clock instance?
                    // yes, if it contains our additional sbClock field
                    if (DEBUG) log("getSmallTime() called. mAmPmHide=" + mAmPmHide);
                    Object sbClock = XposedHelpers.getAdditionalInstanceField(param.thisObject, "sbClock");
                    if (DEBUG) log("Is statusbar clock: " + (sbClock == null ? "false" : "true"));
                    Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
                    boolean is24 = DateFormat.is24HourFormat(mClock.getContext());
                    String clockText = param.getResult().toString();
                    if (DEBUG) log("Original clockText: '" + clockText + "'");
                    // generate fresh base time text if seconds enabled
                    if (mShowSeconds) {
                        if (mSecondsFormat == null) {
                            mSecondsFormat = new SimpleDateFormat(
                                    DateFormat.getBestDateTimePattern(
                                    Locale.getDefault(), is24 ? "Hms" : "hms"));
                        }
                        clockText = mSecondsFormat.format(calendar.getTime());
                        if (DEBUG) log("New clock text with seconds: " + clockText);
                    }
                    String amPm = calendar.getDisplayName(
                            Calendar.AM_PM, Calendar.SHORT, Locale.getDefault());
                    if (DEBUG) log("Locale specific AM/PM string: '" + amPm + "'");
                    int amPmIndex = clockText.indexOf(amPm);
                    if (DEBUG) log("Original AM/PM index: " + amPmIndex);
                    if (mAmPmHide && amPmIndex != -1) {
                        clockText = clockText.replace(amPm, "").trim();
                        if (DEBUG) log("AM/PM removed. New clockText: '" + clockText + "'");
                        amPmIndex = -1;
                    } else if (!mAmPmHide && !is24 && amPmIndex == -1) {
                        // insert AM/PM if missing
                        if(Locale.getDefault().equals(Locale.TAIWAN) || Locale.getDefault().equals(Locale.CHINA)) {
                            clockText = amPm + " " + clockText;
                        } else {
                            clockText += " " + amPm;
                        }
                        amPmIndex = clockText.indexOf(amPm);
                        if (DEBUG) log("AM/PM added. New clockText: '" + clockText + "'; New AM/PM index: " + amPmIndex);
                    }
                    CharSequence date = "";
                    // apply date to statusbar clock, not the notification panel clock
                    if (!mClockShowDate.equals("disabled") && sbClock != null) {
                        SimpleDateFormat df = (SimpleDateFormat) SimpleDateFormat.getDateInstance(SimpleDateFormat.SHORT);
                        String pattern = mClockShowDate.equals("localized") ?
                                df.toLocalizedPattern().replaceAll(".?[Yy].?", "") : mClockShowDate;
                        date = new SimpleDateFormat(pattern, Locale.getDefault()).format(calendar.getTime()) + " ";
                    }
                    clockText = date + clockText;
                    CharSequence dow = "";
                    // apply day of week only to statusbar clock, not the notification panel clock
                    if (mClockShowDow != GravityBoxSettings.DOW_DISABLED && sbClock != null) {
                        dow = getFormattedDow(calendar.getDisplayName(
                                Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())) + " ";
                    }
                    clockText = dow + clockText;
                    SpannableStringBuilder sb = new SpannableStringBuilder(clockText);
                    sb.setSpan(new RelativeSizeSpan(mDowSize), 0, dow.length() + date.length(),
                            Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    if (amPmIndex > -1) {
                        if(Locale.getDefault().equals(Locale.TAIWAN) || Locale.getDefault().equals(Locale.CHINA)) {
                            sb.setSpan(new RelativeSizeSpan(mAmPmSize), dow.length() + date.length() + amPmIndex,
                                    dow.length() + date.length() + amPmIndex + amPm.length(),
                                    Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                        } else {
                            int offset = Character.isWhitespace(clockText.charAt(dow.length() + date.length() + amPmIndex - 1)) ?
                                    1 : 0;
                            sb.setSpan(new RelativeSizeSpan(mAmPmSize), dow.length() + date.length() + amPmIndex - offset,
                                    dow.length() + date.length() + amPmIndex + amPm.length(),
                                    Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                        }
                    }
                    if (DEBUG) log("Final clockText: '" + sb + "'");
                    param.setResult(sb);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private String getFormattedDow(String inDow) {
        switch (mClockShowDow) {
            case GravityBoxSettings.DOW_LOWERCASE: 
                return inDow.toLowerCase(Locale.getDefault());
            case GravityBoxSettings.DOW_UPPERCASE:
                return inDow.toUpperCase(Locale.getDefault());
            case GravityBoxSettings.DOW_STANDARD:
            default: return inDow;
        }
    }

    private final Runnable mSecondTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            mSecondsHandler.postAtTime(this, SystemClock.uptimeMillis() / 1000 * 1000 + 1000);
        }
    };

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if (mClock != null && (flags & StatusBarIconManager.FLAG_ICON_COLOR_CHANGED) != 0) {
            if (colorInfo.coloringEnabled) {
                updateColor(colorInfo.iconColor[0]);
            } else {
                updateColor(mDefaultClockColor);
            }
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_CLOCK_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_CLOCK_DOW)) {
                mClockShowDow = intent.getIntExtra(GravityBoxSettings.EXTRA_CLOCK_DOW,
                        GravityBoxSettings.DOW_DISABLED);
                updateClock();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_AMPM_HIDE)) {
                mAmPmHide = intent.getBooleanExtra(GravityBoxSettings.EXTRA_AMPM_HIDE, false);
                updateClock();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_CLOCK_HIDE)) {
                mClockHidden = intent.getBooleanExtra(GravityBoxSettings.EXTRA_CLOCK_HIDE, false);
                setClockVisibility();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_CLOCK_DOW_SIZE)) {
                mDowSize = intent.getIntExtra(GravityBoxSettings.EXTRA_CLOCK_DOW_SIZE, 70) / 100f;
                updateClock();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_AMPM_SIZE)) {
                mAmPmSize = intent.getIntExtra(GravityBoxSettings.EXTRA_AMPM_SIZE, 70) / 100f;
                updateClock();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_CLOCK_DATE)) {
                mClockShowDate = intent.getStringExtra(GravityBoxSettings.EXTRA_CLOCK_DATE);
                updateClock();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_CLOCK_SHOW_SECONDS)) {
                mShowSeconds = intent.getBooleanExtra(GravityBoxSettings.EXTRA_CLOCK_SHOW_SECONDS, false);
                updateSecondsHandler();
            }
        }
        if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            if (mSecondsHandler != null && !mClockHidden) {
                mSecondsHandler.postAtTime(mSecondTick,
                        SystemClock.uptimeMillis() / 1000 * 1000 + 1000);
            }
        }
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            if (mSecondsHandler != null) {
                mSecondsHandler.removeCallbacks(mSecondTick);
            }
        }
        if (intent.getAction().equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            mSecondsFormat = null;
        }
    }
}
