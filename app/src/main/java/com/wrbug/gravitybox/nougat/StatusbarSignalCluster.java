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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import com.wrbug.gravitybox.nougat.ModStatusBar.ContainerType;
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager;
import com.wrbug.gravitybox.nougat.managers.SysUiManagers;
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager.ColorInfo;
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager.IconManagerListener;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.telephony.TelephonyManager;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class StatusbarSignalCluster implements BroadcastSubReceiver, IconManagerListener {
    public static final String TAG = "GB:StatusbarSignalCluster";
    protected static final boolean DEBUG = false;

    protected static XSharedPreferences sPrefs;

    // HSPA+
    protected static int sQsHpResId;
    protected static int sSbHpResId;
    protected static int[][] DATA_HP;
    protected static int[] QS_DATA_HP;

    protected ContainerType mContainerType;
    protected LinearLayout mView;
    protected StatusBarIconManager mIconManager;
    protected Resources mResources;
    protected Resources mGbResources;
    protected Field mFldWifiGroup;
    private List<String> mErrorsLogged = new ArrayList<String>();
    protected boolean mNetworkTypeIndicatorsDisabled;

    // Data activity
    protected boolean mDataActivityEnabled;
    protected Object mNetworkControllerCallback;
    protected SignalActivity mWifiActivity;
    protected SignalActivity mMobileActivity;

    // Battery padding
    protected Integer mBatteryPaddingOriginal;
    protected int mBatteryStyle;
    protected boolean mPercentTextSb;

    protected static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    protected void logAndMute(String key, Throwable t) {
        if (!mErrorsLogged.contains(key)) {
            XposedBridge.log(t);
            mErrorsLogged.add(key);
        }
    }

    // Signal activity
    enum SignalType {
        WIFI, MOBILE
    }

    ;

    class SignalActivity {
        boolean enabled;
        boolean activityIn;
        boolean activityOut;
        Drawable imageDataIn;
        Drawable imageDataOut;
        Drawable imageDataInOut;
        ImageView activityView;
        SignalType signalType;

        public SignalActivity(ViewGroup container, SignalType type) {
            this(container, type, Gravity.BOTTOM | Gravity.CENTER);
        }

        public SignalActivity(ViewGroup container, SignalType type, int gravity) {
            signalType = type;
            if (mDataActivityEnabled) {
                activityView = new ImageView(container.getContext());
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                lp.gravity = gravity;
                activityView.setLayoutParams(lp);
                activityView.setTag("gbDataActivity");
                container.addView(activityView);
                if (type == SignalType.WIFI) {
                    imageDataIn = mGbResources.getDrawable(R.drawable.stat_sys_wifi_in);
                    imageDataOut = mGbResources.getDrawable(R.drawable.stat_sys_wifi_out);
                    imageDataInOut = mGbResources.getDrawable(R.drawable.stat_sys_wifi_inout);
                } else if (type == SignalType.MOBILE) {
                    imageDataIn = mGbResources.getDrawable(R.drawable.stat_sys_signal_in);
                    imageDataOut = mGbResources.getDrawable(R.drawable.stat_sys_signal_out);
                    imageDataInOut = mGbResources.getDrawable(R.drawable.stat_sys_signal_inout);
                }
                updateDataActivityColor();
            }
        }

        public void update() {
            try {
                update(enabled, activityIn, activityOut);
            } catch (Throwable t) {
                logAndMute("SignalActivity.update", t);
            }
        }

        public void update(boolean enabled, boolean in, boolean out) throws Throwable {
            this.enabled = enabled;
            activityIn = in;
            activityOut = out;

            // in/out activity
            if (mDataActivityEnabled) {
                if (activityIn && activityOut) {
                    activityView.setImageDrawable(imageDataInOut);
                } else if (activityIn) {
                    activityView.setImageDrawable(imageDataIn);
                } else if (activityOut) {
                    activityView.setImageDrawable(imageDataOut);
                } else {
                    activityView.setImageDrawable(null);
                }
                activityView.setVisibility(activityIn || activityOut ?
                        View.VISIBLE : View.GONE);
                if (DEBUG)
                    log("SignalActivity: " + signalType + ": data activity indicators updated");
            }
        }

        public void updateDataActivityColor() {
            if (mIconManager == null) return;

            if (imageDataIn != null) {
                imageDataIn = mIconManager.applyDataActivityColorFilter(imageDataIn);
            }
            if (imageDataOut != null) {
                imageDataOut = mIconManager.applyDataActivityColorFilter(imageDataInOut);
            }
            if (imageDataInOut != null) {
                imageDataInOut = mIconManager.applyDataActivityColorFilter(imageDataInOut);
            }
        }
    }

    public static void initResources(XSharedPreferences prefs, InitPackageResourcesParam resparam) {
        XModuleResources modRes = XModuleResources.createInstance(GravityBox.MODULE_PATH, resparam.res);

        if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_SIGNAL_CLUSTER_HPLUS, false) &&
                !Utils.isMtkDevice() && !Utils.isOxygenOs35Rom()) {

            sQsHpResId = XResources.getFakeResId(modRes, R.drawable.ic_qs_signal_hp);
            sSbHpResId = XResources.getFakeResId(modRes, R.drawable.stat_sys_data_fully_connected_hp);

            resparam.res.setReplacement(sQsHpResId, modRes.fwd(R.drawable.ic_qs_signal_hp));
            resparam.res.setReplacement(sSbHpResId, modRes.fwd(R.drawable.stat_sys_data_fully_connected_hp));

            DATA_HP = new int[][]{
                    {sSbHpResId, sSbHpResId, sSbHpResId, sSbHpResId},
                    {sSbHpResId, sSbHpResId, sSbHpResId, sSbHpResId}
            };
            QS_DATA_HP = new int[]{sQsHpResId, sQsHpResId};
            if (DEBUG) log("H+ icon resources initialized");
        }

        String lteStyle = prefs.getString(GravityBoxSettings.PREF_KEY_SIGNAL_CLUSTER_LTE_STYLE, "DEFAULT");
        if (!lteStyle.equals("DEFAULT")) {
            resparam.res.setReplacement(ModStatusBar.PACKAGE_NAME, "bool", "config_show4GForLTE",
                    lteStyle.equals("4G"));
        }
    }

    public static StatusbarSignalCluster create(ContainerType containerType,
                                                LinearLayout view, XSharedPreferences prefs) throws Throwable {
        sPrefs = prefs;
        return new StatusbarSignalCluster(containerType, view);
    }

    public StatusbarSignalCluster(ContainerType containerType, LinearLayout view) throws Throwable {
        mContainerType = containerType;
        mView = view;
        mIconManager = SysUiManagers.IconManager;
        mResources = mView.getResources();
        mGbResources = Utils.getGbContext(mView.getContext()).getResources();

        mFldWifiGroup = resolveField("mWifiGroup", "mWifiViewGroup");

        initPreferences();
        createHooks();

        if (mIconManager != null) {
            mIconManager.registerListener(this);
        }
    }

    private Field resolveField(String... fieldNames) {
        Field field = null;
        for (String fieldName : fieldNames) {
            try {
                field = XposedHelpers.findField(mView.getClass(), fieldName);
                if (DEBUG) log(fieldName + " field found");
                break;
            } catch (NoSuchFieldError nfe) {
                if (DEBUG) log(fieldName + " field NOT found");
            }
        }
        return field;
    }

    protected void createHooks() {
        if (!Utils.isXperiaDevice()) {
            try {
                XposedHelpers.findAndHookMethod(mView.getClass(), "inflatePhoneState", int.class, new XC_MethodHook() {
                    @SuppressWarnings("unchecked")
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (mView != param.thisObject) return;

                        if (mDataActivityEnabled && mMobileActivity == null) {
                            List<Object> phoneStates = (List<Object>) XposedHelpers.getObjectField(
                                    param.thisObject, "mPhoneStates");
                            ViewGroup mobileGroup = (ViewGroup) XposedHelpers.getObjectField(
                                    phoneStates.get(0), "mMobileGroup");
                            View nestedGroup = null;
                            int resId = mResources.getIdentifier("mobile_combo", "id",
                                    ModStatusBar.PACKAGE_NAME);
                            if (resId != 0) {
                                nestedGroup = mobileGroup.findViewById(resId);
                            }
                            mMobileActivity = new SignalActivity((nestedGroup instanceof ViewGroup) ?
                                    (ViewGroup) nestedGroup : mobileGroup, SignalType.MOBILE,
                                    Gravity.BOTTOM | Gravity.END);
                        }

                        if (mNetworkTypeIndicatorsDisabled) {
                            List<Object> phoneStates = (List<Object>) XposedHelpers.getObjectField(
                                    param.thisObject, "mPhoneStates");
                            int resId = mResources.getIdentifier("network_type", "id",
                                    ModStatusBar.PACKAGE_NAME);
                            for (Object state : phoneStates) {
                                ViewGroup mobileGroup = (ViewGroup) XposedHelpers.getObjectField(
                                        state, "mMobileGroup");
                                View networkType = resId != 0 ? mobileGroup.findViewById(resId) : null;
                                if (networkType != null) {
                                    mobileGroup.removeView(networkType);
                                }
                            }
                        }

                        update();
                    }
                });
            } catch (Throwable t) {
                if (!Utils.isOxygenOs35Rom()) {
                    log("Error hooking getOrInflateState: " + t.getMessage());
                }
            }

            if (sPrefs.getBoolean(GravityBoxSettings.PREF_KEY_SIGNAL_CLUSTER_NOSIM, false) &&
                    !Utils.isMotoXtDevice()) {
                try {
                    int noSimsResId = mResources.getIdentifier("no_sims", "id", ModStatusBar.PACKAGE_NAME);
                    if (noSimsResId != 0) {
                        View v = mView.findViewById(noSimsResId);
                        if (v != null) v.setVisibility(View.GONE);
                    }
                    XposedHelpers.setBooleanField(mView, "mNoSimsVisible", false);
                    if (Utils.isOxygenOs35Rom()) {
                        XposedHelpers.findAndHookMethod(mView.getClass(), "setNoSims",
                                boolean.class, new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                        View v = (View) XposedHelpers.getObjectField(mView, "mMobileSignalGroup");
                                        if (v != null) v.setVisibility(
                                                (boolean) param.args[0] ? View.GONE : View.VISIBLE);
                                    }
                                });
                    } else {
                        XposedHelpers.findAndHookMethod(mView.getClass(), "setNoSims",
                                boolean.class, new XC_MethodHook() {
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                        param.args[0] = false;
                                    }
                                });
                    }
                } catch (Throwable t) {
                    log("Error hooking setNoSims: " + t.getMessage());
                }
            }
        }

        if (mDataActivityEnabled) {
            try {
                XposedHelpers.findAndHookMethod(mView.getClass(), "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (mView != param.thisObject) return;

                        ViewGroup wifiGroup = (ViewGroup) mFldWifiGroup.get(param.thisObject);
                        if (wifiGroup != null) {
                            mWifiActivity = new SignalActivity(wifiGroup, SignalType.WIFI);
                            if (DEBUG) log("onAttachedToWindow: mWifiActivity created");
                        }
                    }
                });

                XposedHelpers.findAndHookMethod(mView.getClass(), "onDetachedFromWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (mView != param.thisObject) return;

                        mWifiActivity = null;
                        mMobileActivity = null;
                        if (DEBUG) log("onDetachedFromWindow: signal activities destoyed");
                    }
                });
            } catch (Throwable t) {
                log("Error hooking SignalActivity related methods: " + t.getMessage());
            }
        }

        if (sPrefs.getBoolean(GravityBoxSettings.PREF_KEY_SIGNAL_CLUSTER_HPLUS, false) &&
                !Utils.isFalconAsiaDs() && !Utils.isMtkDevice() && !Utils.isOxygenOs35Rom()) {
            try {
                final Class<?> mobileNetworkCtrlClass = Utils.isMotoXtDevice() ?
                        XposedHelpers.findClass(
                                "com.android.systemui.statusbar.policy.MotorolaMobileSignalController",
                                mView.getContext().getClassLoader()) :
                        XposedHelpers.findClass(
                                "com.android.systemui.statusbar.policy.MobileSignalController",
                                mView.getContext().getClassLoader());

                XposedHelpers.findAndHookMethod(mobileNetworkCtrlClass, "mapIconSets", new XC_MethodHook() {
                    @SuppressWarnings("unchecked")
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        SparseArray<Object> iconSet = (SparseArray<Object>) XposedHelpers.getObjectField(
                                param.thisObject, "mNetworkToIconLookup");
                        Object hGroup = iconSet.get(TelephonyManager.NETWORK_TYPE_HSPAP);
                        if (Utils.isMotoXtDevice()) {
                            Constructor<?> c = hGroup.getClass().getConstructor(
                                    String.class, int[][].class, int[][].class, int[].class,
                                    int.class, int.class, int.class, int.class,
                                    int.class, int.class, int.class, boolean.class, int.class,
                                    int[][].class, int[][].class, boolean.class, boolean.class,
                                    int[].class, int[].class, int[].class, int[].class,
                                    int[].class, int[].class, int[].class, int[].class,
                                    int[].class, int.class, int[].class, int[].class, int[].class,
                                    int[].class, int[].class);
                            c.setAccessible(true);
                            Object hPlusGroup = c.newInstance("HP",
                                    XposedHelpers.getObjectField(hGroup, "mSbIcons"),
                                    XposedHelpers.getObjectField(hGroup, "mQsIcons"),
                                    XposedHelpers.getObjectField(hGroup, "mContentDesc"),
                                    XposedHelpers.getIntField(hGroup, "mSbNullState"),
                                    XposedHelpers.getIntField(hGroup, "mQsNullState"),
                                    XposedHelpers.getIntField(hGroup, "mSbDiscState"),
                                    XposedHelpers.getIntField(hGroup, "mQsDiscState"),
                                    XposedHelpers.getIntField(hGroup, "mDiscContentDesc"),
                                    XposedHelpers.getIntField(hGroup, "mDataContentDescription"),
                                    sSbHpResId,
                                    XposedHelpers.getBooleanField(hGroup, "mIsWide"),
                                    sQsHpResId,
                                    XposedHelpers.getObjectField(hGroup, "mMotoSBActivityAOSPLookup"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoQSActivityAOSPLookup"),
                                    XposedHelpers.getBooleanField(hGroup, "mIsMotoUI"),
                                    XposedHelpers.getBooleanField(hGroup, "mIsMotoTwoCell"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoSimDescriptionLookup"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoSBSimLookup"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoQSSimLookup"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoSignalDescriptionLookup"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoSBSignalLookup"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoQSSignalLookup"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoRoamingDescriptionLookup"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoSBRoamingLookup"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoQSRoamingLookup"),
                                    XposedHelpers.getIntField(hGroup, "mMotoDataTypeDescription"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoSBDataTypeLookup"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoQSDataTypeLookup"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoActivityDescriptionLookup"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoSBActivityLookup"),
                                    XposedHelpers.getObjectField(hGroup, "mMotoQSActivityLookup"));
                            iconSet.put(TelephonyManager.NETWORK_TYPE_HSPAP, hPlusGroup);
                        } else {
                            Constructor<?> c = hGroup.getClass().getConstructor(
                                    String.class, int[][].class, int[][].class, int[].class,
                                    int.class, int.class, int.class, int.class,
                                    int.class, int.class, int.class, boolean.class, int.class);
                            c.setAccessible(true);
                            Object hPlusGroup = c.newInstance("HP",
                                    XposedHelpers.getObjectField(hGroup, "mSbIcons"),
                                    XposedHelpers.getObjectField(hGroup, "mQsIcons"),
                                    XposedHelpers.getObjectField(hGroup, "mContentDesc"),
                                    XposedHelpers.getIntField(hGroup, "mSbNullState"),
                                    XposedHelpers.getIntField(hGroup, "mQsNullState"),
                                    XposedHelpers.getIntField(hGroup, "mSbDiscState"),
                                    XposedHelpers.getIntField(hGroup, "mQsDiscState"),
                                    XposedHelpers.getIntField(hGroup, "mDiscContentDesc"),
                                    XposedHelpers.getIntField(hGroup, "mDataContentDescription"),
                                    sSbHpResId,
                                    XposedHelpers.getBooleanField(hGroup, "mIsWide"),
                                    sQsHpResId);
                            iconSet.put(TelephonyManager.NETWORK_TYPE_HSPAP, hPlusGroup);
                        }
                    }
                });
            } catch (Throwable t) {
                logAndMute("updateDataNetType", t);
            }
        }
    }

    public static void disableSignalExclamationMarks(ClassLoader cl) {
        if (Utils.isFalconAsiaDs() || Utils.isMtkDevice()) {
            return;
        }

        final String CLASS_WIFI_ICONS = Utils.isMotoXtDevice() ?
                "com.android.systemui.statusbar.policy.MotorolaWifiIcons" :
                "com.android.systemui.statusbar.policy.WifiIcons";
        final String CLASS_TELEPHONY_ICONS = Utils.isMotoXtDevice() ?
                "com.android.systemui.statusbar.policy.MotorolaTelephonyIcons" :
                "com.android.systemui.statusbar.policy.TelephonyIcons";
        Class<?> clsWifiIcons = null;
        Class<?> clsTelephonyIcons = null;
        final String[] wifiFields = new String[]{
                "WIFI_SIGNAL_STRENGTH", "WIFI_SIGNAL_STRENGTH_NARROW", "WIFI_SIGNAL_STRENGTH_WIDE"
        };
        final String[] mobileFields = new String[]{
                "TELEPHONY_SIGNAL_STRENGTH", "TELEPHONY_SIGNAL_STRENGTH_ROAMING",
                "DATA_SIGNAL_STRENGTH", "SB_TELEPHONY_SIGNAL_STRENGTH_4_BAR_NARROW",
                "SB_TELEPHONY_SIGNAL_STRENGTH_4_BAR_SEPARATED_NARROW", "SB_TELEPHONY_SIGNAL_STRENGTH_4_BAR_SEPARATED_WIDE",
                "SB_TELEPHONY_SIGNAL_STRENGTH_4_BAR_WIDE", "SB_TELEPHONY_SIGNAL_STRENGTH_5_BAR_NARROW",
                "SB_TELEPHONY_SIGNAL_STRENGTH_5_BAR_SEPARATED_NARROW", "SB_TELEPHONY_SIGNAL_STRENGTH_5_BAR_SEPARATED_WIDE",
                "SB_TELEPHONY_SIGNAL_STRENGTH_5_BAR_WIDE", "SB_TELEPHONY_SIGNAL_STRENGTH_6_BAR_NARROW",
                "SB_TELEPHONY_SIGNAL_STRENGTH_6_BAR_SEPARATED_NARROW", "SB_TELEPHONY_SIGNAL_STRENGTH_6_BAR_SEPARATED_WIDE",
                "SB_TELEPHONY_SIGNAL_STRENGTH_6_BAR_WIDE"
        };

        // Get classes
        try {
            clsWifiIcons = XposedHelpers.findClass(CLASS_WIFI_ICONS, cl);
        } catch (Throwable t) {
        }

        try {
            clsTelephonyIcons = XposedHelpers.findClass(CLASS_TELEPHONY_ICONS, cl);
        } catch (Throwable t) {
        }

        // WiFi
        for (String field : wifiFields) {
            try {
                int[][] wifiIcons = (int[][]) XposedHelpers.getStaticObjectField(clsWifiIcons, field);
                for (int i = 0; i < wifiIcons[1].length; i++) {
                    wifiIcons[0][i] = wifiIcons[1][i];
                }
            } catch (Throwable t) {
                //log("disableSignalExclamationMarks: field=" + field + ": " + t.getMessage()); 
            }
        }

        // Mobile
        for (String field : mobileFields) {
            try {
                int[][] telephonyIcons = (int[][]) XposedHelpers.getStaticObjectField(clsTelephonyIcons, field);
                for (int i = 0; i < telephonyIcons[1].length; i++) {
                    telephonyIcons[0][i] = telephonyIcons[1][i];
                }
            } catch (Throwable t) {
                //log("disableSignalExclamationMarks: field=" + field + ": " + t.getMessage());
            }
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_BATTERY_STYLE_CHANGED) &&
                intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_STYLE)) {
            mBatteryStyle = intent.getIntExtra(GravityBoxSettings.EXTRA_BATTERY_STYLE, 1);
            updateBatteryPadding();
        }
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_BATTERY_PERCENT_TEXT_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_STATUSBAR)) {
                mPercentTextSb = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_STATUSBAR, false) &&
                        "LEFT".equals(sPrefs.getString(GravityBoxSettings
                                .PREF_KEY_BATTERY_PERCENT_TEXT_POSITION, "RIGHT"));
                updateBatteryPadding();
            }
        }
    }

    protected void initPreferences() {
        mDataActivityEnabled = mContainerType != ContainerType.HEADER &&
                !Utils.isOxygenOs35Rom() &&
                sPrefs.getBoolean(GravityBoxSettings.PREF_KEY_SIGNAL_CLUSTER_DATA_ACTIVITY, false);

        mBatteryStyle = Integer.valueOf(sPrefs.getString(
                GravityBoxSettings.PREF_KEY_BATTERY_STYLE, "1"));

        mPercentTextSb = sPrefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_PERCENT_TEXT_STATUSBAR, false) &&
                "LEFT".equals(sPrefs.getString(GravityBoxSettings.PREF_KEY_BATTERY_PERCENT_TEXT_POSITION, "RIGHT"));

        mNetworkTypeIndicatorsDisabled = Utils.isMtkDevice() &&
                sPrefs.getBoolean(GravityBoxSettings.PREF_KEY_SIGNAL_CLUSTER_DNTI, false);

        updateBatteryPadding();
    }

    protected boolean supportsDataActivityIndicators() {
        return mDataActivityEnabled;
    }

    protected void setNetworkController(Object networkController) {
        final ClassLoader classLoader = mView.getClass().getClassLoader();
        final Class<?> networkCtrlCbClass = XposedHelpers.findClass("com.android.systemui.statusbar.policy.NetworkController.SignalCallback", classLoader);
        mNetworkControllerCallback = Proxy.newProxyInstance(classLoader,
                new Class<?>[]{networkCtrlCbClass}, new NetworkControllerCallback());
        XposedHelpers.callMethod(networkController, "addSignalCallback",
                mNetworkControllerCallback);
        if (DEBUG) log("setNetworkController: callback registered");
    }

    protected void update() {
        if (mView != null) {
            try {
                mobileGroupIdx = 0;
                updateIconColorRecursive(mView);
            } catch (Throwable t) {
                logAndMute("update", t);
            }
        }
    }

    protected int mobileGroupIdx;

    protected void updateIconColorRecursive(ViewGroup vg) {
        if (mIconManager == null) return;

        int count = vg.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = vg.getChildAt(i);
            if (child instanceof ViewGroup) {
                if (child.getId() != View.NO_ID) {
                    String resName = mResources.getResourceEntryName(child.getId());
                    if (resName.startsWith("mobile_combo")) {
                        mobileGroupIdx++;
                    }
                }
                updateIconColorRecursive((ViewGroup) child);
            } else if (child instanceof ImageView) {
                ImageView iv = (ImageView) child;
                if ("gbDataActivity".equals(iv.getTag())) {
                    continue;
                }
                if (mIconManager.isColoringEnabled() && mIconManager.getSignalIconMode() !=
                        StatusBarIconManager.SI_MODE_DISABLED) {
                    int color = mobileGroupIdx > 1 ?
                            mIconManager.getIconColor(1) : mIconManager.getIconColor(0);
                    iv.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                } else {
                    iv.clearColorFilter();
                }
            }
        }
    }

    protected void updateBatteryPadding() {
        if (Utils.isXperiaDevice()) return;

        try {
            if (mBatteryPaddingOriginal == null) {
                mBatteryPaddingOriginal = XposedHelpers.getIntField(mView, "mEndPadding");
            }
            int padding = mBatteryPaddingOriginal;
            if (mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_NONE) {
                if ((mContainerType == ContainerType.STATUSBAR && !mPercentTextSb) ||
                        (mContainerType == ContainerType.KEYGUARD)) {
                    padding = Math.round((float) mBatteryPaddingOriginal / 4f);
                }
            }
            XposedHelpers.setIntField(mView, "mEndPadding", padding);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if ((flags & (StatusBarIconManager.FLAG_ICON_COLOR_CHANGED |
                StatusBarIconManager.FLAG_DATA_ACTIVITY_COLOR_CHANGED |
                StatusBarIconManager.FLAG_ICON_COLOR_SECONDARY_CHANGED |
                StatusBarIconManager.FLAG_SIGNAL_ICON_MODE_CHANGED)) != 0) {
            if ((flags & StatusBarIconManager.FLAG_DATA_ACTIVITY_COLOR_CHANGED) != 0 &&
                    mDataActivityEnabled) {
                if (mWifiActivity != null) {
                    mWifiActivity.updateDataActivityColor();
                }
                if (mMobileActivity != null) {
                    mMobileActivity.updateDataActivityColor();
                }
            }
            update();
        }
    }

    protected class NetworkControllerCallback implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();

            try {
                if (methodName.equals("setWifiIndicators")) {
                    int enabledIdx = 0;
                    int inIdx = 3;
                    int outIdx = 4;
                    if (Utils.isMotoXtDevice() && args.length == 10) {
                        enabledIdx = 1;
                        inIdx = 5;
                        outIdx = 6;
                    }
                    if (DEBUG) {
                        log("WiFi enabled: " + args[enabledIdx]);
                        log("WiFi activity in: " + (Boolean) args[inIdx]);
                        log("WiFi activity out: " + (Boolean) args[outIdx]);
                    }
                    if (mWifiActivity != null) {
                        mWifiActivity.update((Boolean) args[enabledIdx],
                                (Boolean) args[inIdx], (Boolean) args[outIdx]);
                    }
                } else if (methodName.equals("setMobileDataIndicators")) {
                    //int enabledIdx = 0;
                    int inIdx = 4;
                    int outIdx = 5;
                    if (Utils.isMotoXtDevice() && args.length == 24) {
                        //enabledIdx = 1;
                        inIdx = 7;
                        outIdx = 8;
                    }
                    if (DEBUG) {
                        //log("Mobile data enabled: " + args[enabledIdx]);
                        log("Mobile data activity in: " + (Boolean) args[inIdx]);
                        log("Mobile data activity out: " + (Boolean) args[outIdx]);
                    }
                    if (mMobileActivity != null) {
                        mMobileActivity.update(true,
                                (Boolean) args[inIdx], (Boolean) args[outIdx]);
                    }
                }
            } catch (Throwable t) {
                logAndMute("NetworkControllerCallback", t);
            }

            return null;
        }
    }
}
