/*
 * Copyright (C) 2016 Peter Gregus for GravityBox Project (C3C076@xda)
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

import java.util.HashSet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XSharedPreferences;

public class ModFingerprint {
    private static final String TAG = "GB:ModFingerprint";
    private static final String CLASS_FINGERPRINT_SERVICE = "com.android.server.fingerprint.FingerprintService";
    private static final String CLASS_FINGERPRINT_UTILS = "com.android.server.fingerprint.FingerprintUtils";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static XSharedPreferences mPrefs;

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(GravityBoxSettings.ACTION_LOCKSCREEN_SETTINGS_CHANGED)) {
                mPrefs.reload();
                if (DEBUG) log("Settings reloaded");
            }
        }
    };

    public static void initAndroid(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            mPrefs = prefs;

            XposedBridge.hookAllConstructors(XposedHelpers.findClass(
                    CLASS_FINGERPRINT_SERVICE, classLoader), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    Context ctx = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(GravityBoxSettings.ACTION_LOCKSCREEN_SETTINGS_CHANGED);
                    ctx.registerReceiver(mBroadcastReceiver, intentFilter);
                    if (DEBUG) log("Fingerprint service created");
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_FINGERPRINT_UTILS, classLoader,
                    "vibrateFingerprintError", Context.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (DEBUG) log("vibrateFingerprintError");
                    if (mPrefs.getStringSet(GravityBoxSettings.PREF_KEY_IMPRINT_VIBE_DISABLE,
                            new HashSet<String>()).contains("ERROR")) {
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_FINGERPRINT_UTILS, classLoader,
                    "vibrateFingerprintSuccess", Context.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (DEBUG) log("vibrateFingerprintSuccess");
                    if (mPrefs.getStringSet(GravityBoxSettings.PREF_KEY_IMPRINT_VIBE_DISABLE,
                            new HashSet<String>()).contains("SUCCESS")) {
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
