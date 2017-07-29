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

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager;
import com.wrbug.gravitybox.nougat.managers.SysUiManagers;
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager.ColorInfo;
import com.wrbug.gravitybox.nougat.managers.StatusBarIconManager.IconManagerListener;

import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

public class StatusbarBattery implements IconManagerListener {
    private static final String TAG = "GB:StatusbarBattery";

    private View mBattery;
    private Drawable mDrawable;
    private int mDefaultColor;
    private int mDefaultFrameColor;
    private int mFrameAlpha;
    private int mDefaultChargeColor;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public StatusbarBattery(View batteryView) {
        mBattery = batteryView;
        createHooks();
        try {
            Object drawable = getDrawable();
            final int[] colors = (int[]) XposedHelpers.getObjectField(drawable, "mColors");
            mDefaultColor = colors[colors.length - 1];
            final Paint framePaint = (Paint) XposedHelpers.getObjectField(drawable, "mFramePaint");
            mDefaultFrameColor = framePaint.getColor();
            mFrameAlpha = framePaint.getAlpha();
            mDefaultChargeColor = XposedHelpers.getIntField(drawable, "mChargeColor");
        } catch (Throwable t) {
            log("Error backing up original colors: " + t.getMessage());
        }
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.registerListener(this);
        }
    }

    private void createHooks() {
        if (!Utils.isXperiaDevice()) {
            try {
                XposedHelpers.findAndHookMethod(getDrawable().getClass(), "getFillColor",
                        float.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (SysUiManagers.IconManager != null &&
                                        SysUiManagers.IconManager.isColoringEnabled()) {
                                    param.setResult(SysUiManagers.IconManager.getIconColor());
                                }
                            }
                        });
            } catch (Throwable t) {
                log("Error hooking getFillColor(): " + t.getMessage());
            }
        }
    }

    public View getView() {
        return mBattery;
    }

    public Drawable getDrawable() {
        if (mDrawable == null) {
            mDrawable = (Drawable) XposedHelpers.getObjectField(mBattery, "mDrawable");
        }
        return mDrawable;
    }

    public void setColors(int mainColor, int frameColor, int chargeColor) {
        if (mBattery != null) {
            try {
                Object drawable = getDrawable();
                final int[] colors = (int[]) XposedHelpers.getObjectField(drawable, "mColors");
                colors[colors.length - 1] = mainColor;
                final Paint framePaint = (Paint) XposedHelpers.getObjectField(drawable, "mFramePaint");
                framePaint.setColor(frameColor);
                framePaint.setAlpha(mFrameAlpha);
                XposedHelpers.setIntField(drawable, "mChargeColor", chargeColor);
                XposedHelpers.setIntField(drawable, "mIconTint", mainColor);
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    }

    public void setShowPercentage(boolean showPercentage) {
        if (mBattery != null) {
            try {
                XposedHelpers.setBooleanField(getDrawable(), "mShowPercent", showPercentage);
                getDrawable().invalidateSelf();
                mBattery.invalidate();
            } catch (Throwable t) {
                log("Error setting percentage: " + t.getMessage());
            }
        }
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if ((flags & StatusBarIconManager.FLAG_ICON_COLOR_CHANGED) != 0) {
            if (colorInfo.coloringEnabled) {
                setColors(colorInfo.iconColor[0], colorInfo.iconColor[0], colorInfo.iconColor[0]);
            } else {
                setColors(mDefaultColor, mDefaultFrameColor, mDefaultChargeColor);
            }
            getDrawable().invalidateSelf();
            mBattery.invalidate();
        }
    }
}
