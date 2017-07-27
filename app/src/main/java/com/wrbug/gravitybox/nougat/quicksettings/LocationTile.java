package com.wrbug.gravitybox.nougat.quicksettings;

import android.provider.Settings;
import de.robv.android.xposed.XSharedPreferences;

public class LocationTile extends AospTile {
    public static final String AOSP_KEY = "location";

    protected LocationTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, "aosp_tile_location", tile, prefs, eventDistributor);
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }

    @Override
    public boolean handleLongClick() {
        startSettingsActivity(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        return true;
    }
}
