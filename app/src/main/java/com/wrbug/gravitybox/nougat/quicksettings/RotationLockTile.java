package com.wrbug.gravitybox.nougat.quicksettings;

import de.robv.android.xposed.XSharedPreferences;

public class RotationLockTile extends AospTile {
    public static final String AOSP_KEY = "rotation";

    protected RotationLockTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, "aosp_tile_rotation", tile, prefs, eventDistributor);
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }
}
