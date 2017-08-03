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

package com.wrbug.gravitybox.nougat;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.wrbug.gravitybox.nougat.util.DensityUtils;
import com.wrbug.gravitybox.nougat.util.GraphicUtils;
import com.wrbug.gravitybox.nougat.util.LogUtils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModClearAllRecents {
    private static final String TAG = "GB:ModClearAllRecents";
    public static final String PACKAGE_NAME = "com.android.systemui";
    public static final String CLASS_RECENT_ACTIVITY = "com.android.systemui.recents.RecentsActivity";
    private static final String CLASS_SYSTEM_BAR_SCRIM_VIEWS = "com.android.systemui.recents.views.SystemBarScrimViews";
    public static final String CLASS_SWIPE_HELPER = "com.android.systemui.SwipeHelper";
    public static final String CLASS_TASK_STACK_VIEW = "com.android.systemui.recents.views.TaskStackView";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String CLASS_TASK_THUMB_NAIL = "com.android.systemui.recents.views.TaskViewThumbnail";
    private static final String CLASS_RECENTS_VIEW = "com.android.systemui.recents.views.RecentsView";

    private enum SearchBarState {DEFAULT, HIDE_KEEP_SPACE, HIDE_REMOVE_SPACE}

    private static Object mScrimViews;
    private static boolean hasStackTasks;
    private static int mButtonGravity;
    private static int mMarginTopPx;
    private static int recentTaskAlpha;
    private static int taskMaskColor;
    private static int mMarginBottomPx;
    private static boolean mNavbarLeftHanded;
    private static ViewGroup mRecentsView;
    private static TextView mStackActionButton;
    private static SearchBarState mSearchBarState;
    private static SearchBarState mSearchBarStatePrev;
    private static Integer mSearchBarOriginalHeight;
    private static boolean mClearAllUseAltIcon;
    private static Interpolator mExitAnimInterpolator;
    private static int mExitAnimDuration;
    private static Activity mRecentsActivity;
    private static boolean mClearVisible;

    // RAM bar
    private static TextView mBackgroundProcessText;
    private static TextView mForegroundProcessText;
    private static ActivityManager mAm;
    private static MemInfoReader mMemInfoReader;
    private static Context mGbContext;
    private static LinearColorBar mRamUsageBar;
    private static int mRamBarGravity;
    private static Handler mHandler;
    private static int[] mRamUsageBarPaddings;
    private static int mClearAllRecentsSizePx;
    private static int mRamUsageBarVerticalMargin;
    private static int mRamUsageBarHorizontalMargin;
    private static int cleanBtnLocation;
    private static String clearBtnText;
    private static int clearBtnOffset;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_RECENTS_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_RECENTS_CLEAR_ALL)) {
                    cleanBtnLocation = intent.getIntExtra(GravityBoxSettings.EXTRA_RECENTS_CLEAR_ALL, 1);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_RECENTS_RAMBAR)) {
                    mRamBarGravity = intent.getIntExtra(GravityBoxSettings.EXTRA_RECENTS_RAMBAR, 0);
                    updateRamBarLayout();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_RECENTS_MARGIN_TOP)) {
                    mMarginTopPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                            intent.getIntExtra(GravityBoxSettings.EXTRA_RECENTS_MARGIN_TOP, 77),
                            context.getResources().getDisplayMetrics());
                    updateRamBarLayout();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_RECENTS_MARGIN_BOTTOM)) {
                    mMarginBottomPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                            intent.getIntExtra(GravityBoxSettings.EXTRA_RECENTS_MARGIN_BOTTOM, 50),
                            context.getResources().getDisplayMetrics());
                    updateRamBarLayout();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_RECENTS_SEARCH_BAR)) {
                    mSearchBarStatePrev = mSearchBarState;
                    mSearchBarState = SearchBarState.valueOf(intent.getStringExtra(
                            GravityBoxSettings.EXTRA_RECENTS_SEARCH_BAR));
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_RECENTS_CLEAR_ALL_ICON_ALT)) {
                    mClearAllUseAltIcon = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_RECENTS_CLEAR_ALL_ICON_ALT, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_RECENTS_CLEAR_ALL_VISIBLE)) {
                    mClearVisible = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_RECENTS_CLEAR_ALL_VISIBLE, false);
                }
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_RECENTS_ALPHA)) {
                recentTaskAlpha = intent.getIntExtra(GravityBoxSettings.PREF_RECENT_TASK_ALPHA, 0);
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_TASK_MASK_COLOR_CHANGED)) {
                taskMaskColor = intent.getIntExtra(GravityBoxSettings.PREF_RECENT_TASK_MASK_COLOR, Color.WHITE);
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_RECENTS_CLEAR_ALL_BTN_CHANGED)) {
                clearBtnText = intent.getStringExtra(GravityBoxSettings.PREF_KEY_RECENTS_CLEAR_ALL_BUTTON_TEXT);
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_TASK_CLEAR_BTN_OFFSET_CHANGED)) {
                clearBtnOffset = intent.getIntExtra(GravityBoxSettings.PREF_KEY_TASK_CLEAR_BTN_OFFSET, 30);
            }
            if (intent.getAction().equals(ModHwKeys.ACTION_RECENTS_CLEAR_ALL_SINGLETAP)) {
                clearAll();
            }
        }
    };

    public static void init(final XSharedPreferences prefs, ClassLoader classLoader) {
        try {
            Class<?> recentActivityClass = XposedHelpers.findClass(CLASS_RECENT_ACTIVITY, classLoader);
            Class<?> systemBarScrimViewsClass = XposedHelpers.findClass(CLASS_SYSTEM_BAR_SCRIM_VIEWS, classLoader);
            mButtonGravity = Integer.valueOf(prefs.getString(GravityBoxSettings.PREF_KEY_RECENTS_CLEAR_ALL, "1"));
            mRamBarGravity = Integer.valueOf(prefs.getString(GravityBoxSettings.PREF_KEY_RAMBAR, "0"));
            mNavbarLeftHanded = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_OVERRIDE, false) &&
                    prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_ENABLE, false) &&
                    prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_LEFT_HANDED, false);
            mSearchBarState = SearchBarState.valueOf(prefs.getString(
                    GravityBoxSettings.PREF_KEY_RECENTS_SEARCH_BAR, "DEFAULT"));
            mClearAllUseAltIcon = prefs.getBoolean(GravityBoxSettings.PREF_KEY_RECENTS_CLEAR_ALL_ICON_ALT, false);
            mClearVisible = prefs.getBoolean(GravityBoxSettings.PREF_KEY_RECENTS_CLEAR_ALL_VISIBLE, false);
            mSearchBarStatePrev = mSearchBarState;
            mMemInfoReader = new MemInfoReader();
            cleanBtnLocation = Integer.valueOf(prefs.getString(GravityBoxSettings.PREF_KEY_RECENTS_CLEAR_ALL, "1"));
            clearBtnText = prefs.getString(GravityBoxSettings.PREF_KEY_RECENTS_CLEAR_ALL_BUTTON_TEXT, "");
            clearBtnOffset = prefs.getInt(GravityBoxSettings.PREF_KEY_TASK_CLEAR_BTN_OFFSET, 30);
            XposedHelpers.findAndHookMethod(recentActivityClass, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mRecentsActivity = (Activity) param.thisObject;
                    mGbContext = Utils.getGbContext(mRecentsActivity);
                    mHandler = new Handler();
                    mAm = (ActivityManager) mRecentsActivity.getSystemService(Context.ACTIVITY_SERVICE);
                    mRecentsView = (ViewGroup) XposedHelpers.getObjectField(param.thisObject, "mRecentsView");
                    mStackActionButton = (TextView) XposedHelpers.getObjectField(mRecentsView, "mStackActionButton");
                    if (mStackActionButton == null) {
                        mStackActionButton = new TextView(mRecentsActivity);
                    }
                    mStackActionButton.setGravity(Gravity.CENTER);
                    final Resources res = mRecentsActivity.getResources();
                    mScrimViews = XposedHelpers.getObjectField(param.thisObject, "mScrimViews");
                    mMarginTopPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                            prefs.getInt(GravityBoxSettings.PREF_KEY_RECENTS_CLEAR_MARGIN_TOP, 77),
                            res.getDisplayMetrics());
                    recentTaskAlpha = prefs.getInt(GravityBoxSettings.PREF_RECENT_TASK_ALPHA, 100);
                    taskMaskColor = prefs.getInt(GravityBoxSettings.PREF_RECENT_TASK_MASK_COLOR, Color.WHITE);
                    mMarginBottomPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                            prefs.getInt(GravityBoxSettings.PREF_KEY_RECENTS_CLEAR_MARGIN_BOTTOM, 50),
                            res.getDisplayMetrics());

                    mRamUsageBarPaddings = new int[4];
                    mRamUsageBarPaddings[0] = mRamUsageBarPaddings[2] = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 4, res.getDisplayMetrics());
                    mRamUsageBarPaddings[1] = mRamUsageBarPaddings[3] = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 1, res.getDisplayMetrics());
                    mClearAllRecentsSizePx = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 50, res.getDisplayMetrics());
                    mRamUsageBarVerticalMargin = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 15, res.getDisplayMetrics());
                    mRamUsageBarHorizontalMargin = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 10, res.getDisplayMetrics());

                    FrameLayout vg = (FrameLayout) mRecentsActivity.getWindow().getDecorView()
                            .findViewById(android.R.id.content);
                    // create and inject RAM bar
                    mRamUsageBar = new LinearColorBar(vg.getContext(), null);
                    mRamUsageBar.setOrientation(LinearLayout.HORIZONTAL);
                    mRamUsageBar.setClipChildren(false);
                    mRamUsageBar.setClipToPadding(false);
                    mRamUsageBar.setPadding(mRamUsageBarPaddings[0], mRamUsageBarPaddings[1],
                            mRamUsageBarPaddings[2], mRamUsageBarPaddings[3]);
                    FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                    mRamUsageBar.setLayoutParams(flp);
                    LayoutInflater inflater = LayoutInflater.from(mGbContext);
                    inflater.inflate(R.layout.linear_color_bar, mRamUsageBar, true);
                    vg.addView(mRamUsageBar);
                    mForegroundProcessText = (TextView) mRamUsageBar.findViewById(R.id.foregroundText);
                    mBackgroundProcessText = (TextView) mRamUsageBar.findViewById(R.id.backgroundText);
                    mRamUsageBar.setVisibility(View.GONE);
                    updateRamBarLayout();
                    if (DEBUG) log("RAM bar injected");

                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_RECENTS_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_RECENTS_ALPHA);
                    intentFilter.addAction(ModHwKeys.ACTION_RECENTS_CLEAR_ALL_SINGLETAP);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_TASK_MASK_COLOR_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_RECENTS_CLEAR_ALL_BTN_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_TASK_CLEAR_BTN_OFFSET_CHANGED);
                    mRecentsActivity.registerReceiver(mBroadcastReceiver, intentFilter);
                    if (DEBUG) log("Recents panel view constructed");
                }
            });
            XposedHelpers.findAndHookMethod(systemBarScrimViewsClass, "isNavBarScrimRequired", boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject == mScrimViews) {
                        hasStackTasks = param.args[0] instanceof Boolean ? (Boolean) param.args[0] : false;
                        log("find tasks:" + hasStackTasks);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(recentActivityClass, "onDestroy", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    ((Activity) param.thisObject).unregisterReceiver(mBroadcastReceiver);
                    mStackActionButton.setVisibility(View.GONE);
                }
            });
            initRecentsTaskMask(prefs, classLoader);
            initClearTaskBtn(prefs, classLoader);
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if (param.thisObject != mRecentsActivity) return;

