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

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.wrbug.gravitybox.nougat.ledcontrol.QuietHours;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModDialer24 {
    private static final String TAG = "GB:ModDialer24";

    private static final String CLASS_DIALTACTS_ACTIVITY = "com.android.dialer.app.DialtactsActivity";
    private static final String CLASS_DIALTACTS_ACTIVITY_GOOGLE = 
            "com.google.android.apps.dialer.extensions.GoogleDialtactsActivity";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static QuietHours mQuietHours;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    static class ClassInfo {
        Class<?> clazz;
        Map<String,String> methods;
        Object extra;
        ClassInfo(Class<?> cls) {
            clazz = cls;
            methods = new HashMap<>();
        }
    }

    private static ClassInfo resolveCallCardFragment(ClassLoader cl) {
        ClassInfo info = null;
        String[] CLASS_NAMES = new String[] { "com.android.incallui.CallCardFragment", "ayv" };
        String[] METHOD_NAMES = new String[] { "setDrawableToImageView" };
        for (String className : CLASS_NAMES) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz == null || !Fragment.class.isAssignableFrom(clazz))
                continue;
            info = new ClassInfo(clazz);
            for (String methodName : METHOD_NAMES) {
                if (methodName.equals("setDrawableToImageView")) {
                    for (String realMethodName : new String[] { methodName, "b" }) {
                        Method m = XposedHelpers.findMethodExactIfExists(clazz, realMethodName,
                            Drawable.class);
                        if (m != null) {
                            info.methods.put(methodName, realMethodName);
                            break;
                        }
                    }
                }
            }
        }
        return info;
    }

    private static ClassInfo resolveAnswerFragment(ClassLoader cl) {
        ClassInfo info = null;
        String[] CLASS_NAMES = new String[] { "com.android.incallui.AnswerFragment", "bbw", "bbx" };
        String[] METHOD_NAMES = new String[] { "onShowAnswerUi" };
        for (String className : CLASS_NAMES) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz == null || !Fragment.class.isAssignableFrom(clazz))
                continue;
            info = new ClassInfo(clazz);
            for (String methodName : METHOD_NAMES) {
                if (methodName.equals("onShowAnswerUi")) {
                    for (String realMethodName : new String[] { methodName, "a" }) {
                        Method m = XposedHelpers.findMethodExactIfExists(clazz, realMethodName,
                            boolean.class);
                        if (m != null) {
                            info.methods.put(methodName, realMethodName);
                            break;
                        }
                    }
                }
            }
        }
        return info;
    }

    private static ClassInfo resolveCallButtonFragment(ClassLoader cl) {
        ClassInfo info = null;
        String[] CLASS_NAMES = new String[] { "com.android.incallui.CallButtonFragment" };
        String[] METHOD_NAMES = new String[] { "setEnabled" };
        for (String className : CLASS_NAMES) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz == null || !Fragment.class.isAssignableFrom(clazz))
                continue;
            info = new ClassInfo(clazz);
            for (String methodName : METHOD_NAMES) {
                if (methodName.equals("setEnabled")) {
                    for (String realMethodName : new String[] { methodName, "a" }) {
                        Method m = XposedHelpers.findMethodExactIfExists(clazz, realMethodName,
                            boolean.class);
                        if (m != null) {
                            info.methods.put(methodName, realMethodName);
                            break;
                        }
                    }
                }
            }
        }
        return info;
    }

    private static ClassInfo resolveDialtactsActivity(ClassLoader cl) {
        ClassInfo info = null;
        String[] CLASS_NAMES = new String[] { CLASS_DIALTACTS_ACTIVITY };
        String[] METHOD_NAMES = new String[] { "displayFragment" };
        for (String className : CLASS_NAMES) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz == null || !Activity.class.isAssignableFrom(clazz))
                continue;
            info = new ClassInfo(clazz);
            for (String methodName : METHOD_NAMES) {
                if (methodName.equals("displayFragment")) {
                    for (String realMethodName : new String[] { methodName, "c" }) {
                        Method m = XposedHelpers.findMethodExactIfExists(clazz, realMethodName,
                            Intent.class);
                        if (m != null) {
                            info.methods.put(methodName, realMethodName);
                            if (realMethodName.equals(methodName)) {
                                info.extra = "showDialpadFragment";
                            } else {
                                info.extra = "b";
                            }
                            break;
                        }
                    }
                }
            }
        }
        return info;
    }

    private static ClassInfo resolveDialpadFragment(ClassLoader cl) {
        ClassInfo info = null;
        String[] CLASS_NAMES = new String[] { "com.android.dialer.app.dialpad.DialpadFragment" };
        String[] METHOD_NAMES = new String[] { "onResume", "playTone" };
        for (String className : CLASS_NAMES) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz == null || !Fragment.class.isAssignableFrom(clazz))
                continue;
            info = new ClassInfo(clazz);
            for (String methodName : METHOD_NAMES) {
                Method m = null;
                if (methodName.equals("onResume")) {
                    m = XposedHelpers.findMethodExactIfExists(clazz, methodName);
                } else if (methodName.equals("playTone")) {
                    for (String realMethodName : new String[] { methodName, "a" }) {
                        m = XposedHelpers.findMethodExactIfExists(clazz, realMethodName,
                            int.class, int.class);
                        if (m != null) break;
                    }
                }
                if (m != null) {
                    info.methods.put(methodName, m.getName());
                }
            }
        }
        return info;
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader, final String packageName) {
        try {
            final ClassInfo classInfoAnswerFragment = resolveAnswerFragment(classLoader);
            final ClassInfo classInfoCallButtonFragment = resolveCallButtonFragment(classLoader); 

            XposedHelpers.findAndHookMethod(classInfoCallButtonFragment.clazz,
                    classInfoCallButtonFragment.methods.get("setEnabled"),
                    boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final View view = ((Fragment)param.thisObject).getView();
                    final boolean fsc = prefs.getBoolean(
                            GravityBoxSettings.PREF_KEY_CALLER_FULLSCREEN_PHOTO, false);
                    if (fsc & view != null) {
                        view.setVisibility(!(Boolean)param.args[0] ? View.GONE : View.VISIBLE);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classInfoAnswerFragment.clazz,
                    classInfoAnswerFragment.methods.get("onShowAnswerUi"),
                    boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!(Boolean) param.args[0]) return;

                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_CALLER_FULLSCREEN_PHOTO, false)) { 
                    final View v = ((Fragment)param.thisObject).getView();
                        v.setBackgroundColor(0);
                        if (Utils.isMtkDevice()) {
                            final View gpView = (View) XposedHelpers.getObjectField(param.thisObject, "a");
                            gpView.setBackgroundColor(0);
                        }
                        if (DEBUG) log("AnswerFragment showAnswerUi: background color set");
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            final ClassInfo classInfoCallCardFragment = resolveCallCardFragment(classLoader);

            XC_MethodHook unknownCallerHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!prefs.getBoolean(
                            GravityBoxSettings.PREF_KEY_CALLER_UNKNOWN_PHOTO_ENABLE, false)) return;

                    int idx = param.args.length-1;
                    boolean shouldShowUnknownPhoto = param.args[idx] == null;
                    final Fragment frag = (Fragment) param.thisObject;
                    final Resources res = frag.getResources();
                    if (param.args[idx] != null) {
                        String resName = "img_no_image_automirrored";
                        Drawable picUnknown = res.getDrawable(res.getIdentifier(resName, "drawable",
                                        res.getResourcePackageName(frag.getId())), null);
                        shouldShowUnknownPhoto = ((Drawable)param.args[idx]).getConstantState().equals(
                                                    picUnknown.getConstantState());
                    }

                    if (shouldShowUnknownPhoto) {
                        final String path = Utils.getGbContext(frag.getContext()).getFilesDir() + "/caller_photo";
                        File f = new File(path);
                        if (f.exists() && f.canRead()) {
                            Bitmap b = BitmapFactory.decodeFile(path);
                            if (b != null) {
                                param.args[idx] = new BitmapDrawable(res, b);
                                if (DEBUG) log("Unknow caller photo set");
                            }
                        }
                    }
                }
            };
            XposedHelpers.findAndHookMethod(classInfoCallCardFragment.clazz,
                    classInfoCallCardFragment.methods.get("setDrawableToImageView"),
                    Drawable.class, unknownCallerHook);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            final ClassInfo classInfoDialtactsActivity = resolveDialtactsActivity(classLoader);

            XposedHelpers.findAndHookMethod(classInfoDialtactsActivity.clazz,
                    classInfoDialtactsActivity.methods.get("displayFragment"),
                    Intent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    prefs.reload();
                    if (!prefs.getBoolean(GravityBoxSettings.PREF_KEY_DIALER_SHOW_DIALPAD, false)) return;

                    final String realClassName = param.thisObject.getClass().getName();
                    if (realClassName.equals(CLASS_DIALTACTS_ACTIVITY)) {
                        XposedHelpers.callMethod(param.thisObject,
                                classInfoDialtactsActivity.extra.toString(), false);
                        if (DEBUG) log("showDialpadFragment() called within " + realClassName);
                    } else if (realClassName.equals(CLASS_DIALTACTS_ACTIVITY_GOOGLE)) {
                        final Class<?> superc = param.thisObject.getClass().getSuperclass();
                        Method m = XposedHelpers.findMethodExact(superc,
                                classInfoDialtactsActivity.extra.toString(), boolean.class);
                        m.invoke(param.thisObject, false);
                        if (DEBUG) log("showDialpadFragment() called within " + realClassName);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            final ClassInfo classInfoDialpadFragment = resolveDialpadFragment(classLoader);

            XposedHelpers.findAndHookMethod(classInfoDialpadFragment.clazz,
                    classInfoDialpadFragment.methods.get("onResume"), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param2) throws Throwable {
                    XSharedPreferences qhPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "quiet_hours");
                    mQuietHours = new QuietHours(qhPrefs);
                }
            });

            XposedHelpers.findAndHookMethod(classInfoDialpadFragment.clazz,
                    classInfoDialpadFragment.methods.get("playTone"),
                    int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mQuietHours.isSystemSoundMuted(QuietHours.SystemSound.DIALPAD)) {
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
