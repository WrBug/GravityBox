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
package com.wrbug.gravitybox.nougat.managers;

import java.util.ArrayList;
import java.util.List;

import com.wrbug.gravitybox.nougat.BroadcastSubReceiver;
import com.wrbug.gravitybox.nougat.BuildConfig;
import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.ModPower;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;

public class KeyguardStateMonitor implements BroadcastSubReceiver {
    public static final String TAG = "GB:KeyguardStateMonitor";
    public static final String CLASS_KG_MONITOR =
            "com.android.systemui.statusbar.policy.KeyguardMonitor";
    public static final String CLASS_KG_UPDATE_MONITOR =
            "com.android.keyguard.KeyguardUpdateMonitor";
    public static final String CLASS_KG_VIEW_MEDIATOR =
            "com.android.systemui.keyguard.KeyguardViewMediator";
    private static boolean DEBUG = BuildConfig.DEBUG;

    private static enum ImprintMode {DEFAULT, WAKE_ONLY}

    ;

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    public interface Listener {
        void onKeyguardStateChanged();
    }

    private XSharedPreferences mPrefs;
    private Context mContext;
    private boolean mIsShowing;
    private boolean mIsSecured;
    private boolean mIsLocked;
    private boolean mIsTrustManaged;
    private boolean mIsKeyguardDisabled;
    private Object mMonitor;
    private Object mUpdateMonitor;
    private Object mMediator;
    private boolean mProxWakeupEnabled;
    private PowerManager mPm;
    private Handler mHandler;
    private boolean mFpAuthOnNextScreenOn;
    private ImprintMode mImprintMode = ImprintMode.DEFAULT;
    private List<Listener> mListeners = new ArrayList<>();

    protected KeyguardStateMonitor(Context context, XSharedPreferences prefs) {
        mContext = context;
        mPrefs = prefs;
        mPm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mHandler = new Handler();

        mProxWakeupEnabled = prefs.getBoolean(
                GravityBoxSettings.PREF_KEY_POWER_PROXIMITY_WAKE, false);
        mImprintMode = ImprintMode.valueOf(prefs.getString(
                GravityBoxSettings.PREF_KEY_LOCKSCREEN_IMPRINT_MODE, "DEFAULT"));

        createHooks();
    }

    public void setMediator(Object mediator) {
        mMediator = mediator;
    }

