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

import com.wrbug.gravitybox.nougat.managers.FingerprintLauncher;
import com.wrbug.gravitybox.nougat.util.ArrayUtils;

import android.os.Build;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class GravityBox implements IXposedHookZygoteInit, IXposedHookInitPackageResources, IXposedHookLoadPackage {
    public static final String PACKAGE_NAME = GravityBox.class.getPackage().getName();
    public static String MODULE_PATH = null;
    private static XSharedPreferences prefs;
    private static final String[] LAUNCH_PACKAGE_NAME = {"android"};
    private static final int[] SUPPORT_SDK = {24, 25};

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
        prefs = new XSharedPreferences(PACKAGE_NAME);
        prefs.makeWorldReadable();

        if (!startupParam.startsSystemServer) return;

        XposedBridge.log("GB:Hardware: " + Build.HARDWARE);
        XposedBridge.log("GB:Product: " + Build.PRODUCT);
        XposedBridge.log("GB:Device manufacturer: " + Build.MANUFACTURER);
        XposedBridge.log("GB:Device brand: " + Build.BRAND);
        XposedBridge.log("GB:Device model: " + Build.MODEL);
        XposedBridge.log("GB:Device type: " + (Utils.isTablet() ? "tablet" : "phone"));
        XposedBridge.log("GB:Is MTK device: " + Utils.isMtkDevice());
        XposedBridge.log("GB:Is Xperia device: " + Utils.isXperiaDevice());
        XposedBridge.log("GB:Is Moto XT device: " + Utils.isMotoXtDevice());
        XposedBridge.log("GB:Is OxygenOS 4.5 ROM: " + Utils.isOxygenOs45Rom());
        XposedBridge.log("GB:Has Lenovo custom UI: " + Utils.hasLenovoCustomUI());
        if (Utils.hasLenovoCustomUI()) {
            XposedBridge.log("GB:Lenovo UI is VIBE: " + Utils.hasLenovoVibeUI());
            XposedBridge.log("GB:Lenovo ROM is ROW: " + Utils.isLenovoROW());
        }
        XposedBridge.log("GB:Has telephony support: " + Utils.hasTelephonySupport());
        XposedBridge.log("GB:Has Gemini support: " + Utils.hasGeminiSupport());
        XposedBridge.log("GB:Android SDK: " + Build.VERSION.SDK_INT);
        XposedBridge.log("GB:Android Release: " + Build.VERSION.RELEASE);
        XposedBridge.log("GB:ROM: " + Build.DISPLAY);

        if (!ArrayUtils.arrayHas(SUPPORT_SDK, Build.VERSION.SDK_INT)) {
            XposedBridge.log("!!! GravityBox you are running is not designed for "
                    + "Android SDK " + Build.VERSION.SDK_INT + " !!!");
            return;
        }

        SystemWideResources.initResources(prefs);

        // Common
        ModInputMethod.initZygote(prefs);
        PhoneWrapper.initZygote(prefs);
        ModTelephony.initZygote(prefs);

        // MTK (deprecated)
//        if (Utils.isMtkDevice()) {
//            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_MTK_FIX_DEV_OPTS, false)) {
//                MtkFixDevOptions.initZygote();
//            }
//        }
    }

    @Override
    public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
        if (!ArrayUtils.arrayHas(SUPPORT_SDK, Build.VERSION.SDK_INT)) {
            return;
        }

        if (resparam.packageName.equals(ModStatusBar.PACKAGE_NAME)) {
            ModStatusBar.initResources(prefs, resparam);
        }

        if (resparam.packageName.equals(ModSettings.PACKAGE_NAME)) {
            ModSettings.initPackageResources(prefs, resparam);
        }

        if (resparam.packageName.equals(ModLockscreen.PACKAGE_NAME)) {
            ModLockscreen.initResources(prefs, resparam);
        }

        if (resparam.packageName.equals(ModVolumePanel.PACKAGE_NAME)) {
            ModVolumePanel.initResources(prefs, resparam);
        }