//                    Object config = XposedHelpers.getObjectField(param.thisObject, "mConfig");
//                    boolean hasTasks = !XposedHelpers.getBooleanField(config, "launchedWithNoRecentTasks");
//                    if (mRecentsClearButton != null) {
//                        boolean visible = mButtonGravity != 0 && mButtonGravity != 1 && hasStackTasks;
//                        mRecentsClearButton.setVisibility(visible ? View.VISIBLE : View.GONE);
//                    }
                    log("resume:" + mRamUsageBar);
                    if (mRamUsageBar != null) {
                        log("resume:" + mRamBarGravity);
                        if (mRamBarGravity != 0) {
                            mRamUsageBar.setVisibility(View.VISIBLE);
                            updateRamBarLayout();
                            updateRamBarMemoryUsage();
                        } else {
                            mRamUsageBar.setVisibility(View.GONE);
                        }
                    }
//                    if (mButtonGravity == GravityBoxSettings.RECENT_CLEAR_NAVIGATION_BAR && hasStackTasks) {
//                        setRecentsClearAll(true, (Context) param.thisObject);
//                    }
//                    if (mSearchBarState != SearchBarState.DEFAULT) {
//                        XposedHelpers.callMethod(mRecentsView, "setSearchBarVisibility", View.GONE);
//                        if (mSearchBarState == SearchBarState.HIDE_REMOVE_SPACE) {
//                            if (mSearchBarOriginalHeight == null) {
//                                mSearchBarOriginalHeight = XposedHelpers.getIntField(
//                                        config, "searchBarSpaceHeightPx");
//                            }
//                            XposedHelpers.setIntField(config, "searchBarSpaceHeightPx", 0);
//                            if (DEBUG) log("onResume: search bar height set to 0");
//                        } else if (mSearchBarOriginalHeight != null) {
//                            XposedHelpers.setIntField(config, "searchBarSpaceHeightPx",
//                                    mSearchBarOriginalHeight);
//                            if (DEBUG) log("onResume: restored original search bar height: " +
//                                    mSearchBarOriginalHeight);
//                        }
//                    } else
                    if (mSearchBarStatePrev != SearchBarState.DEFAULT && hasStackTasks) {
                        if ((Boolean) XposedHelpers.callMethod(mRecentsView, "hasValidSearchBar")) {
                            XposedHelpers.callMethod(mRecentsView, "setSearchBarVisibility", View.VISIBLE);
                        } else {
                            XposedHelpers.callMethod(param.thisObject, "refreshSearchWidgetView");
                        }
                        mSearchBarStatePrev = mSearchBarState;
                    }
                }
            });
            XposedHelpers.findAndHookMethod(recentActivityClass, "onStop", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    setRecentsClearAll(false, (Context) param.thisObject);
                }
            });

            // When to update RAM bar values
            XposedHelpers.findAndHookMethod(CLASS_SWIPE_HELPER, classLoader, "dismissChild",
                    View.class, float.class, boolean.class, updateRambarHook);

