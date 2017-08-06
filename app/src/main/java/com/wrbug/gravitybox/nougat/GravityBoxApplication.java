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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.TextUtils;

import com.tencent.bugly.Bugly;
import com.tencent.bugly.beta.Beta;
import com.tencent.bugly.crashreport.CrashReport;

public class GravityBoxApplication extends Application {

    private Locale mLocale = null;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!BuildConfig.DEBUG) {
            initBugly();
        }
    }

    private void initBugly() {
        Beta.upgradeCheckPeriod = 60 * 1000 * 60 * 12;
        Beta.initDelay = 1000;
        Beta.showInterruptedStrategy = true;
        Beta.enableHotfix = false;
        Bugly.init(getApplicationContext(), "375cce688e", BuildConfig.DEBUG);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mLocale != null && !newConfig.locale.getLanguage().equals("en")) {
            Resources res = getBaseContext().getResources();
            Configuration config = res.getConfiguration();
            Locale.setDefault(mLocale);
            config.locale = mLocale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
    }
}
