package com.wrbug.gravitybox.nougat.quicksettings;

import de.robv.android.xposed.XC_MethodHook.Unhook;

import com.wrbug.gravitybox.nougat.Utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ColorInversionTile extends AospTile {
    public static final String AOSP_KEY = "inversion";

    private Unhook mLongClickHook;

    protected ColorInversionTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, "aosp_tile_inversion", tile, prefs, eventDistributor);

        createHooks();
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }

    @Override
    public boolean handleLongClick() {
        // noop
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        destroyHooks();
    }

    private void createHooks() {
        if (Utils.isOxygenOs35Rom())
            return;

        try {
            mLongClickHook = XposedHelpers.findAndHookMethod(mTile.getClass().getName(), 
                    mContext.getClassLoader(), "handleLongClick", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (handleLongClick()) {
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void destroyHooks() {
        if (mLongClickHook != null) {
            mLongClickHook.unhook();
            mLongClickHook = null;
        }
    }
}