//            XposedHelpers.findAndHookMethod(CLASS_RECENT_VIEW, classLoader, "setSearchBarVisibility",
//                    int.class, new XC_MethodHook() {
//                        @Override
//                        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
//                            if (mSearchBarState != SearchBarState.DEFAULT) {
//                                if (DEBUG) log("setSearchBarVisibility: forcing View.GONE");
//                                param.args[0] = Integer.valueOf(View.GONE);
//                            }
//                        }
//                    });

//            XposedHelpers.findAndHookMethod(CLASS_RECENT_VIEW, classLoader, "startExitToHomeAnimation",
//                    CLASS_TASK_VIEW_EXIT_CONTEXT, new XC_MethodHook() {
//                        @Override
//                        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
//                            if (mRamUsageBar != null && mRamUsageBar.getVisibility() == View.VISIBLE) {
//                                performExitAnimation(mRamUsageBar);
//                            }
//                        }
//                    });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void initRecentsTaskMask(XSharedPreferences prefs, ClassLoader classLoader) {
        if (!prefs.getBoolean(GravityBoxSettings.PREF_RECENT_TASK_MASK_ENABLE, false)) {
            return;
        }
        XposedHelpers.findAndHookMethod(CLASS_TASK_THUMB_NAIL, classLoader, "setThumbnail", Bitmap.class, "android.app.ActivityManager$TaskThumbnailInfo", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Bitmap bitmap = (Bitmap) param.args[0];
                if (bitmap != null && recentTaskAlpha != 100) {
                    Bitmap b = GraphicUtils.getBackGroundBitmap(taskMaskColor, bitmap.getWidth(), bitmap.getHeight());
                    b = GraphicUtils.getAlplaBitmap(b, 100 - recentTaskAlpha);
                    param.args[0] = GraphicUtils.mergeBitmap(bitmap, b);
                }
            }
        });
    }

    private static void initClearTaskBtn(XSharedPreferences prefs, ClassLoader classLoader) {
        if (!prefs.getBoolean(GravityBoxSettings.PREF_KEY_CLEAR_TASK_ENABLE, false)) {
            return;
        }
        XposedHelpers.findAndHookMethod(CLASS_RECENTS_VIEW, classLoader, "getStackActionButtonBoundsFromStackLayout", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                mStackActionButton.setText(clearBtnText);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Rect r = (Rect) param.getResult();
                if (DEBUG) log(param.getResult().toString());
                int offset = DensityUtils.dip2px(mRecentsActivity, clearBtnOffset);
                switch (cleanBtnLocation) {
                    case 0: {
                        r.offsetTo(10, r.top + offset);
                        break;
                    }
                    case 1: {
                        r.offset(0, offset);
                        break;
                    }
                    case 2: {
                        r.offsetTo(10, mRecentsView.getMeasuredHeight() - mStackActionButton.getMeasuredHeight() - offset);
                        break;
                    }
                    case 3: {
                        r.offsetTo(r.left, mRecentsView.getMeasuredHeight() - mStackActionButton.getMeasuredHeight() - offset);
                        break;
                    }
                }
                mStackActionButton.layout(r.left, r.top, r.right, r.bottom);
            }
        });
        XposedHelpers.findAndHookMethod(CLASS_RECENTS_VIEW, classLoader, "hideStackActionButton", int.class, boolean.class, "com.android.systemui.recents.misc.ReferenceCountedTrigger", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                mStackActionButton.setVisibility(View.INVISIBLE);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mStackActionButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private static void performExitAnimation(final View view) {
        try {
            if (mExitAnimInterpolator == null) {
                Object config = XposedHelpers.getObjectField(mRecentsView, "mConfig");
                mExitAnimInterpolator = (Interpolator) XposedHelpers.getObjectField(
                        config, "fastOutSlowInInterpolator");
                mExitAnimDuration = XposedHelpers.getIntField(config, "taskViewRemoveAnimDuration");
            }
            view.animate()
                    .alpha(0f)
                    .setInterpolator(mExitAnimInterpolator)
                    .setDuration(mExitAnimDuration)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            view.setVisibility(View.GONE);
                            view.setAlpha(1f);
                        }
                    })
                    .start();
        } catch (Throwable t) {
            // don't need to be loud about it
        }
    }

    private static XC_MethodHook updateRambarHook = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
            updateRamBarMemoryUsage();
        }
    };

    private static void setRecentsClearAll(Boolean show, Context context) {
        ModNavigationBar.setRecentAlt(show);
        ModPieControls.setRecentAlt(show);
    }


    @SuppressLint("RtlHardcoded")
    private static void updateRamBarLayout() {
        if (mRamUsageBar == null || mRamBarGravity == 0) return;

        final Context context = mRamUsageBar.getContext();
        final Resources res = mRamUsageBar.getResources();
        final int orientation = res.getConfiguration().orientation;
        final boolean caOnTop = (mButtonGravity & Gravity.TOP) == Gravity.TOP;
        final boolean caOnLeft = (mButtonGravity & Gravity.LEFT) == Gravity.LEFT;
        final boolean rbOnTop = (mRamBarGravity == Gravity.TOP);
        final int marginTop = rbOnTop ? mMarginTopPx : 0;
        final int marginBottom = (!rbOnTop && (orientation == Configuration.ORIENTATION_PORTRAIT ||
                !Utils.isPhoneUI(context))) ? mMarginBottomPx : 0;
        final int marginLeft = orientation == Configuration.ORIENTATION_LANDSCAPE &&
                Utils.isPhoneUI(context) & mNavbarLeftHanded ? mMarginBottomPx : 0;
        final int marginRight = orientation == Configuration.ORIENTATION_LANDSCAPE &&
                Utils.isPhoneUI(context) & !mNavbarLeftHanded ? mMarginBottomPx : 0;

        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) mRamUsageBar.getLayoutParams();
        flp.gravity = mRamBarGravity;
        flp.setMargins(mRamUsageBarHorizontalMargin + marginLeft,
                rbOnTop ? (mRamUsageBarVerticalMargin + marginTop) : 0,
                mRamUsageBarHorizontalMargin + marginRight,
                rbOnTop ? 0 : (mRamUsageBarVerticalMargin + marginBottom)
        );
        mRamUsageBar.setLayoutParams(flp);
        if (DEBUG) log("RAM bar layout updated");
    }

    private static void updateRamBarMemoryUsage() {
        if (mRamUsageBar != null && mRamBarGravity != 0 && mHandler != null) {
            mHandler.post(updateRamBarTask);
        }
    }

    private static final Runnable updateRamBarTask = new Runnable() {
        @Override
        public void run() {
            if (mRamUsageBar == null || mRamUsageBar.getVisibility() == View.GONE) {
                return;
            }

            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            mAm.getMemoryInfo(memInfo);
            long secServerMem = 0;//XposedHelpers.getLongField(memInfo, "secondaryServerThreshold");
            mMemInfoReader.readMemInfo();
            long availMem = mMemInfoReader.getFreeSize() + mMemInfoReader.getCachedSize() -
                    secServerMem;
            long totalMem = mMemInfoReader.getTotalSize();

            String sizeStr = Formatter.formatShortFileSize(mGbContext, totalMem - availMem);
            mForegroundProcessText.setText(mGbContext.getResources().getString(
                    R.string.service_foreground_processes, sizeStr));
            sizeStr = Formatter.formatShortFileSize(mGbContext, availMem);
            mBackgroundProcessText.setText(mGbContext.getResources().getString(
                    R.string.service_background_processes, sizeStr));

            float fTotalMem = totalMem;
            float fAvailMem = availMem;
            mRamUsageBar.setRatios((fTotalMem - fAvailMem) / fTotalMem, 0, 0);
            if (DEBUG) log("RAM bar values updated");
        }
    };

    private static synchronized final void clearAll() {
        if (mRecentsView == null) return;

        try {
            int childCount = mRecentsView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = mRecentsView.getChildAt(i);
                if (child.getClass().getName().equals(CLASS_TASK_STACK_VIEW)) {
                    clearStack((ViewGroup) child);
                }
            }
            updateRamBarMemoryUsage();
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static final void clearStack(final ViewGroup stackView) {
        Object stack = XposedHelpers.getObjectField(stackView, "mStack");
        final ArrayList<?> tasks = (ArrayList<?>) XposedHelpers.callMethod(stack, "getTasks");
        final int count = tasks.size();
        for (int i = (count - 1); i >= 0; i--) {
            Object task = tasks.get(i);
            final Object taskView = XposedHelpers.callMethod(stackView,
                    "getChildViewForTask", task);
            if (taskView != null) {
                XposedHelpers.callMethod(taskView, "dismissTask");
            } else if (!mClearVisible) {
                XposedHelpers.callMethod(stack, "removeTask", task);
            }
        }
    }
}
