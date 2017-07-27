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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModTrustManager {
    private static final String TAG = "GB:ModTrustManager";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String CLASS_TRUST_MANAGER_SERVICE = "com.android.server.trust.TrustManagerService";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static Object mTrustManager;
    private static Set<String> mWifiTrusted;
    private static WifiManagerWrapper mWifiManager;
    private static ConnectivityManager mConnectivityManager;
    private static boolean mUpdateTrustAlreadyCalled;
    private static boolean mForceRefreshAgentList;
    private static boolean mWifiConnected;

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiPriorityActivity.ACTION_WIFI_TRUSTED_CHANGED)) {
                if (intent.hasExtra(WifiPriorityActivity.EXTRA_WIFI_TRUSTED)) {
                    String[] values = intent.getStringArrayExtra(WifiPriorityActivity.EXTRA_WIFI_TRUSTED);
                    if (mWifiTrusted.size() > 0 && values.length == 0) {
                        mForceRefreshAgentList = true;
                    }
                    mWifiTrusted = new HashSet<String>(Arrays.asList(values));
                    if (DEBUG) log("ACTION_WIFI_TRUSTED_CHANGED: mWifiTrusted=" + mWifiTrusted +
                            "; mForceRefreshAgentList=" + mForceRefreshAgentList);
                    updateTrustAll();
                }
            } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                if (intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, -1) ==
                        ConnectivityManager.TYPE_WIFI) {
                    onWifiConnectivityChanged();
                }
            }
        }
    };

    private static void onWifiConnectivityChanged() {
        try {
            NetworkInfo nwInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            boolean connectedChanged = mWifiConnected != nwInfo.isConnected();
            mWifiConnected = nwInfo.isConnected();
            if (DEBUG) log("onWifiConnectivityChanged: connected=" + mWifiConnected);
            if ((mWifiTrusted.size() > 0 || mForceRefreshAgentList) && connectedChanged) {
                updateTrustAll();
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void updateTrustAll() {
        try {
            XposedHelpers.callMethod(mTrustManager, "updateTrustAll");
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public static void initAndroid(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            mWifiTrusted = prefs.getStringSet(WifiPriorityActivity.PREF_KEY_WIFI_TRUSTED,
                    new HashSet<String>());
            if (DEBUG) log("initAndroid: mWifiTrusted=" + mWifiTrusted);

            XposedBridge.hookAllConstructors(XposedHelpers.findClass(
                    CLASS_TRUST_MANAGER_SERVICE, classLoader), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mTrustManager = param.thisObject;
                    Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    mWifiManager = new WifiManagerWrapper(context, null);
                    mConnectivityManager = (ConnectivityManager) context.getSystemService(
                            Context.CONNECTIVITY_SERVICE);

                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(WifiPriorityActivity.ACTION_WIFI_TRUSTED_CHANGED);
                    intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                    context.registerReceiver(mBroadcastReceiver, intentFilter);

                    if (DEBUG) log("Trust manager constructed");
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_TRUST_MANAGER_SERVICE, classLoader,
                    "refreshAgentList", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    mUpdateTrustAlreadyCalled = false;
                }
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if ((mWifiTrusted.size() > 0 || mForceRefreshAgentList) && 
                            !mUpdateTrustAlreadyCalled) {
                        if (DEBUG) log("refreshAgentList: updating trust agents");
                        updateTrustAll();
                    }
                    mForceRefreshAgentList = false;
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_TRUST_MANAGER_SERVICE, classLoader,
                    "updateTrustAll", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mUpdateTrustAlreadyCalled = true;
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_TRUST_MANAGER_SERVICE, classLoader,
                    "aggregateIsTrustManaged", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mWifiTrusted.size() == 0) return;

                    if (!isTrustAllowedForUser((int)param.args[0])) {
                        if (DEBUG) log("aggregateIsTrustManaged: user not yet authenticated");
                        return;
                    } else {
                        if (DEBUG) log("aggregateIsTrustManaged: yes, as wifi trusted list contains entries");
                        param.setResult(true);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_TRUST_MANAGER_SERVICE, classLoader,
                    "aggregateIsTrusted", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mWifiManager == null || mWifiTrusted.size() == 0 || !mWifiConnected) return;

                    if (!isTrustAllowedForUser((int)param.args[0])) {
                        if (DEBUG) log("aggregateIsTrusted: user not yet authenticated");
                        return;
                    }

                    String ssid = filterSSID(mWifiManager.getWifiSsid());
                    if (mWifiTrusted.contains(ssid)) {
                        if (DEBUG) log("aggregateIsTrusted: yes, as wifi trusted list contains: " + ssid);
                        param.setResult(true);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static boolean hasStrongAuthTracker() {
        try {
            XposedHelpers.findField(mTrustManager.getClass(), "mStrongAuthTracker");
            return true;
        } catch (NoSuchFieldError nfe) {
            return false;
        }
    }

    private static boolean isTrustAllowedForUser(int userId) {
        if (hasStrongAuthTracker()) {
            Object authTracker = XposedHelpers.getObjectField(
                    mTrustManager, "mStrongAuthTracker");
            return (boolean) XposedHelpers.callMethod(authTracker,
                    "isTrustAllowedForUser", userId);
        } else {
            return (boolean) XposedHelpers.callMethod(mTrustManager,
                    "getUserHasAuthenticated", userId);
        }
    }

    private static String filterSSID(String ssid) {
        // Filter only if has start and end double quotes
        if (ssid == null || !ssid.startsWith("\"") || !ssid.endsWith("\"")) {
            return ssid;
        }
        return ssid.substring(1, ssid.length()-1);
    }
}
