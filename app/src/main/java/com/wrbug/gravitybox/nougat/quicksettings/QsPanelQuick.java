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
package com.wrbug.gravitybox.nougat.quicksettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.wrbug.gravitybox.nougat.BuildConfig;
import com.wrbug.gravitybox.nougat.GravityBox;
import com.wrbug.gravitybox.nougat.PhoneWrapper;
import com.wrbug.gravitybox.nougat.Utils;

import android.content.Intent;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class QsPanelQuick {
    private static final String TAG = "GB:QsPanelQuick";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String CLASS_QS_PANEL_QUICK = "com.android.systemui.qs.QuickQSPanel";
    private static final String CLASS_HEADER = "com.android.systemui.statusbar.phone.QuickStatusBarHeader";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public QsPanelQuick(XSharedPreferences prefs, ClassLoader classLoader) {
        createHooks(classLoader);
        if (DEBUG) log("QsPanelQuick wrapper created");
    }

    private void createHooks(final ClassLoader classLoader) {
        try {
            XC_MethodHook filterTilesHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Collection<?> tiles = (Collection<?>) param.args[0];
                    List<Object> toRemove = new ArrayList<>();
                    for (Object tile : tiles) {
                        Object state = XposedHelpers.getObjectField(tile, "mState");
                        if (!XposedHelpers.getBooleanField(state, "visible")) {
                            toRemove.add(tile);
                        }
                    }
                    for (Object tile : toRemove) {
                        tiles.remove(tile);
                    }
                }
            };
            XposedBridge.hookAllMethods(
                    XposedHelpers.findClass(CLASS_QS_PANEL_QUICK, classLoader),
                    "setTiles", filterTilesHook);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(CLASS_HEADER, classLoader,
                    "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    View editBtn = (View) XposedHelpers.getObjectField(param.thisObject, "mEditButton");
                    if (editBtn != null) {
                        editBtn.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent i = new Intent();
                                i.setClassName(GravityBox.PACKAGE_NAME, TileOrderActivity.class.getName());
                                i.putExtra(TileOrderActivity.EXTRA_HAS_MSIM_SUPPORT,
                                        PhoneWrapper.hasMsimSupport());
                                i.putExtra(TileOrderActivity.EXTRA_IS_OOS_35_ROM,
                                        Utils.isOxygenOs35Rom());
                                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                try {
                                    Object starter = XposedHelpers.getObjectField(
                                            param.thisObject, "mActivityStarter");
                                    XposedHelpers.callMethod(starter, "startActivity", i, true);
                                } catch (Throwable t) {
                                    XposedBridge.log(t);
                                }
                            }
                        });
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
