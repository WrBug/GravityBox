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

import java.util.List;

import android.Manifest.permission;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class PermissionGranter {
    public static final String TAG = "GB:PermissionGranter";
    public static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String CLASS_PACKAGE_MANAGER_SERVICE = "com.android.server.pm.PackageManagerService";
    private static final String CLASS_PACKAGE_PARSER_PACKAGE = "android.content.pm.PackageParser.Package";
    private static final String PERM_ACCESS_SURFACE_FLINGER = "android.permission.ACCESS_SURFACE_FLINGER";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void initAndroid(final ClassLoader classLoader) {
        try {
            final Class<?> pmServiceClass = XposedHelpers.findClass(CLASS_PACKAGE_MANAGER_SERVICE, classLoader);

            XposedHelpers.findAndHookMethod(pmServiceClass, "grantPermissionsLPw",
                    CLASS_PACKAGE_PARSER_PACKAGE, boolean.class, String.class, new XC_MethodHook() {
                @SuppressWarnings("unchecked")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final String pkgName = (String) XposedHelpers.getObjectField(param.args[0], "packageName");
                    final Object extras = XposedHelpers.getObjectField(param.args[0], "mExtras");
                    final Object ps = XposedHelpers.callMethod(extras, "getPermissionsState");
                    final List<String> grantedPerms =
                            (List<String>) XposedHelpers.getObjectField(param.args[0], "requestedPermissions");
                    final Object settings = XposedHelpers.getObjectField(param.thisObject, "mSettings");
                    final Object permissions = XposedHelpers.getObjectField(settings, "mPermissions");

                    // GravityBox
                    if (GravityBox.PACKAGE_NAME.equals(pkgName)) {
                        // Add android.permission.ACCESS_SURFACE_FLINGER needed by screen recorder
                        if (!(boolean)XposedHelpers.callMethod(ps,"hasInstallPermission", PERM_ACCESS_SURFACE_FLINGER)) {
                            final Object pAccessSurfaceFlinger = XposedHelpers.callMethod(permissions, "get",
                                    PERM_ACCESS_SURFACE_FLINGER);
                            int ret = (int) XposedHelpers.callMethod(ps, "grantInstallPermission", pAccessSurfaceFlinger);
                            if (DEBUG) log("Permission added: " + pAccessSurfaceFlinger + "; ret=" + ret);
                        }

                        if (DEBUG) {
                            log("List of permissions: ");
                            for (Object perm : grantedPerms) {
                                log(pkgName + ": " + perm);
                            }
                        }
                    }

                    // SystemUI
                    if (ModStatusBar.PACKAGE_NAME.equals(pkgName)) {
                        // Add android.permission.READ_CALL_LOG needed by LockscreenAppBar to show badge for missed calls
                        if (!grantedPerms.contains(permission.READ_CALL_LOG)) {
                            final Object p = XposedHelpers.callMethod(permissions, "get",
                                    permission.READ_CALL_LOG);
                            XposedHelpers.callMethod(ps, "grantInstallPermission", p);
                        }
                        // Add ACCESS_FINE_LOCATION needed by GpsStatusMonitor
                        if (!grantedPerms.contains(permission.ACCESS_FINE_LOCATION)) {
                            final Object p = XposedHelpers.callMethod(permissions, "get",
                                    permission.ACCESS_FINE_LOCATION);
                            XposedHelpers.callMethod(ps, "grantInstallPermission", p);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
