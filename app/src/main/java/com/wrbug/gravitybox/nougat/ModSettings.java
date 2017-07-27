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

import android.content.res.XModuleResources;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;

public class ModSettings {
    private static final String TAG = "GB:ModSettings";
    public static final String PACKAGE_NAME = "com.android.settings";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static void log (String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void initPackageResources(final XSharedPreferences prefs, final InitPackageResourcesParam resparam) {
        try {
            XModuleResources modRes = XModuleResources.createInstance(GravityBox.MODULE_PATH, resparam.res);
            resparam.res.setReplacement(PACKAGE_NAME, "array", "window_animation_scale_entries",
                    modRes.fwd(R.array.window_animation_scale_entries));
            resparam.res.setReplacement(PACKAGE_NAME, "array", "window_animation_scale_values",
                    modRes.fwd(R.array.window_animation_scale_values));
            resparam.res.setReplacement(PACKAGE_NAME, "array", "transition_animation_scale_entries",
                    modRes.fwd(R.array.transition_animation_scale_entries));
            resparam.res.setReplacement(PACKAGE_NAME, "array", "transition_animation_scale_values",
                    modRes.fwd(R.array.transition_animation_scale_values));
            resparam.res.setReplacement(PACKAGE_NAME, "array", "animator_duration_scale_entries",
                    modRes.fwd(R.array.animator_duration_scale_entries));
            resparam.res.setReplacement(PACKAGE_NAME, "array", "animator_duration_scale_values",
                    modRes.fwd(R.array.animator_duration_scale_values));
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            // reserved for potential future use
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}