    private void createHooks() {
        try {
            ClassLoader cl = mContext.getClassLoader();
            Class<?> monitorClass = XposedHelpers.findClass(CLASS_KG_MONITOR, cl);

            XposedBridge.hookAllConstructors(monitorClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    XposedBridge.log("find mKeyguardUpdateMonitor");
                    mMonitor = param.thisObject;
                    mUpdateMonitor = XposedHelpers.getObjectField(mMonitor, "mKeyguardUpdateMonitor");
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_KG_MONITOR, cl,
                    "notifyKeyguardChanged", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                            boolean showing = XposedHelpers.getBooleanField(param.thisObject, "mShowing");
                            boolean secured = XposedHelpers.getBooleanField(param.thisObject, "mSecure");
                            boolean locked = !XposedHelpers.getBooleanField(param.thisObject, "mCanSkipBouncer");
                            boolean managed = getIsTrustManaged();
                            if (showing != mIsShowing || secured != mIsSecured ||
                                    locked != mIsLocked || managed != mIsTrustManaged) {
                                mIsShowing = showing;
                                mIsSecured = secured;
                                mIsLocked = locked;
                                mIsTrustManaged = managed;
                                notifyStateChanged();
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(CLASS_KG_VIEW_MEDIATOR, cl,
                    "setKeyguardEnabled", boolean.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                            if (mIsKeyguardDisabled && (boolean) param.args[0] &&
                                    !keyguardEnforcedByDevicePolicy()) {
                                param.setResult(null);
                            }
                        }
                    });

            XposedBridge.hookAllMethods(XposedHelpers.findClass(CLASS_KG_UPDATE_MONITOR, cl),
                    "handleFingerprintAuthenticated", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                            if ((mProxWakeupEnabled || mImprintMode == ImprintMode.WAKE_ONLY) &&
                                    !XposedHelpers.getBooleanField(param.thisObject, "mDeviceInteractive")) {
                                mFpAuthOnNextScreenOn = mProxWakeupEnabled && mImprintMode == ImprintMode.DEFAULT;
                                XposedHelpers.callMethod(mPm, "wakeUp", SystemClock.uptimeMillis());
                                if (mFpAuthOnNextScreenOn) {
                                    mHandler.postDelayed(mResetFpRunnable, ModPower.MAX_PROXIMITY_WAIT + 200);
                                } else {
                                    mResetFpRunnable.run();
                                }
                                param.setResult(null);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private boolean getIsTrustManaged() {
        if (mUpdateMonitor == null) {
            return false;
        }
        return (boolean) XposedHelpers.callMethod(mUpdateMonitor,
                "getUserTrustIsManaged", getCurrentUserId());
    }

    public boolean keyguardEnforcedByDevicePolicy() {
        DevicePolicyManager dpm = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null) {
            int passwordQuality = dpm.getPasswordQuality(null);
            switch (passwordQuality) {
                case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
                case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
                case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
                case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
                case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
                case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                    return true;
            }
        }
        return false;
    }

    private void notifyStateChanged() {
        if (DEBUG) log("showing:" + mIsShowing + "; secured:" + mIsSecured +
                "; locked:" + mIsLocked + "; trustManaged:" + mIsTrustManaged);
        synchronized (mListeners) {
            for (Listener l : mListeners) {
                l.onKeyguardStateChanged();
            }
        }
    }

    public void registerListener(Listener l) {
        if (l == null) return;
        synchronized (mListeners) {
            if (!mListeners.contains(l)) {
                mListeners.add(l);
            }
        }
    }

    public void unregisterListener(Listener l) {
        if (l == null) return;
        synchronized (mListeners) {
            if (mListeners.contains(l)) {
                mListeners.remove(l);
            }
        }
    }

    public int getCurrentUserId() {
        try {
            return XposedHelpers.getIntField(mMonitor, "mCurrentUser");
        } catch (Throwable t) {
            return 0;
        }
    }

    public boolean isShowing() {
        return mIsShowing;
    }

    public boolean isSecured() {
        return mIsSecured;
    }

    public boolean isLocked() {
        return (mIsSecured && mIsLocked);
    }

    public boolean isTrustManaged() {
        return mIsTrustManaged;
    }

    public void dismissKeyguard() {
        if (mMediator != null) {
            try {
                XposedHelpers.callMethod(mMediator, "dismiss");
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    }

    public void setKeyguardDisabled(boolean disabled) {
        try {
            mIsKeyguardDisabled = disabled;
            XposedHelpers.callMethod(mMediator, "setKeyguardEnabled", !disabled);
            if (mIsKeyguardDisabled) {
                XposedHelpers.setBooleanField(mMediator, "mNeedToReshowWhenReenabled", false);
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public boolean isKeyguardDisabled() {
        return mIsKeyguardDisabled;
    }

    private Runnable mResetFpRunnable = new Runnable() {
        @Override
        public void run() {
            mFpAuthOnNextScreenOn = false;

            try {
                XposedHelpers.setBooleanField(mUpdateMonitor, "mFingerprintAlreadyAuthenticated", false);
            } catch (Throwable t) { /* ignore */ }

            try {
                XposedHelpers.callMethod(mUpdateMonitor, "setFingerprintRunningState", 0);
            } catch (Throwable t) {
                try {
                    XposedHelpers.callMethod(mUpdateMonitor, "setFingerprintRunningDetectionRunning", false);
                } catch (Throwable t2) {
                    XposedBridge.log(t2);
                }
            }

            try {
                XposedHelpers.callMethod(mUpdateMonitor, "updateFingerprintListeningState");
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    };

    private void handleFingerprintAuthenticated() {
        try {
            XposedHelpers.callMethod(mUpdateMonitor, "handleFingerprintAuthenticated");
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(Intent.ACTION_SCREEN_ON)) {
            if (mFpAuthOnNextScreenOn) {
                mHandler.removeCallbacks(mResetFpRunnable);
                mFpAuthOnNextScreenOn = false;
                handleFingerprintAuthenticated();
            }
        } else if (action.equals(GravityBoxSettings.ACTION_PREF_POWER_CHANGED) &&
                intent.hasExtra(GravityBoxSettings.EXTRA_POWER_PROXIMITY_WAKE)) {
            mProxWakeupEnabled = intent.getBooleanExtra(
                    GravityBoxSettings.EXTRA_POWER_PROXIMITY_WAKE, false);
        } else if (action.equals(GravityBoxSettings.ACTION_LOCKSCREEN_SETTINGS_CHANGED)) {
            mPrefs.reload();
            mImprintMode = ImprintMode.valueOf(mPrefs.getString(
                    GravityBoxSettings.PREF_KEY_LOCKSCREEN_IMPRINT_MODE, "DEFAULT"));
        }
    }
}
