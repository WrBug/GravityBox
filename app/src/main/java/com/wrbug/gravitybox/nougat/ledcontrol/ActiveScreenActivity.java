/*
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.wrbug.gravitybox.nougat.ledcontrol;

import java.io.File;

import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.util.SharedPreferencesUtils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;

public class ActiveScreenActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        File file = new File(getFilesDir() + "/" + GravityBoxSettings.FILE_THEME_DARK_FLAG);
        if (file.exists()) {
            setTheme(R.style.AppThemeDark);
        }

        super.onCreate(savedInstanceState);

        setContentView(R.layout.active_screen_activity);
    }

    public static class PrefsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
        private SharedPreferences mPrefs;
        private CheckBoxPreference mPrefPocketMode;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mPrefs = SharedPreferencesUtils.getSharedPreferences(getPreferenceManager(), "ledcontrol");
            addPreferencesFromResource(R.xml.led_control_active_screen_settings);

            mPrefPocketMode = (CheckBoxPreference) findPreference(
                    LedSettings.PREF_KEY_ACTIVE_SCREEN_POCKET_MODE);

            if (LedSettings.isProximityWakeUpEnabled(getActivity())) {
                mPrefPocketMode.setSummary(R.string.pref_unc_as_pocket_mode_summary_overriden);
                mPrefPocketMode.setEnabled(false);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            mPrefs.registerOnSharedPreferenceChangeListener(this);
            updateSummaries();
        }

        @Override
        public void onPause() {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
            super.onPause();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            Intent intent = new Intent(LedSettings.ACTION_UNC_SETTINGS_CHANGED);
            if (LedSettings.PREF_KEY_ACTIVE_SCREEN_ENABLED.equals(key)) {
                intent.putExtra(LedSettings.EXTRA_UNC_AS_ENABLED, prefs.getBoolean(key, false));
            }
            prefs.edit().commit();
            getActivity().sendBroadcast(intent);
            updateSummaries();
        }

        private void updateSummaries() {
        }
    }
}
