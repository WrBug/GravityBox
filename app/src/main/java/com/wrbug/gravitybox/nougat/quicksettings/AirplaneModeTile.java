package com.wrbug.gravitybox.nougat.quicksettings;

import android.provider.Settings;
import de.robv.android.xposed.XSharedPreferences;

public class AirplaneModeTile extends AospTile {
    public static final String AOSP_KEY = "airplane";

    protected AirplaneModeTile(Object host, Object tile, XSharedPreferences prefs, 
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, "aosp_tile_airplane_mode", tile, prefs, eventDistributor);
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }

    @Override
    public boolean handleLongClick() {
        startSettingsActivity(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        return true;
    }
}
