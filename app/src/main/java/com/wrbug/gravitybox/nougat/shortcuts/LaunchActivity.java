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

package com.wrbug.gravitybox.nougat.shortcuts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class LaunchActivity extends Activity {

    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;
        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            finish();
            return;
        } else if (intent.getAction().equals(ShortcutActivity.ACTION_LAUNCH_ACTION) &&
                intent.hasExtra(ShortcutActivity.EXTRA_ACTION)) {
            launchAction(intent);
            finish();
            return;
        } else {
            finish();
            return;
        }
    }

    private void launchAction(Intent intent) {
        final String action = intent.getStringExtra(ShortcutActivity.EXTRA_ACTION);

        if (action.equals(ShowPowerMenuShortcut.ACTION)) {
            ShowPowerMenuShortcut.launchAction(mContext, intent);
        } else if (action.equals(ExpandNotificationsShortcut.ACTION)) {
            ExpandNotificationsShortcut.launchAction(mContext, intent);
        } else if (action.equals(ExpandQuicksettingsShortcut.ACTION)) {
            ExpandQuicksettingsShortcut.launchAction(mContext, intent);
        } else if (action.equals(ExpandedDesktopShortcut.ACTION)) {
            ExpandedDesktopShortcut.launchAction(mContext, intent);
        } else if (action.equals(ScreenshotShortcut.ACTION)) {
            ScreenshotShortcut.launchAction(mContext, intent);
        } else if (action.equals(ScreenrecordShortcut.ACTION)) {
            ScreenrecordShortcut.launchAction(mContext, intent);
        } else if (action.equals(TorchShortcut.ACTION)) {
            TorchShortcut.launchAction(mContext, intent);
        } else if (action.equals(NetworkModeShortcut.ACTION)) {
            NetworkModeShortcut.launchAction(mContext, intent);
        } else if (action.equals(RecentAppsShortcut.ACTION)) {
            RecentAppsShortcut.launchAction(mContext, intent);
        } else if (action.equals(AppLauncherShortcut.ACTION)) {
            AppLauncherShortcut.launchAction(mContext, intent);
        } else if (action.equals(RotationLockShortcut.ACTION)) {
            RotationLockShortcut.launchAction(mContext, intent);
        } else if (action.equals(SleepShortcut.ACTION)) {
            SleepShortcut.launchAction(mContext, intent);
        } else if (action.equals(MobileDataShortcut.ACTION)) {
            MobileDataShortcut.launchAction(mContext, intent);
        } else if (action.equals(WifiShortcut.ACTION)) {
            WifiShortcut.launchAction(mContext, intent);
        } else if (action.equals(BluetoothShortcut.ACTION)) {
            BluetoothShortcut.launchAction(mContext, intent);
        } else if (action.equals(WifiApShortcut.ACTION)) {
            WifiApShortcut.launchAction(mContext, intent);
        } else if (action.equals(LocationModeShortcut.ACTION)) {
            LocationModeShortcut.launchAction(mContext, intent);
        } else if (action.equals(NfcShortcut.ACTION)) {
            NfcShortcut.launchAction(mContext, intent);
        } else if (action.equals(GoogleNowShortcut.ACTION)) {
            GoogleNowShortcut.launchAction(mContext, intent);
        } else if (action.equals(VolumePanelShortcut.ACTION)) {
            VolumePanelShortcut.launchAction(mContext, intent);
        } else if (action.equals(LauncherDrawerShortcut.ACTION)) {
            LauncherDrawerShortcut.launchAction(mContext, intent);
        } else if (action.equals(SmartRadioShortcut.ACTION)) {
            SmartRadioShortcut.launchAction(mContext, intent);
        } else if (action.equals(QuietHoursShortcut.ACTION)) {
            QuietHoursShortcut.launchAction(mContext, intent);
        } else if (action.equals(AirplaneModeShortcut.ACTION)) {
            AirplaneModeShortcut.launchAction(mContext, intent);
        } else if (action.equals(RingerModeShortcut.ACTION)) {
            RingerModeShortcut.launchAction(mContext, intent);
        } else if (action.equals(SyncShortcut.ACTION)) {
            SyncShortcut.launchAction(mContext, intent);
        } else if (action.equals(ClearNotificationsShortcut.ACTION)) {
            ClearNotificationsShortcut.launchAction(mContext, intent);
        } else if (action.equals(AutoBrightnessShortcut.ACTION)) {
            AutoBrightnessShortcut.launchAction(mContext, intent);
        } else if (action.equals(GoHomeShortcut.ACTION)) {
            GoHomeShortcut.launchAction(mContext, intent);
        } else if (action.equals(SimSettingsShortcut.ACTION)) {
            SimSettingsShortcut.launchAction(mContext, intent);
        }
    }
}
