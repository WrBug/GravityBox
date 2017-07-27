/*
 * Copyright (C) 2014 The CyanogenMod Project
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

import com.wrbug.gravitybox.nougat.managers.BatteryInfoManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.TelephonyManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModPower {
    private static final String TAG = "GB:ModPower";
    private static final String CLASS_PM_SERVICE = "com.android.server.power.PowerManagerService";
    private static final String CLASS_PM_HANDLER = "com.android.server.power.PowerManagerService$PowerManagerHandler";
    private static final String CLASS_PM_NOTIFIER = "com.android.server.power.Notifier";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final int MSG_WAKE_UP = 100;
    private static final int MSG_UNREGISTER_PROX_SENSOR_LISTENER = 101;
    public static final int MAX_PROXIMITY_WAIT = 500;
    private static final int MAX_PROXIMITY_TTL = MAX_PROXIMITY_WAIT * 2;

    private static Context mContext;
    private static Handler mHandler;
    private static SensorManager mSensorManager;
    private static Sensor mProxSensor;
    private static Object mLock;
    private static Runnable mWakeUpRunnable;
    private static boolean mProxSensorCovered;
    private static WakeLock mWakeLock;
    private static boolean mIgnoreIncomingCall;
    private static boolean mIsWirelessChargingSoundCustom;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_POWER_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_POWER_PROXIMITY_WAKE)) {
                    toggleWakeUpWithProximityFeature(intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_POWER_PROXIMITY_WAKE, false));
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_POWER_PROXIMITY_WAKE_IGNORE_CALL)) {
                    mIgnoreIncomingCall = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_POWER_PROXIMITY_WAKE_IGNORE_CALL, false);
                }
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_BATTERY_SOUND_CHANGED) &&
                    intent.getIntExtra(GravityBoxSettings.EXTRA_BATTERY_SOUND_TYPE, -1) == 
                        BatteryInfoManager.SOUND_WIRELESS) {
                updateIsWirelessChargingSoundCustom(intent.getStringExtra(
                        GravityBoxSettings.EXTRA_BATTERY_SOUND_URI));
            }
        }
    };

    public static void initAndroid(final XSharedPreferences prefs, final ClassLoader classLoader) {
        // wake up with proximity feature
        try {
            Class<?> pmServiceClass = XposedHelpers.findClass(CLASS_PM_SERVICE, classLoader);
            Class<?> pmHandlerClass = XposedHelpers.findClass(CLASS_PM_HANDLER, classLoader);

            mIgnoreIncomingCall = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_POWER_PROXIMITY_WAKE_IGNORE_CALL, false);

            XposedBridge.hookAllMethods(pmServiceClass, "systemReady", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    mHandler = (Handler) XposedHelpers.getObjectField(param.thisObject, "mHandler");
                    mLock = XposedHelpers.getObjectField(param.thisObject, "mLock");
                    toggleWakeUpWithProximityFeature(prefs.getBoolean(
                            GravityBoxSettings.PREF_KEY_POWER_PROXIMITY_WAKE, false));

                    IntentFilter intentFilter = new IntentFilter(GravityBoxSettings.ACTION_PREF_POWER_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_BATTERY_SOUND_CHANGED);
                    mContext.registerReceiver(mBroadcastReceiver, intentFilter);
                }
            });

            XposedHelpers.findAndHookMethod(pmServiceClass, "wakeUpInternal",
                    long.class, String.class, int.class, String.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (!shouldRunProximityCheck()) return;

                    synchronized (mLock) { 
                        if (mHandler.hasMessages(MSG_WAKE_UP)) {
                            if (DEBUG) log("wakeUpInternal: Wake up message already queued");
                            param.setResult(null);
                            return;
                        }
    
                        mWakeUpRunnable = new Runnable() {
                            @Override
                            public void run() {
                                final long ident = Binder.clearCallingIdentity();
                                try {
                                    if (DEBUG) log("Waking up...");
                                    XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                                } catch (Throwable t) {
                                } finally {
                                    Binder.restoreCallingIdentity(ident);
                                }
                            }
                        };
                        runWithProximityCheck();
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(pmHandlerClass, "handleMessage",
                    Message.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    Message msg = (Message) param.args[0];
                    if (msg.what == MSG_WAKE_UP) {
                        mWakeUpRunnable.run();
                        unregisterProxSensorListener();
                    } else if (msg.what == MSG_UNREGISTER_PROX_SENSOR_LISTENER) {
                        unregisterProxSensorListener();
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        // Wireless charging sound
        try {
            updateIsWirelessChargingSoundCustom(prefs.getString(
                    GravityBoxSettings.PREF_KEY_CHARGER_PLUGGED_SOUND_WIRELESS, null));

            XposedHelpers.findAndHookMethod(CLASS_PM_NOTIFIER, classLoader,
                    "playWirelessChargingStartedSound", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mIsWirelessChargingSoundCustom) {
                        Object suspendBlocker = XposedHelpers.getObjectField(
                                param.thisObject, "mSuspendBlocker");
                        XposedHelpers.callMethod(suspendBlocker, "release");
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void toggleWakeUpWithProximityFeature(boolean enabled) {
        try {
            if (enabled) {
                mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
                mProxSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
                mWakeLock = ((PowerManager) mContext.getSystemService(Context.POWER_SERVICE))
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            } else {
                unregisterProxSensorListener();
                mProxSensor = null;
                mSensorManager = null;
                mWakeLock = null;
            }
            if (DEBUG) log("toggleWakeUpWithProximityFeature: " + enabled);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static boolean shouldRunProximityCheck() {
        return (mSensorManager != null && mProxSensor != null &&
                !(mIgnoreIncomingCall && isIncomingCall()));
    }

    private static boolean isIncomingCall() {
        try {
            TelephonyManager phone = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            return (phone.getCallState() == TelephonyManager.CALL_STATE_RINGING);
        } catch (Throwable t) {
            XposedBridge.log(t);
            return false;
        }
    }

    private static void runWithProximityCheck() {
        if (mHandler.hasMessages(MSG_WAKE_UP)) {
            if (DEBUG) log("runWithProximityCheck: Wake up message already queued");
            return;
        } else if (mHandler.hasMessages(MSG_UNREGISTER_PROX_SENSOR_LISTENER)) {
            mHandler.removeMessages(MSG_UNREGISTER_PROX_SENSOR_LISTENER);
            mHandler.sendEmptyMessageDelayed(MSG_UNREGISTER_PROX_SENSOR_LISTENER, MAX_PROXIMITY_TTL);
            if (DEBUG) log("Proximity sensor listener still alive; mProxSensorCovered=" + mProxSensorCovered);
            if (!mProxSensorCovered) {
                mWakeUpRunnable.run();
            }
        } else {
            mHandler.sendEmptyMessageDelayed(MSG_WAKE_UP, MAX_PROXIMITY_WAIT);
            mSensorManager.registerListener(mProxSensorListener, mProxSensor, 
                    SensorManager.SENSOR_DELAY_FASTEST);
            mWakeLock.acquire();
            if (DEBUG) log("Proximity sensor listener resgistered");
        }
    }

    private static void unregisterProxSensorListener() {
        if (mSensorManager != null && mProxSensor != null) {
            mSensorManager.unregisterListener(mProxSensorListener, mProxSensor);
            if (DEBUG) log("Proximity sensor listener unregistered");
        }
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    private static SensorEventListener mProxSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            mProxSensorCovered = event.values[0] != mProxSensor.getMaximumRange();
            if (DEBUG) log("onSensorChanged:  mProxSensorCovered=" + mProxSensorCovered);
            if (!mHandler.hasMessages(MSG_UNREGISTER_PROX_SENSOR_LISTENER)) {
                if (DEBUG) log("Proximity sensor listener was not alive; scheduling unreg");
                mHandler.sendEmptyMessageDelayed(MSG_UNREGISTER_PROX_SENSOR_LISTENER, MAX_PROXIMITY_TTL);
                if (!mHandler.hasMessages(MSG_WAKE_UP)) {
                    if (DEBUG) log("Prox sensor status received too late. Wake up already triggered");
                    return;
                }
                mHandler.removeMessages(MSG_WAKE_UP);
                if (!mProxSensorCovered) {
                    mWakeUpRunnable.run();
                }
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private static void updateIsWirelessChargingSoundCustom(String value) {
        mIsWirelessChargingSoundCustom = (value != null &&
                !value.equals("content://settings/system/notification_sound"));
        if (DEBUG) log("mIsWirelessChargingSoundCustom: " + mIsWirelessChargingSoundCustom + 
                " [" + (value != null && value.isEmpty() ? "silent" : value) + "]");
    }
}
