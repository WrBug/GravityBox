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

import com.wrbug.gravitybox.nougat.shortcuts.AShortcut;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ConnectivityServiceWrapper {
    private static final String TAG = "GB:ConnectivityServiceWrapper";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String CLASS_CONNECTIVITY_SERVICE = "com.android.server.ConnectivityService";

    public static final String ACTION_SET_MOBILE_DATA_ENABLED = 
            "gravitybox.intent.action.SET_MOBILE_DATA_ENABLED";
    public static final String ACTION_XPERIA_MOBILE_DATA_TOGGLE =
            "com.android.phone.intent.ACTION_DATA_TRAFFIC_SWITCH";
    public static final String ACTION_TOGGLE_MOBILE_DATA = 
            "gravitybox.intent.action.TOGGLE_MOBILE_DATA";
    public static final String ACTION_TOGGLE_WIFI = "gravitybox.intent.action.TOGGLE_WIFI";
    public static final String ACTION_TOGGLE_BLUETOOTH = "gravitybox.intent.action.TOGGLE_BLUETOOTH";
    public static final String ACTION_TOGGLE_WIFI_AP = "gravitybox.intent.action.TOGGLE_WIFI_AP";
    public static final String ACTION_SET_LOCATION_MODE = "gravitybox.intent.action.SET_LOCATION_MODE";
    public static final String ACTION_TOGGLE_NFC = "gravitybox.intent.action.TOGGLE_NFC";
    public static final String ACTION_GET_NFC_STATE = "gravitybox.intent.action.GET_NFC_STATE";
    public static final String EXTRA_LOCATION_MODE = "locationMode";
    public static final String ACTION_TOGGLE_AIRPLANE_MODE = "gravitybox.intent.action.TOGGLE_AIRPLANE_MODE";
    public static final String EXTRA_ENABLED = "enabled";

    public static final int NFC_STATE_OFF = 1;
    public static final int NFC_STATE_TURNING_ON = 2;
    public static final int NFC_STATE_ON = 3;
    public static final int NFC_STATE_TURNING_OFF = 4;
    public static final int NFC_STATE_UNKNOWN = -100;

    private static Context mContext;
    private static Object mConnectivityService;
    private static WifiManagerWrapper mWifiManager;
    private static TelephonyManager mTelephonyManager;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());

            if (intent.getAction().equals(ACTION_SET_MOBILE_DATA_ENABLED)) {
                final boolean enabled = intent.getBooleanExtra(EXTRA_ENABLED, false);
                setMobileDataEnabled(enabled);
            } else if (intent.getAction().equals(ACTION_TOGGLE_MOBILE_DATA)) {
                changeMobileDataState(intent);
            } else if (intent.getAction().equals(ACTION_TOGGLE_WIFI)) {
                changeWifiState(intent);
            } else if (intent.getAction().equals(ACTION_TOGGLE_BLUETOOTH)) {
                changeBluetoothState(intent);
            } else if (intent.getAction().equals(ACTION_TOGGLE_WIFI_AP)) {
                changeWiFiApState(intent);
            } else if (intent.getAction().equals(ACTION_SET_LOCATION_MODE)) {
                setLocationMode(intent);
            } else if (intent.getAction().equals(ACTION_TOGGLE_NFC)) {
                changeNfcState(intent);
            } else if (intent.getAction().equals(ACTION_GET_NFC_STATE)) {
                if (intent.hasExtra("receiver")) {
                    sendNfcState((ResultReceiver)intent.getParcelableExtra("receiver"));
                }
            } else if (intent.getAction().equals(ACTION_TOGGLE_AIRPLANE_MODE)) {
                changeAirplaneModeState(intent);
            }
        }
    };

    public static void initAndroid(final ClassLoader classLoader) {
        try {
            final Class<?> connServiceClass = 
                    XposedHelpers.findClass(CLASS_CONNECTIVITY_SERVICE, classLoader);

            XposedBridge.hookAllConstructors(connServiceClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if (DEBUG) log("ConnectivityService constructed.");
                    mConnectivityService = param.thisObject;

                    Context context = (Context) XposedHelpers.getObjectField(
                            param.thisObject, "mContext");
                    if (context == null && param.args.length != 0) {
                        context = (Context) param.args[0];
                    }
                    
                    if (context != null) {
                        mContext = context;
                        mWifiManager = new WifiManagerWrapper(context);
                        mTelephonyManager = (TelephonyManager) context.getSystemService(
                                Context.TELEPHONY_SERVICE);

                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(ACTION_SET_MOBILE_DATA_ENABLED);
                        intentFilter.addAction(ACTION_TOGGLE_MOBILE_DATA);
                        intentFilter.addAction(ACTION_TOGGLE_WIFI);
                        intentFilter.addAction(ACTION_TOGGLE_BLUETOOTH);
                        intentFilter.addAction(ACTION_TOGGLE_WIFI_AP);
                        intentFilter.addAction(ACTION_SET_LOCATION_MODE);
                        intentFilter.addAction(ACTION_TOGGLE_NFC);
                        intentFilter.addAction(ACTION_GET_NFC_STATE);
                        intentFilter.addAction(ACTION_TOGGLE_AIRPLANE_MODE);
                        context.registerReceiver(mBroadcastReceiver, intentFilter);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void setMobileDataEnabled(boolean enabled) {
        if (mTelephonyManager == null) return;
        try {
            XposedHelpers.callMethod(mTelephonyManager, "setDataEnabled", enabled);
            if (DEBUG) log("setDataEnabled called");
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void changeMobileDataState(Intent intent) {
        if (mTelephonyManager == null) return;
        try {
            boolean enabled;
            if (intent.hasExtra(AShortcut.EXTRA_ENABLE)) {
                enabled = intent.getBooleanExtra(AShortcut.EXTRA_ENABLE, false);
            } else {
                enabled = !(Boolean) XposedHelpers.callMethod(
                        mTelephonyManager, "getDataEnabled");
            }
            setMobileDataEnabled(enabled);
            if (intent.getBooleanExtra(AShortcut.EXTRA_SHOW_TOAST, false)) {
                Utils.postToast(mContext, enabled ? R.string.mobile_data_on :
                    R.string.mobile_data_off);
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void changeWifiState(Intent intent) {
        if (mWifiManager == null) return;
        try {
            if (intent.hasExtra(AShortcut.EXTRA_ENABLE)) {
                mWifiManager.setWifiEnabled(
                        intent.getBooleanExtra(AShortcut.EXTRA_ENABLE, false),
                        intent.getBooleanExtra(AShortcut.EXTRA_SHOW_TOAST, false));
            } else {
                mWifiManager.toggleWifiEnabled(
                        intent.getBooleanExtra(AShortcut.EXTRA_SHOW_TOAST, false));
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void changeWiFiApState(Intent intent) {
        if (mWifiManager == null) return;
        try {
            if (intent.hasExtra(AShortcut.EXTRA_ENABLE)) {
                mWifiManager.setWifiApEnabled(
                        intent.getBooleanExtra(AShortcut.EXTRA_ENABLE, false),
                        intent.getBooleanExtra(AShortcut.EXTRA_SHOW_TOAST, false));
            } else {
                mWifiManager.toggleWifiApEnabled(
                        intent.getBooleanExtra(AShortcut.EXTRA_SHOW_TOAST, false));
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void changeBluetoothState(Intent intent) {
        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            int labelResId;
            if (intent.hasExtra(AShortcut.EXTRA_ENABLE)) {
                if (intent.getBooleanExtra(AShortcut.EXTRA_ENABLE, false)) {
                    btAdapter.enable();
                    labelResId = R.string.bluetooth_on;
                } else {
                    btAdapter.disable();
                    labelResId = R.string.bluetooth_off;
                }
            } else {
                if (btAdapter.isEnabled()) {
                    labelResId = R.string.bluetooth_off;
                    btAdapter.disable();
                } else {
                    btAdapter.enable();
                    labelResId = R.string.bluetooth_on;
                }
            }
            if (intent.getBooleanExtra(AShortcut.EXTRA_SHOW_TOAST, false)) {
                Utils.postToast(mContext, labelResId);
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void setLocationMode(Intent intent) {
        if (mContext == null || !intent.hasExtra(EXTRA_LOCATION_MODE)) return;
        try {
            int mode = intent.getIntExtra(EXTRA_LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_BATTERY_SAVING);
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.LOCATION_MODE, mode);
            if (intent.getBooleanExtra(AShortcut.EXTRA_SHOW_TOAST, false)) {
                int msgResId =
                        mode == Settings.Secure.LOCATION_MODE_BATTERY_SAVING ?
                                R.string.location_mode_battery_saving :
                        mode == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY ?
                                R.string.location_mode_high_accuracy :
                        mode == Settings.Secure.LOCATION_MODE_SENSORS_ONLY ?
                                R.string.location_mode_device_only :
                        R.string.location_mode_off;
                Utils.postToast(mContext, msgResId);
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void sendNfcState(ResultReceiver receiver) {
        if (mContext == null || receiver == null) return;
        int nfcState = NFC_STATE_UNKNOWN;
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            if (adapter != null) {
                nfcState = (Integer) XposedHelpers.callMethod(adapter, "getAdapterState");
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        } finally {
            Bundle b = new Bundle();
            b.putInt("nfcState", nfcState);
            receiver.send(0, b);
        }
    }

    private static void changeNfcState(Intent intent) {
        if (mContext == null) return;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        if (adapter == null) return;

        try {
            boolean enable = false;
            if (intent.hasExtra(AShortcut.EXTRA_ENABLE)) {
                enable = intent.getBooleanExtra(AShortcut.EXTRA_ENABLE, false);
            } else {
                int nfcState = (Integer) XposedHelpers.callMethod(adapter, "getAdapterState");
                switch (nfcState) {
                    case NFC_STATE_TURNING_ON:
                    case NFC_STATE_ON:
                        enable = false;
                        break;
                    case NFC_STATE_TURNING_OFF:
                    case NFC_STATE_OFF:
                        enable = true;
                        break;
                }
            }
            XposedHelpers.callMethod(adapter, enable ? "enable" : "disable");
            if (intent.getBooleanExtra(AShortcut.EXTRA_SHOW_TOAST, false)) {
                Utils.postToast(mContext, enable ? R.string.nfc_on : R.string.nfc_off);
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void setAirplaneModeEnabled(boolean enabled, boolean showToast) {
        if (mConnectivityService == null) return;
        try {
            XposedHelpers.callMethod(mConnectivityService, "setAirplaneMode", enabled);
            if (showToast) {
                Utils.postToast(mContext, enabled ? R.string.airplane_mode_on :
                    R.string.airplane_mode_off);
            }
            if (DEBUG) log("setAirplaneModeEnabled called");
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void changeAirplaneModeState(Intent intent) {
        if (mContext == null) return;
        try {
            boolean enable;
            if (intent.hasExtra(AShortcut.EXTRA_ENABLE)) {
                enable = intent.getBooleanExtra(AShortcut.EXTRA_ENABLE, false);
            } else {
                ContentResolver cr = mContext.getContentResolver();
                enable = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 0;
            }
            setAirplaneModeEnabled(enable, intent.getBooleanExtra(AShortcut.EXTRA_SHOW_TOAST, false));
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
