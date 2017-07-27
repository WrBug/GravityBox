/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
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

import java.io.File;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            prepareAssets(context);
        }
    }

    // copies required files from assets to file system
    private void prepareAssets(Context context) {
        File f;

        // prepare alternative screenrecord binary if doesn't exist yet
        f = new File(context.getFilesDir() + "/screenrecord");
        if (!f.exists()) {
            String assetName = Build.SUPPORTED_64_BIT_ABIS.length > 0 ?
                    "screenrecord_arm64" : "screenrecord";
            Utils.writeAssetToFile(context, assetName, f);
        }
        if (f.exists()) {
            f.setExecutable(true);
        }
    }
}
