package com.wrbug.gravitybox.nougat.quicksettings;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import com.wrbug.gravitybox.nougat.Utils;

import android.content.Context;
import android.provider.Settings;
import android.view.View;

public class WifiTile extends AospTile {
    public static final String AOSP_KEY = "wifi";
    public static final String KEY = "aosp_tile_wifi";

    private Unhook mCreateTileViewHook;
    private Unhook mSupportsDualTargetsHook;

    protected WifiTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, KEY, tile, prefs, eventDistributor);

        createHooks();
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }

    @Override
    public boolean handleLongClick() {
        if (!mDualMode) {
            XposedHelpers.callMethod(mTile, "handleSecondaryClick");
        } else {
            startSettingsActivity(Settings.ACTION_WIFI_SETTINGS);
        }
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        destroyHooks();
    }

    private void createHooks() {
        try {
            final ClassLoader cl = mContext.getClassLoader();

            mCreateTileViewHook = XposedHelpers.findAndHookMethod(mTile.getClass().getName(), 
                    cl, "createTileView", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    onCreateTileView((View)param.getResult());
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        // this seems to be unsupported on some custom ROMs. Log one line and continue.
        XC_MethodHook dtHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(mDualMode);
            }
        };
        try {
            mSupportsDualTargetsHook = XposedHelpers.findAndHookMethod(mTile.getClass().getName(), 
                    mContext.getClassLoader(), "supportsDualTargets", dtHook);
        } catch (Throwable t) {
            try {
                mSupportsDualTargetsHook = XposedHelpers.findAndHookMethod(mTile.getClass().getName(), 
                        mContext.getClassLoader(), "hasDualTargetsDetails", dtHook);
            } catch (Throwable t2) {
                if (!Utils.isOxygenOs35Rom()) {
                    log(getKey() + ": Your system does not seem to support standard AOSP tile dual mode");
                }
            }
        }
    }

    private void destroyHooks() {
        if (mSupportsDualTargetsHook != null) {
            mSupportsDualTargetsHook.unhook();
            mSupportsDualTargetsHook = null;
        }
        if (mCreateTileViewHook != null) {
            mCreateTileViewHook.unhook();
            mCreateTileViewHook = null;
        }
    }
}
