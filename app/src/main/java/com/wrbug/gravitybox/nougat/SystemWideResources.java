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

import android.content.res.Resources;
import android.content.res.XResources;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class SystemWideResources {
    private static final String TAG = "GB:SystemWideResources";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void initResources(final XSharedPreferences prefs) {
        try {
            Resources systemRes = XResources.getSystem();

            int translucentDecor = Integer.valueOf(prefs.getString(GravityBoxSettings.PREF_KEY_TRANSLUCENT_DECOR, "0"));
            if (translucentDecor != 0) {
                XResources.setSystemWideReplacement("android", "bool", "config_enableTranslucentDecor", translucentDecor == 1);
            }

            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_OVERRIDE, false)) {
                XResources.setSystemWideReplacement("android", "bool", "config_showNavigationBar",
                        prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_ENABLE,
                                SystemPropertyProvider.getSystemConfigBool(systemRes,
                                        "config_showNavigationBar")));
            }

            XResources.setSystemWideReplacement("android", "bool", "config_unplugTurnsOnScreen",
                    prefs.getBoolean(GravityBoxSettings.PREF_KEY_UNPLUG_TURNS_ON_SCREEN,
                            SystemPropertyProvider.getSystemConfigBool(systemRes,
                                    "config_unplugTurnsOnScreen")));

            if (!Utils.isVerneeApolloDevice()) {
                int pulseNotificationDelay = prefs.getInt(GravityBoxSettings.PREF_KEY_PULSE_NOTIFICATION_DELAY, -1);
                if (pulseNotificationDelay != -1) {
                    XResources.setSystemWideReplacement("android", "integer", "config_defaultNotificationLedOff",
                            (pulseNotificationDelay));;
                }
            }

            XResources.setSystemWideReplacement("android", "bool", "config_sip_wifi_only", false);

            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_BRIGHTNESS_MASTER_SWITCH, false)) {
                int brightnessMin = prefs.getInt(GravityBoxSettings.PREF_KEY_BRIGHTNESS_MIN, 20);
                XResources.setSystemWideReplacement(
                    "android", "integer", "config_screenBrightnessSettingMinimum", brightnessMin);
                if (DEBUG) log("Minimum brightness value set to: " + brightnessMin);

                int screenDim = prefs.getInt(GravityBoxSettings.PREF_KEY_SCREEN_DIM_LEVEL, 10);
                XResources.setSystemWideReplacement(
                        "android", "integer", "config_screenBrightnessDim", screenDim);
                if (DEBUG) log("Screen dim level set to: " + screenDim);
            }

            // Safe media volume
            Utils.TriState triState = Utils.TriState.valueOf(prefs.getString(
                    GravityBoxSettings.PREF_KEY_SAFE_MEDIA_VOLUME, "DEFAULT"));
            if (DEBUG) log(GravityBoxSettings.PREF_KEY_SAFE_MEDIA_VOLUME + ": " + triState);
            if (triState != Utils.TriState.DEFAULT) {
                XResources.setSystemWideReplacement("android", "bool", "config_safe_media_volume_enabled",
                        triState == Utils.TriState.ENABLED);
                if (DEBUG) log("config_safe_media_volume_enabled: " + (triState == Utils.TriState.ENABLED));
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

}