//        if (resparam.packageName.equals(ModQsTiles.PACKAGE_NAME) &&
//                prefs.getBoolean(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_ENABLE, false)) {
//            ModQsTiles.initResources(resparam);
//        }
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!ArrayUtils.arrayHas(SUPPORT_SDK, Build.VERSION.SDK_INT)) {
            return;
        }
        XposedBridge.log("processName:" + lpparam.processName + "  packageName:" + lpparam.packageName);
        if (ArrayUtils.arrayHas(LAUNCH_PACKAGE_NAME, lpparam.packageName) &&
                lpparam.processName.equals("android")) {
            XposedBridge.log("GB:Is AOSP forced: " + Utils.isAospForced());
            ModVolumeKeySkipTrack.initAndroid(prefs, lpparam.classLoader);
            ModHwKeys.initAndroid(prefs, lpparam.classLoader);
            ModExpandedDesktop.initAndroid(prefs, lpparam.classLoader);
            ModAudio.initAndroid(prefs, lpparam.classLoader);
            PermissionGranter.initAndroid(lpparam.classLoader);
            ModLowBatteryWarning.initAndroid(prefs, lpparam.classLoader);
            ModDisplay.initAndroid(prefs, lpparam.classLoader);
            ConnectivityServiceWrapper.initAndroid(lpparam.classLoader);
            ModViewConfig.initAndroid(prefs, lpparam.classLoader);
            ModPower.initAndroid(prefs, lpparam.classLoader);
            ModLedControl.initAndroid(prefs, lpparam.classLoader);
            ModTrustManager.initAndroid(prefs, lpparam.classLoader);
            ModPowerMenu.initAndroid(prefs, lpparam.classLoader);
            ModFingerprint.initAndroid(prefs, lpparam.classLoader);
            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_FINGERPRINT_LAUNCHER_ENABLE, false)) {
                FingerprintLauncher.initAndroid(lpparam.classLoader);
            }
        }

        if (lpparam.packageName.equals(SystemPropertyProvider.PACKAGE_NAME)) {
            SystemPropertyProvider.init(prefs, lpparam.classLoader);
        }

        // MTK Specific (deprecated)
//        if (Utils.isMtkDevice()) {
//            if (lpparam.packageName.equals(MtkFixDevOptions.PACKAGE_NAME) &&
//                    prefs.getBoolean(GravityBoxSettings.PREF_KEY_MTK_FIX_DEV_OPTS, false)) {
//                MtkFixDevOptions.init(prefs, lpparam.classLoader);
//            }
//            if (lpparam.packageName.equals(MtkFixTtsSettings.PACKAGE_NAME) &&
//                    prefs.getBoolean(GravityBoxSettings.PREF_KEY_MTK_FIX_TTS_SETTINGS, false)) {
//                MtkFixTtsSettings.init(prefs, lpparam.classLoader);
//            }
//        }

        // Common
        if (lpparam.packageName.equals(ModLowBatteryWarning.PACKAGE_NAME)) {
            ModLowBatteryWarning.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModClearAllRecents.PACKAGE_NAME)) {
            ModClearAllRecents.init(prefs, lpparam.classLoader);
        }

        if (ModDialer.PACKAGE_NAMES.contains(lpparam.packageName)) {
            ModDialer25.init(prefs, lpparam.classLoader, lpparam.packageName);
        }

//        if (lpparam.packageName.equals(ModQsTiles.PACKAGE_NAME) &&
//                prefs.getBoolean(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_ENABLE, false)) {
//            ModQsTiles.init(prefs, lpparam.classLoader);
//        }

        if (lpparam.packageName.equals(ModStatusbarColor.PACKAGE_NAME)) {
            ModStatusbarColor.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModStatusBar.PACKAGE_NAME)) {
            ModStatusBar.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModSettings.PACKAGE_NAME)) {
            ModSettings.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModVolumePanel.PACKAGE_NAME)) {
            ModVolumePanel.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModPieControls.PACKAGE_NAME)) {
            ModPieControls.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModNavigationBar.PACKAGE_NAME)
                && prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_OVERRIDE, false)) {
            ModNavigationBar.init(prefs, lpparam.classLoader);
        }

        if (!Utils.hasLenovoVibeUI() &&
                lpparam.packageName.equals(ModLockscreen.PACKAGE_NAME)) {
            ModLockscreen.init(prefs, lpparam.classLoader);
        }

        if (ModLauncher.PACKAGE_NAMES.contains(lpparam.packageName)) {
            ModLauncher.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModSmartRadio.PACKAGE_NAME) &&
                prefs.getBoolean(GravityBoxSettings.PREF_KEY_SMART_RADIO_ENABLE, false)) {
            ModSmartRadio.init(prefs, lpparam.classLoader);
        }

//        if (lpparam.packageName.equals(ModDownloadProvider.PACKAGE_NAME)) {
//            ModDownloadProvider.init(prefs, lpparam.classLoader);
//        }

        if (lpparam.packageName.equals(ModRinger.PACKAGE_NAME)) {
            ModRinger.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModLedControl.PACKAGE_NAME_SYSTEMUI)) {
            ModLedControl.init(prefs, lpparam.classLoader);
            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_HEADS_UP_MASTER_SWITCH, false)) {
                ModLedControl.initHeadsUp(prefs, lpparam.classLoader);
            }
        }

        if (lpparam.packageName.equals(ModMms.PACKAGE_NAME)) {
            ModMms.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModTelecom.PACKAGE_NAME)) {
            ModTelecom.init(prefs, lpparam.classLoader);
        }
    }
}

