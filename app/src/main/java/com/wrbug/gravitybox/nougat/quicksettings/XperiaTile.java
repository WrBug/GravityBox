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
package com.wrbug.gravitybox.nougat.quicksettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XSharedPreferences;

public class XperiaTile extends AospTile {

    public static final List<String> XPERIA_KEYS = new ArrayList<>(Arrays.asList(
        "sync", "roaming", "nfc", "lte", "datatraffic",
        "screenmirroring", "throw", "volte",
        "tethering", "detectusbdevice"
    ));

    private String mAospKey;

    protected XperiaTile(Object host, String aospKey, Object tile,
            XSharedPreferences prefs, QsTileEventDistributor eventDistributor)
                    throws Throwable {
        super(host, "xperia_tile_"+aospKey, tile, prefs, eventDistributor);

        mAospKey = aospKey;
    }

    @Override
    public String getAospKey() {
        return mAospKey;
    }
}
