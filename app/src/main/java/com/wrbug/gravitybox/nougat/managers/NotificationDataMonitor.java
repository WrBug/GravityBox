/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.wrbug.gravitybox.nougat.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.app.Notification;
import android.content.Context;
import android.service.notification.StatusBarNotification;

import com.wrbug.gravitybox.nougat.BuildConfig;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class NotificationDataMonitor {
    public static final String TAG="GB:NotificationDataMonitor";
    private static boolean DEBUG = BuildConfig.DEBUG;

    private static final String CLASS_NOTIF_DATA = "com.android.systemui.statusbar.NotificationData";
    private static final String CLASS_BASE_STATUSBAR = "com.android.systemui.statusbar.BaseStatusBar";

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    public interface Listener {
        void onNotificationDataChanged(final StatusBarNotification sbn);
    }

    private Context mContext;
    private Object mNotifData;
    private List<Listener> mListeners = new ArrayList<>();

    protected NotificationDataMonitor(Context context) {
        if (context == null)
            throw new IllegalArgumentException("Context cannot be null");

        mContext = context;

        createHooks();
    }

    private void createHooks() {
        try {
            ClassLoader cl = mContext.getClassLoader();
            Class<?> classNotifData = XposedHelpers.findClass(CLASS_NOTIF_DATA, cl);
            Class<?> classBaseStatusbar = XposedHelpers.findClass(CLASS_BASE_STATUSBAR, cl);

            XposedBridge.hookAllConstructors(classNotifData, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mNotifData = param.thisObject;
                    if (DEBUG) log("NotificatioData object constructed");
                }
            });

            XposedBridge.hookAllMethods(classNotifData, "add", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if (DEBUG) log("Notification entry added");
                    StatusBarNotification sbn = getSbNotificationFromArgs(param.args);
                    notifyDataChanged(sbn);
                }
            });

            XposedBridge.hookAllMethods(classNotifData, "remove", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if (DEBUG) log("Notification entry removed");
                    StatusBarNotification sbn = null;
                    if (hasNotificationField(param.getResult())) {
                        sbn = (StatusBarNotification) XposedHelpers.getObjectField(
                                param.getResult(), "notification");
                    }
                    notifyDataChanged(sbn);
                }
            });

            XposedBridge.hookAllMethods(classBaseStatusbar, "updateNotification", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (DEBUG) log("Notification entry updated");
                    StatusBarNotification sbn = getSbNotificationFromArgs(param.args);
                    notifyDataChanged(sbn);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private StatusBarNotification getSbNotificationFromArgs(Object[] args) {
        for (Object o : args) {
            if (o instanceof StatusBarNotification)
                return (StatusBarNotification) o;
            else if (hasNotificationField(o))
                return (StatusBarNotification)
                        XposedHelpers.getObjectField(o, "notification");
        }
        return null;
    }

    private boolean hasNotificationField(Object o) {
        try {
            XposedHelpers.getObjectField(o, "notification");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void notifyDataChanged(StatusBarNotification sbn) {
        synchronized (mListeners) {
            for (Listener l : mListeners) {
                l.onNotificationDataChanged(sbn);
            }
        }
    }

    public void registerListener(Listener l) {
        if (l == null) return;
        synchronized (mListeners) {
            if (!mListeners.contains(l)) {
                mListeners.add(l);
            }
        }
    }

    public void unregisterListener(Listener l) {
        if (l == null) return;
        synchronized (mListeners) {
            if (mListeners.contains(l)) {
                mListeners.remove(l);
            }
        }
    }

    public int getNotifCountFor(String pkg) {
        if (pkg == null) return 0;

        int count = 0;

        try {
            Map<?,?> entries = (Map<?,?>) XposedHelpers.getObjectField(mNotifData, "mEntries");
            for (Object entry : entries.values()) {
                StatusBarNotification sbn = (StatusBarNotification)
                        XposedHelpers.getObjectField(entry, "notification");
                if (pkg.equals(sbn.getPackageName())) {
                    final Notification n = sbn.getNotification();
                    count += (n.number > 0 ? n.number : 1);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        if (DEBUG) log("getNotifCountFor: " + pkg + "=" + count);

        return count;
    }
}
