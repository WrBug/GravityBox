package com.wrbug.gravitybox.nougat.quicksettings;

import android.provider.Settings;
import de.robv.android.xposed.XSharedPreferences;

public class CastTile extends AospTile {
    public static final String AOSP_KEY = "cast";

    protected CastTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, "aosp_tile_cast", tile, prefs, eventDistributor);
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }

    @Override
    public boolean handleLongClick() {
        startSettingsActivity(Settings.ACTION_CAST_SETTINGS);
        return true;
    }
}
