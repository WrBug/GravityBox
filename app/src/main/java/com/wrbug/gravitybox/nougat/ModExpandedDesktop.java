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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.Handler;
import android.provider.Settings;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModExpandedDesktop {
    private static final String TAG = "GB:ModExpandedDesktop";
    private static final String CLASS_PHONE_WINDOW_MANAGER = "com.android.server.policy.PhoneWindowManager";
    private static final String CLASS_WINDOW_MANAGER_FUNCS = "android.view.WindowManagerPolicy.WindowManagerFuncs";
    private static final String CLASS_IWINDOW_MANAGER = "android.view.IWindowManager";
    private static final String CLASS_POLICY_WINDOW_STATE = "android.view.WindowManagerPolicy$WindowState";
    private static final String CLASS_SYSTEM_GESTURE = "com.android.server.policy.SystemGesturesPointerEventListener";
    private static final String CLASS_SCREEN_SHAPE_HELPER = "com.android.internal.util.ScreenShapeHelper";

    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final boolean DEBUG_LAYOUT = false;

    public static final String SETTING_EXPANDED_DESKTOP_STATE = "gravitybox_expanded_desktop_state";

    private static class ViewConst {
        static final int SYSTEM_UI_FLAG_IMMERSIVE = 0x00000800;
        static final int SYSTEM_UI_FLAG_IMMERSIVE_STICKY = 0x00001000;
        static final int NAVIGATION_BAR_TRANSLUCENT = 0x80000000;
        static final int NAVIGATION_BAR_TRANSIENT = 0x08000000;
        static final int STATUS_BAR_TRANSIENT = 0x04000000;
        static final int STATUS_BAR_TRANSLUCENT = 0x40000000;
        static final int SYSTEM_UI_FLAG_LOW_PROFILE = 0x00000001;
        static final int SYSTEM_UI_FLAG_HIDE_NAVIGATION = 0x00000002;
        static final int SYSTEM_UI_FLAG_FULLSCREEN = 0x00000004;
        static final int SYSTEM_UI_CLEARABLE_FLAGS =
                SYSTEM_UI_FLAG_LOW_PROFILE | SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | SYSTEM_UI_FLAG_FULLSCREEN;
        static final int SYSTEM_UI_TRANSPARENT = 0x00008000;
    }

    private static class WmLp {
        static final int FLAG_FULLSCREEN = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        static final int FLAG_LAYOUT_IN_SCREEN = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        static final int FLAG_LAYOUT_INSET_DECOR = WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        static final int FLAG_LAYOUT_IN_OVERSCAN = 0x02000000;
        static final int PRIVATE_FLAG_KEYGUARD = 0x00000400;
    }

    private static class NavbarDimensions {
        int wPort, hPort, hLand;

        NavbarDimensions(int wp, int hp, int hl) {
            wPort = wp;
            hPort = hp;
            hLand = hl;
        }
    }

    private static Context mContext;
    private static Object mPhoneWindowManager;
    private static SettingsObserver mSettingsObserver;
    private static boolean mExpandedDesktop;
    private static int mExpandedDesktopMode;
    private static boolean mNavbarOverride;
    private static float mNavbarHeightScaleFactor = 1;
    private static float mNavbarHeightLandscapeScaleFactor = 1;
    private static float mNavbarWidthScaleFactor = 1;
    private static boolean mClearedBecauseOfForceShow;
    private static Unhook mGetSystemUiVisibilityHook;
    private static List<String> mLoggedErrors = new ArrayList<String>();
    private static boolean mNavbarLeftHanded;
    private static int mAnimDockRightExit;
    private static int mAnimDockRightEnter;
    private static int mAnimDockLeftExit;
    private static int mAnimDockLeftEnter;
    private static Method mAreTranslucentBarsAllowed = null;
    private static Method mCanHideNavigationBar = null;
    private static Method mIsStatusBarKeyguard = null;
    private static Method mRequestTransientBars = null;
    private static Method mUpdateSystemBarsLw = null;
    private static Method mUpdateSystemUiVisibilityLw = null;
    private static Method mShouldUseOutsets = null;
    private static NavbarDimensions mNavbarDimensions;
    private static Class<?> mClsScreenShapeHelper;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static void logAndMute(String methodName, Throwable t) {
        if (!mLoggedErrors.contains(methodName)) {
            XposedBridge.log(t);
            mLoggedErrors.add(methodName);
        }
    }

    static class SettingsObserver extends ContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    SETTING_EXPANDED_DESKTOP_STATE), false, this);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_EXPANDED_DESKTOP_MODE_CHANGED)
                    && intent.hasExtra(GravityBoxSettings.EXTRA_ED_MODE)) {
                final int expandedDesktopMode = intent.getIntExtra(
                        GravityBoxSettings.EXTRA_ED_MODE, GravityBoxSettings.ED_DISABLED);
                mExpandedDesktopMode = expandedDesktopMode;
                updateSettings();
            } else if (intent.getAction().equals(ModStatusbarColor.ACTION_PHONE_STATUSBAR_VIEW_MADE)) {
                updateSettings();
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_NAVBAR_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_HEIGHT)) {
                    mNavbarHeightScaleFactor =
                            (float) intent.getIntExtra(GravityBoxSettings.EXTRA_NAVBAR_HEIGHT, 100) / 100f;
                    updateNavbarDimensions();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_HEIGHT_LANDSCAPE)) {
                    mNavbarHeightLandscapeScaleFactor = (float) intent.getIntExtra(
                            GravityBoxSettings.EXTRA_NAVBAR_HEIGHT_LANDSCAPE, 100) / 100f;
                    updateNavbarDimensions();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_WIDTH)) {
                    mNavbarWidthScaleFactor =
                            (float) intent.getIntExtra(GravityBoxSettings.EXTRA_NAVBAR_WIDTH, 100) / 100f;
                    updateNavbarDimensions();
                }
            }
        }
    };

    private static void updateSettings() {
        if (mContext == null || mPhoneWindowManager == null) return;

        try {
            final boolean expandedDesktop = Settings.Global.getInt(mContext.getContentResolver(),
                    SETTING_EXPANDED_DESKTOP_STATE, 0) == 1;
            if (mExpandedDesktopMode == GravityBoxSettings.ED_DISABLED && expandedDesktop) {
                Settings.Global.putInt(mContext.getContentResolver(),
                        SETTING_EXPANDED_DESKTOP_STATE, 0);
                return;
            }

            if (mExpandedDesktop != expandedDesktop) {
                mExpandedDesktop = expandedDesktop;
            }

            XposedHelpers.callMethod(mPhoneWindowManager, "updateSettings");

            int[] navigationBarWidthForRotation = (int[]) XposedHelpers.getObjectField(
                    mPhoneWindowManager, "mNavigationBarWidthForRotationDefault");
            int[] navigationBarHeightForRotation = (int[]) XposedHelpers.getObjectField(
                    mPhoneWindowManager, "mNavigationBarHeightForRotationDefault");
            final int portraitRotation = XposedHelpers.getIntField(mPhoneWindowManager, "mPortraitRotation");
            final int upsideDownRotation = XposedHelpers.getIntField(mPhoneWindowManager, "mUpsideDownRotation");
            final int landscapeRotation = XposedHelpers.getIntField(mPhoneWindowManager, "mLandscapeRotation");
            final int seascapeRotation = XposedHelpers.getIntField(mPhoneWindowManager, "mSeascapeRotation");

            if (isNavbarHidden()) {
                navigationBarWidthForRotation[portraitRotation]
                        = navigationBarWidthForRotation[upsideDownRotation]
                        = navigationBarWidthForRotation[landscapeRotation]
                        = navigationBarWidthForRotation[seascapeRotation]
                        = navigationBarHeightForRotation[portraitRotation]
                        = navigationBarHeightForRotation[upsideDownRotation]
                        = navigationBarHeightForRotation[landscapeRotation]
                        = navigationBarHeightForRotation[seascapeRotation] = 0;
            } else if (mNavbarDimensions != null) {
                navigationBarHeightForRotation[portraitRotation] =
                        navigationBarHeightForRotation[upsideDownRotation] =
                                mNavbarDimensions.hPort;
                navigationBarHeightForRotation[landscapeRotation] =
                        navigationBarHeightForRotation[seascapeRotation] =
                                mNavbarDimensions.hLand;

                navigationBarWidthForRotation[portraitRotation] =
                        navigationBarWidthForRotation[upsideDownRotation] =
                                navigationBarWidthForRotation[landscapeRotation] =
                                        navigationBarWidthForRotation[seascapeRotation] =
                                                mNavbarDimensions.wPort;
            }

            XposedHelpers.callMethod(mPhoneWindowManager, "updateRotation", false);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void updateNavbarDimensions() {
        try {
            Resources res = mContext.getResources();
            int resWidthId = res.getIdentifier(
                    "navigation_bar_width", "dimen", "android");
            int resHeightId = res.getIdentifier(
                    "navigation_bar_height", "dimen", "android");
            int resHeightLandscapeId = res.getIdentifier(
                    "navigation_bar_height_landscape", "dimen", "android");
            mNavbarDimensions = new NavbarDimensions(
                    (int) (res.getDimensionPixelSize(resWidthId) * mNavbarWidthScaleFactor),
                    (int) (res.getDimensionPixelSize(resHeightId) * mNavbarHeightScaleFactor),
                    (int) (res.getDimensionPixelSize(resHeightLandscapeId) * mNavbarHeightLandscapeScaleFactor));
            updateSettings();
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void initReflections(Class<?> classPhoneWindowManager) {
        try {
            mAreTranslucentBarsAllowed = classPhoneWindowManager.getDeclaredMethod(
                    "areTranslucentBarsAllowed");
            mAreTranslucentBarsAllowed.setAccessible(true);
        } catch (NoSuchMethodException e) {
            log("could not find areTranslucentBarsAllowed method");
        }

        try {
            mCanHideNavigationBar = classPhoneWindowManager.getDeclaredMethod(
                    "canHideNavigationBar");
            mCanHideNavigationBar.setAccessible(true);
        } catch (NoSuchMethodException e) {
            log("could not find canHideNavigationBar method");
        }

        try {
            mIsStatusBarKeyguard = classPhoneWindowManager.getDeclaredMethod(
                    "isStatusBarKeyguard");
            mIsStatusBarKeyguard.setAccessible(true);
        } catch (NoSuchMethodException e) {
            log("could not find isStatusBarKeyguard method");
        }

        try {
            mRequestTransientBars = classPhoneWindowManager.getDeclaredMethod("requestTransientBars",
                    XposedHelpers.findClass(CLASS_POLICY_WINDOW_STATE, null));
            mRequestTransientBars.setAccessible(true);
        } catch (NoSuchMethodException e) {
            log("could not find requestTransientBars method");
        }

        try {
            mUpdateSystemBarsLw = classPhoneWindowManager.getDeclaredMethod("updateSystemBarsLw",
                    XposedHelpers.findClass(CLASS_POLICY_WINDOW_STATE, null), int.class, int.class);
            mUpdateSystemBarsLw.setAccessible(true);
        } catch (NoSuchMethodException e) {
            log("could not find updateSystemBarsLw method");
        }

        try {
            mUpdateSystemUiVisibilityLw = classPhoneWindowManager.getDeclaredMethod(
                    "updateSystemUiVisibilityLw");
            mUpdateSystemUiVisibilityLw.setAccessible(true);
        } catch (NoSuchMethodException e) {
            log("could not find updateSystemUiVisibilityLw method");
        }

        try {
            mShouldUseOutsets = classPhoneWindowManager.getDeclaredMethod(
                    "shouldUseOutsets", WindowManager.LayoutParams.class, int.class);
            mShouldUseOutsets.setAccessible(true);
        } catch (NoSuchMethodException e) {
            log("could not find shouldUseOutsets method");
        }
    }

    public static void initAndroid(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> classPhoneWindowManager = XposedHelpers.findClass(CLASS_PHONE_WINDOW_MANAGER, classLoader);
            mClsScreenShapeHelper = XposedHelpers.findClass(CLASS_SCREEN_SHAPE_HELPER, classLoader);
            initReflections(classPhoneWindowManager);

            mNavbarOverride = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_OVERRIDE, false);
            if (mNavbarOverride) {
                mNavbarHeightScaleFactor =
                        (float) prefs.getInt(GravityBoxSettings.PREF_KEY_NAVBAR_HEIGHT, 100) / 100f;
                mNavbarHeightLandscapeScaleFactor =
                        (float) prefs.getInt(GravityBoxSettings.PREF_KEY_NAVBAR_HEIGHT_LANDSCAPE, 100) / 100f;
                mNavbarWidthScaleFactor =
                        (float) prefs.getInt(GravityBoxSettings.PREF_KEY_NAVBAR_WIDTH, 100) / 100f;
                mNavbarLeftHanded = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_ENABLE, false) &&
                        prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_LEFT_HANDED, false);
            }

            mExpandedDesktopMode = GravityBoxSettings.ED_DISABLED;
            try {
                mExpandedDesktopMode = Integer.valueOf(prefs.getString(
                        GravityBoxSettings.PREF_KEY_EXPANDED_DESKTOP, "0"));
            } catch (NumberFormatException nfe) {
                log("Invalid value for PREF_KEY_EXPANDED_DESKTOP preference");
            }

            XposedHelpers.findAndHookMethod(classPhoneWindowManager, "init",
                    Context.class, CLASS_IWINDOW_MANAGER, CLASS_WINDOW_MANAGER_FUNCS, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                                mPhoneWindowManager = param.thisObject;

                                IntentFilter intentFilter = new IntentFilter();
                                intentFilter.addAction(GravityBoxSettings.ACTION_PREF_EXPANDED_DESKTOP_MODE_CHANGED);
                                intentFilter.addAction(ModStatusbarColor.ACTION_PHONE_STATUSBAR_VIEW_MADE);
                                if (mNavbarOverride) {
                                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_NAVBAR_CHANGED);
                                }
                                mContext.registerReceiver(mBroadcastReceiver, intentFilter);

                                mAnimDockRightExit = mContext.getResources().getIdentifier(
                                        "dock_right_exit", "anim", "android");
                                mAnimDockRightEnter = mContext.getResources().getIdentifier(
                                        "dock_right_enter", "anim", "android");
                                mAnimDockLeftExit = mContext.getResources().getIdentifier(
                                        "dock_left_exit", "anim", "android");
                                mAnimDockLeftEnter = mContext.getResources().getIdentifier(
                                        "dock_left_enter", "anim", "android");

                                mSettingsObserver = new SettingsObserver(
                                        (Handler) XposedHelpers.getObjectField(param.thisObject, "mHandler"));
                                mSettingsObserver.observe();

                                if (mNavbarOverride) {
                                    updateNavbarDimensions();
                                }

                                if (DEBUG) log("Phone window manager initialized");
                            } catch (Throwable t) {
                                XposedBridge.log(t);
                            }
                        }
                    });

            if (!mNavbarOverride) {
                XposedBridge.hookAllMethods(classPhoneWindowManager, "setInitialDisplaySize", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int[] navigationBarWidthForRotation = (int[]) XposedHelpers.getObjectField(
                                param.thisObject, "mNavigationBarWidthForRotationDefault");
                        int[] navigationBarHeightForRotation = (int[]) XposedHelpers.getObjectField(
                                param.thisObject, "mNavigationBarHeightForRotationDefault");
                        int portraitRotation = XposedHelpers.getIntField(param.thisObject, "mPortraitRotation");
                        int landscapeRotation = XposedHelpers.getIntField(param.thisObject, "mLandscapeRotation");
                        if (navigationBarWidthForRotation != null &&
                                navigationBarHeightForRotation != null) {
                            mNavbarDimensions = new NavbarDimensions(
                                    navigationBarWidthForRotation[portraitRotation],
                                    navigationBarHeightForRotation[portraitRotation],
                                    navigationBarHeightForRotation[landscapeRotation]);
                        }
                    }
                });
            }

            XposedHelpers.findAndHookMethod(classPhoneWindowManager, "getInsetHintLw",
                    WindowManager.LayoutParams.class, Rect.class, int.class, int.class, int.class,
                    Rect.class, Rect.class, Rect.class, getInsetHintReplacement);

            XposedHelpers.findAndHookMethod(classPhoneWindowManager, "beginLayoutLw",
                    boolean.class, int.class, int.class, int.class, int.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            if (DEBUG_LAYOUT) log("beginLayoutLw");
                            try {
                                if (!isImmersiveModeActive() && !(mNavbarLeftHanded && !isNavbarHidden())) {
                                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                                }

                                boolean isDefaultDisplay = (Boolean) param.args[0];
                                int displayWidth = (Integer) param.args[1];
                                int displayHeight = (Integer) param.args[2];
                                int displayRotation = (Integer) param.args[3];
                                final int overscanLeft, overscanTop, overscanRight, overscanBottom;
                                int val;
                                if (isDefaultDisplay) {
                                    switch (displayRotation) {
                                        case Surface.ROTATION_90:
                                            overscanLeft = getInt("mOverscanTop");
                                            overscanTop = getInt("mOverscanRight");
                                            overscanRight = getInt("mOverscanBottom");
                                            overscanBottom = getInt("mOverscanLeft");
                                            break;
                                        case Surface.ROTATION_180:
                                            overscanLeft = getInt("mOverscanRight");
                                            overscanTop = getInt("mOverscanBottom");
                                            overscanRight = getInt("mOverscanLeft");
                                            overscanBottom = getInt("mOverscanTop");
                                            break;
                                        case Surface.ROTATION_270:
                                            overscanLeft = getInt("mOverscanBottom");
                                            overscanTop = getInt("mOverscanLeft");
                                            overscanRight = getInt("mOverscanTop");
                                            overscanBottom = getInt("mOverscanRight");
                                            break;
                                        default:
                                            overscanLeft = getInt("mOverscanLeft");
                                            overscanTop = getInt("mOverscanTop");
                                            overscanRight = getInt("mOverscanRight");
                                            overscanBottom = getInt("mOverscanBottom");
                                            break;
                                    }
                                } else {
                                    overscanLeft = 0;
                                    overscanTop = 0;
                                    overscanRight = 0;
                                    overscanBottom = 0;
                                }
                                setInt("mOverscanScreenLeft", 0);
                                setInt("mRestrictedOverscanScreenLeft", 0);
                                setInt("mOverscanScreenTop", 0);
                                setInt("mRestrictedOverscanScreenTop", 0);
                                setInt("mOverscanScreenWidth", displayWidth);
                                setInt("mRestrictedOverscanScreenWidth", displayWidth);
                                setInt("mOverscanScreenHeight", displayHeight);
                                setInt("mRestrictedOverscanScreenHeight", displayHeight);
                                setInt("mSystemLeft", 0);
                                setInt("mSystemTop", 0);
                                setInt("mSystemRight", displayWidth);
                                setInt("mSystemBottom", displayHeight);
                                setInt("mUnrestrictedScreenLeft", overscanLeft);
                                setInt("mUnrestrictedScreenTop", overscanTop);
                                setInt("mUnrestrictedScreenWidth", displayWidth - overscanLeft - overscanRight);
                                setInt("mUnrestrictedScreenHeight", displayHeight - overscanTop - overscanBottom);
                                setInt("mRestrictedScreenLeft", overscanLeft);
                                setInt("mRestrictedScreenTop", overscanTop);
                                setInt("mRestrictedScreenWidth", displayWidth - overscanLeft - overscanRight);
                                XposedHelpers.setIntField(getObj("mSystemGestures"), "screenWidth", displayWidth - overscanLeft - overscanRight);
                                setInt("mRestrictedScreenHeight", displayHeight - overscanTop - overscanBottom);
                                XposedHelpers.setIntField(getObj("mSystemGestures"), "screenHeight", displayHeight - overscanTop - overscanBottom);
                                setInt("mDockLeft", overscanLeft);
                                setInt("mContentLeft", overscanLeft);
                                setInt("mStableLeft", overscanLeft);
                                setInt("mStableFullscreenLeft", overscanLeft);
                                setInt("mCurLeft", overscanLeft);
                                setInt("mVoiceContentLeft", overscanLeft);
                                setInt("mDockTop", overscanTop);
                                setInt("mContentTop", overscanTop);
                                setInt("mStableTop", overscanTop);
                                setInt("mStableFullscreenTop", overscanTop);
                                setInt("mCurTop", overscanTop);
                                setInt("mVoiceContentTop", overscanTop);
                                val = displayWidth - overscanRight;
                                setInt("mDockRight", val);
                                setInt("mContentRight", val);
                                setInt("mStableRight", val);
                                setInt("mStableFullscreenRight", val);
                                setInt("mCurRight", val);
                                setInt("mVoiceContentRight", val);
                                val = displayHeight - overscanBottom;
                                setInt("mDockBottom", val);
                                setInt("mContentBottom", val);
                                setInt("mStableBottom", val);
                                setInt("mStableFullscreenBottom", val);
                                setInt("mCurBottom", val);
                                setInt("mVoiceContentBottom", val);
                                setInt("mDockLayer", 0x10000000);
                                setInt("mStatusBarLayer", -1);

                                // start with the current dock rect, which will be (0,0,displayWidth,displayHeight)
                                final Rect pf = getRect("mTmpParentFrame");
                                final Rect df = getRect("mTmpDisplayFrame");
                                final Rect of = getRect("mTmpOverscanFrame");
                                final Rect vf = getRect("mTmpVisibleFrame");
                                final Rect dcf = getRect("mTmpDecorFrame");
                                pf.left = df.left = of.left = vf.left = getInt("mDockLeft");
                                pf.top = df.top = of.top = vf.top = getInt("mDockTop");
                                pf.right = df.right = of.right = vf.right = getInt("mDockRight");
                                pf.bottom = df.bottom = of.bottom = vf.bottom = getInt("mDockBottom");
                                dcf.setEmpty();  // Decor frame N/A for system bars.

                                if (isDefaultDisplay) {
                                    // For purposes of putting out fake window up to steal focus, we will
                                    // drive nav being hidden only by whether it is requested.
                                    final int sysui = getInt("mLastSystemUiFlags");
                                    boolean navVisible = (sysui & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
                                    boolean navTranslucent = (sysui &
                                            (ViewConst.NAVIGATION_BAR_TRANSLUCENT | ViewConst.SYSTEM_UI_TRANSPARENT)) != 0;
                                    boolean immersive = (sysui & ViewConst.SYSTEM_UI_FLAG_IMMERSIVE) != 0;
                                    boolean immersiveSticky = (sysui & ViewConst.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0;
                                    boolean navAllowedHidden = immersive || immersiveSticky;
                                    navTranslucent &= !immersiveSticky ||
                                            mExpandedDesktopMode == GravityBoxSettings.ED_IMMERSIVE_STATUSBAR;  // transient trumps translucent
                                    if (!isKeyguardShowing()) {
                                        navTranslucent &= (Boolean) mAreTranslucentBarsAllowed.invoke(param.thisObject);
                                    }

                                    // When the navigation bar isn't visible, we put up a fake
                                    // input window to catch all touch events.  This way we can
                                    // detect when the user presses anywhere to bring back the nav
                                    // bar and ensure the application doesn't see the event.
                                    if (navVisible || navAllowedHidden) {
                                        if (getObj("mInputConsumer") != null) {
                                            XposedHelpers.callMethod(getObj("mInputConsumer"), "dismiss");
                                            setObj("mInputConsumer", null);
                                        }
                                    } else if (getObj("mInputConsumer") == null) {
                                        Object wmF = getObj("mWindowManagerFuncs");
                                        Handler h = (Handler) getObj("mHandler");
                                        setObj("mInputConsumer", XposedHelpers.callMethod(wmF, "addInputConsumer",
                                                h.getLooper(), getObj("mHideNavInputEventReceiverFactory")));
                                    }

                                    // For purposes of positioning and showing the nav bar, if we have
                                    // decided that it can't be hidden (because of the screen aspect ratio),
                                    // then take that into account.
                                    navVisible |= !(Boolean) mCanHideNavigationBar.invoke(param.thisObject);

                                    boolean updateSysUiVisibility = false;
                                    Object navBar = getObj("mNavigationBar");
                                    if (navBar != null) {
                                        Object navBarCtrl = getObj("mNavigationBarController");
                                        boolean transientNavBarShowing = (Boolean) XposedHelpers.callMethod(navBarCtrl, "isTransientShowing");
                                        // Force the navigation bar to its appropriate place and
                                        // size.  We need to do this directly, instead of relying on
                                        // it to bubble up from the nav bar, because this needs to
                                        // change atomically with screen rotations.
                                        setBool("mNavigationBarOnBottom", (!getBool("mNavigationBarCanMove") || displayWidth < displayHeight));
                                        if (getBool("mNavigationBarOnBottom")) {
                                            // It's a system nav bar or a portrait screen; nav bar goes on bottom.
                                            int top = displayHeight - overscanBottom
                                                    - getIntArray("mNavigationBarHeightForRotation")[displayRotation];
                                            getRect("mTmpNavigationFrame").set(0, top, displayWidth, displayHeight - overscanBottom);
                                            val = getRect("mTmpNavigationFrame").top;
                                            setInt("mStableBottom", val);
                                            if (!isNavbarImmersive()) {
                                                setInt("mStableFullscreenBottom", val);
                                            }
                                            if (transientNavBarShowing
                                                    || (navVisible && isNavbarImmersive())) {
                                                XposedHelpers.callMethod(navBarCtrl, "setBarShowingLw", true);
                                            } else if (navVisible) {
                                                XposedHelpers.callMethod(navBarCtrl, "setBarShowingLw", true);
                                                setInt("mDockBottom", val);
                                                setInt("mRestrictedScreenHeight", getInt("mDockBottom") - getInt("mRestrictedScreenTop"));
                                                setInt("mRestrictedOverscanScreenHeight", getInt("mDockBottom") - getInt("mRestrictedOverscanScreenTop"));
                                            } else {
                                                // We currently want to hide the navigation UI.
                                                XposedHelpers.callMethod(navBarCtrl, "setBarShowingLw", false);
                                            }
                                            if (navVisible && !navTranslucent && !(Boolean) XposedHelpers.callMethod(navBar, "isAnimatingLw")
                                                    && !(Boolean) XposedHelpers.callMethod(navBarCtrl, "wasRecentlyTranslucent")) {
                                                // If the opaque nav bar is currently requested to be visible,
                                                // and not in the process of animating on or off, then
                                                // we can tell the app that it is covered by it.
                                                setInt("mSystemBottom", val);
                                            }
                                        } else if (mNavbarLeftHanded && !isNavbarHidden()) {
                                            // Landscape screen; nav bar goes to the left.
                                            int right = overscanLeft +
                                                    getIntArray("mNavigationBarWidthForRotation")[displayRotation];
                                            getRect("mTmpNavigationFrame").set(0, 0, right, displayHeight);
                                            val = getRect("mTmpNavigationFrame").right;
                                            setInt("mStableLeft", val);
                                            if (!isNavbarImmersive()) {
                                                setInt("mStableFullscreenLeft", val);
                                            }
                                            if (transientNavBarShowing
                                                    || (navVisible && isNavbarImmersive())) {
                                                XposedHelpers.callMethod(navBarCtrl, "setBarShowingLw", true);
                                            } else if (navVisible) {
                                                XposedHelpers.callMethod(navBarCtrl, "setBarShowingLw", true);
                                                setInt("mDockLeft", val);
                                                setInt("mRestrictedScreenLeft", getInt("mDockLeft"));
                                                setInt("mRestrictedScreenWidth", getInt("mDockRight") -
                                                        getInt("mRestrictedScreenLeft"));
                                                setInt("mRestrictedOverscanScreenLeft", getInt("mRestrictedScreenLeft"));
                                                setInt("mRestrictedOverscanScreenWidth", getInt("mDockRight")
                                                        - getInt("mRestrictedOverscanScreenLeft"));
                                            } else {
                                                // We currently want to hide the navigation UI.
                                                XposedHelpers.callMethod(navBarCtrl, "setBarShowingLw", false);
                                            }

                                            if (navVisible && !navTranslucent && !navAllowedHidden &&
                                                    !(Boolean) XposedHelpers.callMethod(navBar, "isAnimatingLw")
                                                    && !(Boolean) XposedHelpers.callMethod(navBarCtrl, "wasRecentlyTranslucent")) {
                                                // If the nav bar is currently requested to be visible,
                                                // and not in the process of animating on or off, then
                                                // we can tell the app that it is covered by it.
                                                setInt("mSystemLeft", val);
                                            }
                                        } else {
                                            // Landscape screen; nav bar goes to the right.
                                            int left = displayWidth - overscanRight
                                                    - getIntArray("mNavigationBarWidthForRotation")[displayRotation];
                                            getRect("mTmpNavigationFrame").set(left, 0, displayWidth - overscanRight, displayHeight);
                                            val = getRect("mTmpNavigationFrame").left;
                                            setInt("mStableRight", val);
                                            if (!isNavbarImmersive()) {
                                                setInt("mStableFullscreenRight", val);
                                            }
                                            if (transientNavBarShowing
                                                    || (navVisible && isNavbarImmersive())) {
                                                XposedHelpers.callMethod(navBarCtrl, "setBarShowingLw", true);
                                            } else if (navVisible) {
                                                XposedHelpers.callMethod(navBarCtrl, "setBarShowingLw", true);
                                                setInt("mDockRight", val);
                                                setInt("mRestrictedScreenWidth", getInt("mDockRight") - getInt("mRestrictedScreenLeft"));
                                                setInt("mRestrictedOverscanScreenWidth", getInt("mDockRight") - getInt("mRestrictedOverscanScreenLeft"));
                                            } else {
                                                // We currently want to hide the navigation UI.
                                                XposedHelpers.callMethod(navBarCtrl, "setBarShowingLw", false);
                                            }
                                            if (navVisible && !navTranslucent && !(Boolean) XposedHelpers.callMethod(navBar, "isAnimatingLw")
                                                    && !(Boolean) XposedHelpers.callMethod(navBarCtrl, "wasRecentlyTranslucent")) {
                                                // If the nav bar is currently requested to be visible,
                                                // and not in the process of animating on or off, then
                                                // we can tell the app that it is covered by it.
                                                setInt("mSystemRight", val);
                                            }
                                        }
                                        // Make sure the content and current rectangles are updated to
                                        // account for the restrictions from the navigation bar.
                                        val = getInt("mDockTop");
                                        setInt("mContentTop", val);
                                        setInt("mCurTop", val);
                                        setInt("mVoiceContentTop", val);
                                        val = getInt("mDockBottom");
                                        setInt("mContentBottom", val);
                                        setInt("mCurBottom", val);
                                        setInt("mVoiceContentBottom", val);
                                        val = getInt("mDockLeft");
                                        setInt("mContentLeft", val);
                                        setInt("mCurLeft", val);
                                        setInt("mVoiceContentLeft", val);
                                        val = getInt("mDockRight");
                                        setInt("mContentRight", val);
                                        setInt("mCurRight", val);
                                        setInt("mVoiceContentRight", val);
                                        setInt("mStatusBarLayer", (Integer) XposedHelpers.callMethod(navBar, "getSurfaceLayer"));
                                        // And compute the final frame.
                                        Object nf = getObj("mTmpNavigationFrame");
                                        XposedHelpers.callMethod(navBar, "computeFrameLw", nf, nf, nf, nf, nf, dcf, nf, nf);
                                        if (DEBUG_LAYOUT) log("mNavigationBar frame: " + nf);
                                        if ((Boolean) XposedHelpers.callMethod(navBarCtrl, "checkHiddenLw")) {
                                            updateSysUiVisibility = true;
                                        }
                                    }
                                    if (DEBUG_LAYOUT)
                                        log(String.format("mDock rect: (%d,%d - %d,%d)",
                                                getInt("mDockLeft"), getInt("mDockTop"), getInt("mDockRight"), getInt("mDockBottom")));

                                    // decide where the status bar goes ahead of time
                                    Object statusBar = getObj("mStatusBar");
                                    if (statusBar != null) {
                                        // apply any navigation bar insets
                                        pf.left = df.left = of.left = getInt("mUnrestrictedScreenLeft");
                                        pf.top = df.top = of.top = getInt("mUnrestrictedScreenTop");
                                        pf.right = df.right = of.right = getInt("mUnrestrictedScreenWidth") + getInt("mUnrestrictedScreenLeft");
                                        pf.bottom = df.bottom = of.bottom = getInt("mUnrestrictedScreenHeight")
                                                + getInt("mUnrestrictedScreenTop");
                                        vf.left = getInt("mStableLeft");
                                        vf.top = getInt("mStableTop");
                                        vf.right = getInt("mStableRight");
                                        vf.bottom = getInt("mStableBottom");

                                        setInt("mStatusBarLayer", (Integer) XposedHelpers.callMethod(statusBar, "getSurfaceLayer"));

                                        // Let the status bar determine its size.
                                        XposedHelpers.callMethod(statusBar, "computeFrameLw", pf, df, vf, vf, vf, dcf, vf, vf);

                                        // For layout, the status bar is always at the top with our fixed height.
                                        setInt("mStableTop", getInt("mUnrestrictedScreenTop") + getInt("mStatusBarHeight"));

                                        boolean statusBarTransient = (sysui & ViewConst.STATUS_BAR_TRANSIENT) != 0;
                                        boolean statusBarTranslucent = (sysui &
                                                (ViewConst.STATUS_BAR_TRANSLUCENT | ViewConst.SYSTEM_UI_TRANSPARENT)) != 0;
                                        if (!isKeyguardShowing()) {
                                            statusBarTranslucent &= (Boolean) mAreTranslucentBarsAllowed.invoke(param.thisObject);
                                        }

                                        // If the status bar is hidden, we don't want to cause
                                        // windows behind it to scroll.
                                        if ((Boolean) XposedHelpers.callMethod(statusBar, "isVisibleLw") && !statusBarTransient
                                                && !isStatusbarImmersive()) {
                                            // Status bar may go away, so the screen area it occupies
                                            // is available to apps but just covering them when the
                                            // status bar is visible.
                                            setInt("mDockTop", getInt("mUnrestrictedScreenTop") + getInt("mStatusBarHeight"));

                                            val = getInt("mDockTop");
                                            setInt("mContentTop", val);
                                            setInt("mCurTop", val);
                                            setInt("mVoiceContentTop", val);
                                            val = getInt("mDockBottom");
                                            setInt("mContentBottom", val);
                                            setInt("mCurBottom", val);
                                            setInt("mVoiceContentBottom", val);
                                            val = getInt("mDockLeft");
                                            setInt("mContentLeft", val);
                                            setInt("mCurLeft", val);
                                            setInt("mVoiceContentLeft", val);
                                            val = getInt("mDockRight");
                                            setInt("mContentRight", val);
                                            setInt("mCurRight", val);
                                            setInt("mVoiceContentRight", val);

                                            if (DEBUG_LAYOUT) log("Status bar: " +
                                                    String.format(
                                                            "dock=[%d,%d][%d,%d] content=[%d,%d][%d,%d] cur=[%d,%d][%d,%d]",
                                                            getInt("mDockLeft"), getInt("mDockTop"), getInt("mDockRight"), getInt("mDockBottom"),
                                                            getInt("mContentLeft"), getInt("mContentTop"),
                                                            getInt("mContentRight"), getInt("mContentBottom"),
                                                            getInt("mCurLeft"), getInt("mCurTop"), getInt("mCurRight"), getInt("mCurBottom")));
                                        }
                                        Object sbCtrl = getObj("mStatusBarController");
                                        if ((Boolean) XposedHelpers.callMethod(statusBar, "isVisibleLw") &&
                                                !(Boolean) XposedHelpers.callMethod(statusBar, "isAnimatingLw")
                                                && !statusBarTransient && !statusBarTranslucent
                                                && !(Boolean) XposedHelpers.callMethod(sbCtrl, "wasRecentlyTranslucent")
                                                && !isStatusbarImmersive()) {
                                            // If the opaque status bar is currently requested to be visible,
                                            // and not in the process of animating on or off, then
                                            // we can tell the app that it is covered by it.
                                            setInt("mSystemTop", getInt("mUnrestrictedScreenTop") + getInt("mStatusBarHeight"));
                                        }
                                        if ((Boolean) XposedHelpers.callMethod(sbCtrl, "checkHiddenLw")) {
                                            updateSysUiVisibility = true;
                                        }
                                    }
                                    if (updateSysUiVisibility) {
                                        mUpdateSystemUiVisibilityLw.invoke(param.thisObject);
                                    }
                                }
                                return null;
                            } catch (Throwable t) {
                                logAndMute(param.method.getName(), t);
                                return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(classPhoneWindowManager, "applyStableConstraints",
                    int.class, int.class, Rect.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                            if (isImmersiveModeActive()) {
                                param.args[1] = updateWindowManagerVisibilityFlagsForExpandedDesktop((Integer) param.args[1]);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(classPhoneWindowManager, "layoutWindowLw",
                    CLASS_POLICY_WINDOW_STATE, CLASS_POLICY_WINDOW_STATE, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                            if (isImmersiveModeActive()) {
                                WindowManager.LayoutParams attrs = (WindowManager.LayoutParams)
                                        XposedHelpers.callMethod(param.args[0], "getAttrs");
                                if (attrs.type == WindowManager.LayoutParams.TYPE_INPUT_METHOD) {
                                    if (mNavbarLeftHanded) {
                                        param.setObjectExtra("gbDockLeft", Integer.valueOf(getInt("mDockLeft")));
                                        setInt("mDockLeft", getInt("mStableLeft"));
                                    } else {
                                        param.setObjectExtra("gbDockRight", Integer.valueOf(getInt("mDockRight")));
                                        setInt("mDockRight", getInt("mStableRight"));
                                    }
                                }
                                if (DEBUG_LAYOUT)
                                    log("layoutWindowLw: hooking WindowState.getSystemUiVisibility()");
                                mGetSystemUiVisibilityHook = XposedHelpers.findAndHookMethod(
                                        param.args[0].getClass(), "getSystemUiVisibility", new XC_MethodHook() {
                                            @Override
                                            protected void beforeHookedMethod(final MethodHookParam param2) throws Throwable {
                                                int vis = XposedHelpers.getIntField(param2.thisObject, "mSystemUiVisibility");
                                                param2.setResult(updateSystemUiVisibilityFlagsForExpandedDesktop(vis));
                                            }
                                        });
                            }
                        }

                        @Override
                        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                            if (mGetSystemUiVisibilityHook != null) {
                                if (DEBUG_LAYOUT)
                                    log("layoutWindowLw: unhooking WindowState.getSystemUiVisibility()");
                                mGetSystemUiVisibilityHook.unhook();
                                mGetSystemUiVisibilityHook = null;
                            }
                            if (param.getObjectExtra("gbDockRight") != null) {
                                setInt("mDockRight", (Integer) param.getObjectExtra("gbDockRight"));
                            }
                            if (param.getObjectExtra("gbDockLeft") != null) {
                                setInt("mDockLeft", (Integer) param.getObjectExtra("gbDockLeft"));
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(classPhoneWindowManager, "finishPostLayoutPolicyLw", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (isImmersiveModeActive()) {
                        if (DEBUG_LAYOUT)
                            log("finishPostLayoutPolicyLw: mangling forceStatusBar flags");
                        param.setObjectExtra("gbForceStatusbar", Boolean.valueOf(getBool("mForceStatusBar")));
                        param.setObjectExtra("gbForceStatusbarFromKeyguard", Boolean.valueOf(getBool("mForceStatusBarFromKeyguard")));
                        setBool("mForceStatusBar", false);
                        setBool("mForceStatusBarFromKeyguard", false);
                    }
                }

                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if (param.getObjectExtra("gbForceStatusbar") != null) {
                        if (DEBUG_LAYOUT)
                            log("finishPostLayoutPolicyLw: unmangling forceStatusBar flags");
                        setBool("mForceStatusBar", (Boolean) param.getObjectExtra("gbForceStatusbar"));
                        setBool("mForceStatusBarFromKeyguard", (Boolean) param.getObjectExtra("gbForceStatusbarFromKeyguard"));
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classPhoneWindowManager, "updateSystemUiVisibilityLw", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(final MethodHookParam param) throws Throwable {
                    if (DEBUG_LAYOUT) log("updateSystemUiVisibilityLw");
                    try {
                        if (!isImmersiveModeActive()) {
                            return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                        }

                        // If there is no window focused, there will be nobody to handle the events
                        // anyway, so just hang on in whatever state we're in until things settle down.
                        final Object win = getObj("mFocusedWindow") != null ?
                                getObj("mFocusedWindow") : getObj("mTopFullscreenOpaqueWindowState");
                        if (win == null) {
                            return 0;
                        }
                        Object winAttrs = XposedHelpers.callMethod(win, "getAttrs");
                        final int privateFlags = XposedHelpers.getIntField(winAttrs, "privateFlags");
                        final int windowType = XposedHelpers.getIntField(winAttrs, "type");
                        if ((privateFlags & WmLp.PRIVATE_FLAG_KEYGUARD) != 0 &&
                                getBool("mHideLockScreen") == true) {
                            // We are updating at a point where the keyguard has gotten
                            // focus, but we were last in a state where the top window is
                            // hiding it.  This is probably because the keyguard as been
                            // shown while the top window was displayed, so we want to ignore
                            // it here because this is just a very transient change and it
                            // will quickly lose focus once it correctly gets hidden.
                            return 0;
                        }

                        int tmpVisibility = (Integer) XposedHelpers.callMethod(win, "getSystemUiVisibility")
                                & ~getInt("mResettingSystemUiFlags")
                                & ~getInt("mForceClearedSystemUiFlags");
                        tmpVisibility = updateSystemUiVisibilityFlagsForExpandedDesktop(tmpVisibility);
                        final boolean subWindowInExpandedMode = isNavbarImmersive()
                                && (windowType >= WindowManager.LayoutParams.FIRST_SUB_WINDOW
                                && windowType <= WindowManager.LayoutParams.LAST_SUB_WINDOW);
                        final boolean wasCleared = mClearedBecauseOfForceShow;

                        if (getBool("mForcingShowNavBar") &&
                                ((Integer) XposedHelpers.callMethod(win, "getSurfaceLayer") < getInt("mForcingShowNavBarLayer")
                                        || subWindowInExpandedMode)) {
                            int clearableFlags = ViewConst.SYSTEM_UI_CLEARABLE_FLAGS;
                            if (isStatusbarImmersive()) {
                                clearableFlags &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                            }
                            if (isNavbarImmersive()) {
                                clearableFlags |= ViewConst.NAVIGATION_BAR_TRANSLUCENT;
                            }
                            tmpVisibility &= ~clearableFlags;
                            mClearedBecauseOfForceShow = true;
                        } else {
                            mClearedBecauseOfForceShow = false;
                        }
                        int visibility = (Integer) mUpdateSystemBarsLw.invoke(param.thisObject,
                                win, getInt("mLastSystemUiFlags"), tmpVisibility);
                        final int diff = visibility ^ getInt("mLastSystemUiFlags");
                        final boolean needsMenu = (Boolean) XposedHelpers.callMethod(win, "getNeedsMenuLw",
                                getObj("mTopFullscreenOpaqueWindowState"));
                        if (diff == 0 && getBool("mLastFocusNeedsMenu") == needsMenu
                                && getObj("mFocusedApp") == XposedHelpers.callMethod(win, "getAppToken")) {
                            return 0;
                        }
                        if (wasCleared && !mClearedBecauseOfForceShow
                                && (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0) {
                            Object navBarCtrl = getObj("mNavigationBarController");
                            XposedHelpers.callMethod(navBarCtrl, "showTransient");
                            visibility |= ViewConst.NAVIGATION_BAR_TRANSIENT;
                            Object wmFuncs = getObj("mWindowManagerFuncs");
                            int lastSbVis = XposedHelpers.getIntField(wmFuncs, "mLastStatusBarVisibility") |
                                    ViewConst.NAVIGATION_BAR_TRANSIENT;
                            XposedHelpers.setIntField(wmFuncs, "mLastStatusBarVisibility", lastSbVis);
                        }
                        final int visibility2 = visibility;
                        setInt("mLastSystemUiFlags", visibility);
                        setBool("mLastFocusNeedsMenu", needsMenu);
                        setObj("mFocusedApp", XposedHelpers.callMethod(win, "getAppToken"));
                        Handler h = (Handler) getObj("mHandler");
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Object statusbar = XposedHelpers.callMethod(param.thisObject, "getStatusBarService");
                                    if (statusbar != null) {
                                        XposedHelpers.callMethod(statusbar, "setSystemUiVisibility",
                                                visibility2, 0xffffffff, win.toString());
                                        XposedHelpers.callMethod(statusbar, "topAppWindowChanged", needsMenu);
                                    }
                                } catch (Throwable t) {
                                    // re-acquire status bar service next time it is needed.
                                    setObj("mStatusBarService", null);
                                }
                            }
                        });
                        return diff;
                    } catch (Throwable t) {
                        logAndMute(param.method.getName(), t);
                        return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classPhoneWindowManager, "requestTransientBars",
                    CLASS_POLICY_WINDOW_STATE, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args[0] == XposedHelpers.getObjectField(param.thisObject, "mNavigationBar")
                                    && isNavbarHidden()) {
                                if (DEBUG)
                                    log("requestTransientBars: ignoring since navbar is hidden");
                                param.setResult(null);
                            }
                        }
                    });

            if (mNavbarLeftHanded) {
                XposedHelpers.findAndHookMethod(classPhoneWindowManager, "selectAnimationLw",
                        CLASS_POLICY_WINDOW_STATE, int.class, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                int result = (Integer) param.getResult();
                                if (result == mAnimDockRightExit) {
                                    param.setResult(mAnimDockLeftExit);
                                } else if (result == mAnimDockRightEnter) {
                                    param.setResult(mAnimDockLeftEnter);
                                }
                            }
                        });

                XposedHelpers.findAndHookMethod(CLASS_SYSTEM_GESTURE, classLoader, "detectSwipe",
                        int.class, long.class, float.class, float.class, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                if ((Integer) param.getResult() == 0 && !isNavbarHidden()) {
                                    final float fromX = ((float[]) XposedHelpers.getObjectField(
                                            param.thisObject, "mDownX"))[(Integer) param.args[0]];
                                    final long elapsed = (Long) param.args[1] -
                                            ((long[]) XposedHelpers.getObjectField(
                                                    param.thisObject, "mDownTime"))[(Integer) param.args[0]];
                                    if (fromX <= XposedHelpers.getIntField(param.thisObject, "mSwipeStartThreshold") &&
                                            (Float) param.args[2] > fromX + XposedHelpers.getIntField(
                                                    param.thisObject, "mSwipeDistanceThreshold") &&
                                            elapsed < XposedHelpers.getStaticLongField(param.thisObject.getClass(),
                                                    "SWIPE_TIMEOUT_MS")) {
                                        Object navBar = getObj("mNavigationBar");
                                        if (navBar != null && !getBool("mNavigationBarOnBottom")) {
                                            mRequestTransientBars.invoke(mPhoneWindowManager, navBar);
                                        }
                                    }
                                }
                            }
                        });
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    // hooks
    private static XC_MethodReplacement getInsetHintReplacement = new XC_MethodReplacement() {
        @Override
        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
            if (DEBUG_LAYOUT) log("getContentInsetHintLw");
            try {
                if (!isImmersiveModeActive()) {
                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                }

                WindowManager.LayoutParams attrs = (WindowManager.LayoutParams) param.args[0];
                final int displayRotation = (int) param.args[1];
                Rect contentInset = (Rect) param.args[2];
                Rect stableInset = (Rect) param.args[3];
                Rect outOutsets = (Rect) param.args[4];

                final int fl = updateWindowManagerVisibilityFlagsForExpandedDesktop(attrs.flags);
                final int systemUiVisibility = updateSystemUiVisibilityFlagsForExpandedDesktop(attrs.systemUiVisibility |
                        XposedHelpers.getIntField(attrs, "subtreeSystemUiVisibility"));

                final boolean useOutsets = outOutsets != null &&
                        (boolean) mShouldUseOutsets.invoke(param.thisObject, attrs, fl);
                if (useOutsets) {
                    int outset = (int) XposedHelpers.callStaticMethod(mClsScreenShapeHelper,
                            "getWindowOutsetBottomPx", mContext.getResources());
                    if (outset > 0) {
                        if (displayRotation == Surface.ROTATION_0) {
                            outOutsets.bottom += outset;
                        } else if (displayRotation == Surface.ROTATION_90) {
                            outOutsets.right += outset;
                        } else if (displayRotation == Surface.ROTATION_180) {
                            outOutsets.top += outset;
                        } else if (displayRotation == Surface.ROTATION_270) {
                            outOutsets.left += outset;
                        }
                    }
                }

                if ((fl & (WmLp.FLAG_LAYOUT_IN_SCREEN | WmLp.FLAG_LAYOUT_INSET_DECOR))
                        == (WmLp.FLAG_LAYOUT_IN_SCREEN | WmLp.FLAG_LAYOUT_INSET_DECOR)) {
                    int availRight, availBottom;
                    if ((Boolean) mCanHideNavigationBar.invoke(param.thisObject) &&
                            (systemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0) {
                        availRight = getInt("mUnrestrictedScreenLeft") + getInt("mUnrestrictedScreenWidth");
                        availBottom = getInt("mUnrestrictedScreenTop") + getInt("mUnrestrictedScreenHeight");
                    } else {
                        availRight = getInt("mRestrictedScreenLeft") + getInt("mRestrictedScreenWidth");
                        availBottom = getInt("mRestrictedScreenTop") + getInt("mRestrictedScreenHeight");
                    }
                    if ((systemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0) {
                        if ((fl & WmLp.FLAG_FULLSCREEN) != 0) {
                            contentInset.set(getInt("mStableFullscreenLeft"), getInt("mStableFullscreenTop"),
                                    availRight - getInt("mStableFullscreenRight"),
                                    availBottom - getInt("mStableFullscreenBottom"));
                        } else {
                            contentInset.set(getInt("mStableLeft"), getInt("mStableTop"),
                                    availRight - getInt("mStableRight"), availBottom - getInt("mStableBottom"));
                        }
                    } else if ((fl & WmLp.FLAG_FULLSCREEN) != 0 || (fl & WmLp.FLAG_LAYOUT_IN_OVERSCAN) != 0) {
                        contentInset.setEmpty();
                    } else if ((systemUiVisibility & (View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)) == 0) {
                        contentInset.set(getInt("mCurLeft"), getInt("mCurTop"),
                                availRight - getInt("mCurRight"), availBottom - getInt("mCurBottom"));
                    } else {
                        contentInset.set(getInt("mCurLeft"), getInt("mCurTop"),
                                availRight - getInt("mCurRight"), availBottom - getInt("mCurBottom"));
                    }

                    if (stableInset != null) {
                        stableInset.set(getInt("mStableLeft"), getInt("mStableTop"),
                                availRight - getInt("mStableRight"),
                                availBottom - getInt("mStableBottom"));
                    }

                    return null;
                }
                contentInset.setEmpty();
                if (stableInset != null) {
                    stableInset.setEmpty();
                }
                return null;
            } catch (Throwable t) {
                logAndMute(param.method.getName(), t);
                return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
            }
        }
    };

    // helpers
    private static int getInt(String field) {
        return XposedHelpers.getIntField(mPhoneWindowManager, field);
    }

    private static void setInt(String field, int value) {
        XposedHelpers.setIntField(mPhoneWindowManager, field, value);
    }

    private static Object getObj(String field) {
        return XposedHelpers.getObjectField(mPhoneWindowManager, field);
    }

    private static void setObj(String field, Object value) {
        XposedHelpers.setObjectField(mPhoneWindowManager, field, value);
    }

    private static boolean getBool(String field) {
        return XposedHelpers.getBooleanField(mPhoneWindowManager, field);
    }

    private static void setBool(String field, boolean value) {
        XposedHelpers.setBooleanField(mPhoneWindowManager, field, value);
    }

    private static Rect getRect(String field) {
        return (Rect) getObj(field);
    }

    private static int[] getIntArray(String field) {
        return (int[]) getObj(field);
    }

    private static boolean isStatusbarImmersive() {
        return (mExpandedDesktop
                && (mExpandedDesktopMode == GravityBoxSettings.ED_SEMI_IMMERSIVE ||
                mExpandedDesktopMode == GravityBoxSettings.ED_IMMERSIVE_STATUSBAR ||
                mExpandedDesktopMode == GravityBoxSettings.ED_IMMERSIVE));
    }

    private static boolean isNavbarImmersive() {
        return (mExpandedDesktop
                && (mExpandedDesktopMode == GravityBoxSettings.ED_IMMERSIVE ||
                mExpandedDesktopMode == GravityBoxSettings.ED_IMMERSIVE_NAVBAR));
    }

    private static boolean isNavbarHidden() {
        return (mExpandedDesktop &&
                (mExpandedDesktopMode == GravityBoxSettings.ED_HIDE_NAVBAR ||
                        mExpandedDesktopMode == GravityBoxSettings.ED_SEMI_IMMERSIVE));
    }

    private static boolean isImmersiveModeActive() {
        return !isKeyguardShowing() && (isStatusbarImmersive() || isNavbarImmersive());
    }

    private static int updateSystemUiVisibilityFlagsForExpandedDesktop(int vis) {
        if (isNavbarImmersive()) {
            vis |= ViewConst.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }
        if (isStatusbarImmersive()) {
            vis |= ViewConst.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
        }
        return vis;
    }

    private static int updateWindowManagerVisibilityFlagsForExpandedDesktop(int vis) {
        if (mExpandedDesktopMode != GravityBoxSettings.ED_DISABLED) {
            vis |= WmLp.FLAG_FULLSCREEN;
        }
        return vis;
    }

    private static boolean isKeyguardShowing() {
        try {
            return (Boolean) mIsStatusBarKeyguard.invoke(mPhoneWindowManager) &&
                    !getBool("mHideLockScreen");
        } catch (Throwable t) {
            return false;
        }
    }
}
