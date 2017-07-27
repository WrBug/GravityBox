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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import com.wrbug.gravitybox.nougat.ModStatusBar.ContainerType;
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager;
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager.ColorInfo;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public class StatusbarSignalClusterMoto extends StatusbarSignalCluster {
    protected static final String[] MOBILE_ICON_SPACERS = new String[] { "mSpacerView_Phone_1a",
            "mSpacerView_Phone_1b", "mSpacerView_Phone_2a", "mSpacerView_Phone_2b" };

    protected SignalActivity[] mMobileActivity;
    protected boolean mHideSimLabels;
    protected boolean mNarrowIcons;
    protected int mIconSpacingPx;
    protected Map<String, Integer> mIconSpacingDef;

    public StatusbarSignalClusterMoto(ContainerType containerType, LinearLayout view) throws Throwable {
        super(containerType, view);
    }

    @Override
    protected void initPreferences() {
        super.initPreferences();
        mHideSimLabels = sPrefs.getBoolean(GravityBoxSettings.PREF_KEY_SIGNAL_CLUSTER_HIDE_SIM_LABELS, false);
        mNarrowIcons = sPrefs.getBoolean(GravityBoxSettings.PREF_KEY_SIGNAL_CLUSTER_NARROW, false);
        mIconSpacingPx = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                mResources.getDisplayMetrics()));
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) { 
        super.onBroadcastReceived(context, intent);

        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_SIGNAL_CLUSTER_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_SC_NARROW)) {
                mNarrowIcons = intent.getBooleanExtra(GravityBoxSettings.EXTRA_SC_NARROW, false);
                updateMobileIconSpacing();
            }
        }
    }

    @Override
    protected void createHooks() {
        try {
            XposedHelpers.findAndHookMethod(mView.getClass(), "apply", 
                    int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mView != param.thisObject) return;
                    apply((Integer) param.args[0]);
                }
            });

            if (mDataActivityEnabled) {
                try {
                    XposedHelpers.findAndHookMethod(mView.getClass(), "onAttachedToWindow", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (mView != param.thisObject) return;

                            View v = (View) XposedHelpers.getObjectField(mView, "mWifiSignalView");
                            if (v != null && v.getParent() instanceof FrameLayout) {
                                mWifiActivity = new SignalActivity((FrameLayout)v.getParent(), SignalType.WIFI);
                                if (DEBUG) log("onAttachedToWindow: mWifiActivity created");
                            }

                            if (mMobileActivity == null) {
                                mMobileActivity = new SignalActivity[PhoneWrapper.getPhoneCount()];
                            }

                            for (int i=0; i < PhoneWrapper.getPhoneCount(); i++) {
                                v = (View) ((View[])XposedHelpers.getObjectField(mView, "mMobileSignalView"))[i];
                                if (v != null && v.getParent() instanceof FrameLayout) {
                                    mMobileActivity[i] = new SignalActivity((FrameLayout)v.getParent(), SignalType.MOBILE,
                                            Gravity.BOTTOM | Gravity.END);
                                    if (DEBUG) log("onAttachedToWindow: mMobileActivity" + i + " created");
                                }
                            }
                        }
                    });

                    XposedHelpers.findAndHookMethod(mView.getClass(), "onDetachedFromWindow", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (mView != param.thisObject) return;

                            mWifiActivity = null;
                            if (mMobileActivity != null) {
                                for (int i=0; i < mMobileActivity.length; i++) {
                                    mMobileActivity[i] = null;
                                }
                            }
                            if (DEBUG) log("onDetachedFromWindow: signal activities destoyed");
                        }
                    });
                } catch (Throwable t) {
                    log("Error hooking SignalActivity related methods: " + t.getMessage());
                }
            }

        } catch (Throwable t) {
            log("Error hooking apply() method: " + t.getMessage());
        }
    }

    @Override
    protected void setNetworkController(Object networkController) {
        final ClassLoader classLoader = mView.getClass().getClassLoader();
        final Class<?> networkCtrlCbClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback", 
                classLoader);
        for (int i=0; i < PhoneWrapper.getPhoneCount(); i++) {
            XposedHelpers.callMethod(networkController, "addNetworkSignalChangedCallback",
                    Proxy.newProxyInstance(classLoader, new Class<?>[] { networkCtrlCbClass },
                        new NetworkControllerCallbackMsim()), i);
        }
        if (DEBUG) log("setNetworkController: callback registered");
    }

    protected void apply(int simSlot) {
        if (mHideSimLabels) {
            hideSimLabel(simSlot);
        }

        if (mIconSpacingDef == null && mView.isAttachedToWindow()) {
            mIconSpacingDef = new HashMap<String, Integer>(MOBILE_ICON_SPACERS.length);
            if (mNarrowIcons) {
                updateMobileIconSpacing();
            }
        }
    }

    private void hideSimLabel(int simSlot) {
        try {
            View simLabel = (View) ((View[])XposedHelpers.getObjectField(mView, "mMobilePhoneView"))[simSlot];
            if (simLabel != null) {
                simLabel.setVisibility(View.GONE);
            }
        } catch (Throwable t) {
            logAndMute("hideSimLabel", t);
        }
    }

    private void updateMobileIconSpacing() {
        for (String spacer : MOBILE_ICON_SPACERS) {
            try {
                View v = (View) XposedHelpers.getObjectField(mView, spacer);
                if (v == null) continue;
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (mIconSpacingDef.get(spacer) == null) {
                    mIconSpacingDef.put(spacer, lp.width);
                }
                lp.width = mNarrowIcons ? mIconSpacingPx :
                    mIconSpacingDef.get(spacer);
                v.setLayoutParams(lp);
            } catch (Throwable t) {
                if (DEBUG) XposedBridge.log(t);
            }
        }
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        super.onIconManagerStatusChanged(flags, colorInfo);

        if ((flags & StatusBarIconManager.FLAG_DATA_ACTIVITY_COLOR_CHANGED) != 0 &&
                    mDataActivityEnabled && mMobileActivity != null) {
            for (int i=0; i < mMobileActivity.length; i++) {
                if (mMobileActivity[i] != null) mMobileActivity[i].updateDataActivityColor();
            }
        }
    }

    protected class NetworkControllerCallbackMsim implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();

            try {
                if (methodName.equals("onWifiSignalChanged")) {
                    if (DEBUG) {
                        log("WiFi enabled: " + args[0]);
                        log("WiFi activity in: " + (Boolean)args[4]);
                        log("WiFi activity out: " + (Boolean)args[5]);
                    }
                    if (mWifiActivity != null) {
                        mWifiActivity.update((Boolean)args[0],
                                (Boolean)args[4], (Boolean)args[5]);
                    }
                } else if (methodName.equals("onMobileDataSignalChanged")) {
                    if (DEBUG) {
                        log("Mobile SIM slot: " + args[22]);
                        log("Mobile data enabled: " + args[0]);
                        log("Mobile data activity in: " + (Boolean)args[7]);
                        log("Mobile data activity out: " + (Boolean)args[8]);
                    }
                    int simSlot = (int) args[22];
                    if (mMobileActivity != null && mMobileActivity[simSlot] != null) {
                        mMobileActivity[simSlot].update((Boolean)args[0], 
                                (Boolean)args[7], (Boolean)args[8]);
                    }
                }
            } catch (Throwable t) {
                logAndMute("NetworkControllerCallback", t);
            }

            return null;
        }
    }
}
