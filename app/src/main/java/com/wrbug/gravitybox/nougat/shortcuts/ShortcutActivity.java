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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.Utils;
import com.wrbug.gravitybox.nougat.adapters.IIconListAdapterItem;
import com.wrbug.gravitybox.nougat.adapters.IconListAdapter;
import com.wrbug.gravitybox.nougat.ledcontrol.LedSettings;
import com.wrbug.gravitybox.nougat.shortcuts.AShortcut.CreateShortcutListener;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

public class ShortcutActivity extends ListActivity {
    public static final String ACTION_LAUNCH_ACTION = "gravitybox.intent.action.LAUNCH_ACTION";
    public static final String EXTRA_ACTION = "action";
    public static final String EXTRA_ACTION_TYPE = "actionType";
    public static final String EXTRA_ALLOW_UNLOCK_ACTION = "allowUnlockAction";
    public static final String EXTRA_LAUNCHES_FROM_LOCKSCREEN = "launchesFromLockscreen";

    private Context mContext;
    private IconListAdapter mListAdapter;
    private Button mBtnCancel;
    private boolean mInvokedFromGb;
    private boolean mAllowUnlockAction;
    private boolean mLaunchesFromLockscreen;

    private static List<String> UNSAFE_ACTIONS = new ArrayList<String>(Arrays.asList(
            NetworkModeShortcut.ACTION,
            MobileDataShortcut.ACTION,
            WifiShortcut.ACTION,
            BluetoothShortcut.ACTION,
            WifiApShortcut.ACTION,
            NfcShortcut.ACTION,
            LocationModeShortcut.ACTION,
            SmartRadioShortcut.ACTION,
            AirplaneModeShortcut.ACTION,
            SimSettingsShortcut.ACTION
    ));

    public static boolean isActionSafe(String action) {
        return (!UNSAFE_ACTIONS.contains(action));
    }

    public static boolean isGbBroadcastShortcut(Intent intent) {
        return (intent != null && intent.getAction() != null &&
                intent.getAction().equals(ShortcutActivity.ACTION_LAUNCH_ACTION) &&
                intent.hasExtra(ShortcutActivity.EXTRA_ACTION_TYPE) &&
                intent.getStringExtra(ShortcutActivity.EXTRA_ACTION_TYPE).equals("broadcast"));
    }

    public static boolean isGbUnlockShortcut(Intent intent) {
        return (intent != null && intent.getAction() != null &&
                intent.getAction().equals(ShortcutActivity.ACTION_LAUNCH_ACTION) &&
                intent.hasExtra(ShortcutActivity.EXTRA_ACTION_TYPE) &&
                intent.getStringExtra(ShortcutActivity.EXTRA_ACTION_TYPE).equals("unlock"));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        File file = new File(getFilesDir() + "/" + GravityBoxSettings.FILE_THEME_DARK_FLAG);
        if (file.exists()) {
            setTheme(R.style.AppThemeDark);
        }

        super.onCreate(savedInstanceState);

        mContext = this;
        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            finish();
            return;
        } else if (intent.getAction().equals(Intent.ACTION_CREATE_SHORTCUT)) {
            setContentView(R.layout.shortcut_activity);
            mBtnCancel = (Button) findViewById(R.id.btnCancel);
            mBtnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            mInvokedFromGb = intent.hasExtra("gravitybox");
            mAllowUnlockAction = intent.getBooleanExtra(EXTRA_ALLOW_UNLOCK_ACTION, false);
            mLaunchesFromLockscreen = intent.getBooleanExtra(EXTRA_LAUNCHES_FROM_LOCKSCREEN, false);
            return;
        } else {
            finish();
            return;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setData();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void setData() {
        ArrayList<IIconListAdapterItem> list = new ArrayList<IIconListAdapterItem>();
        if (mAllowUnlockAction) {
            list.add(new UnlockShortcut(mContext));
        }
        if (!mLaunchesFromLockscreen) {
            list.add(new GoHomeShortcut(mContext));
        }
        list.add(new ShowPowerMenuShortcut(mContext));
        if (!mLaunchesFromLockscreen) {
            list.add(new ExpandNotificationsShortcut(mContext));
        }
        list.add(new ClearNotificationsShortcut(mContext));
        list.add(new ExpandQuicksettingsShortcut(mContext));
        list.add(new ExpandedDesktopShortcut(mContext));
        list.add(new GoogleNowShortcut(mContext));
        list.add(new ScreenshotShortcut(mContext));
        list.add(new ScreenrecordShortcut(mContext));
        if (Utils.hasFlash(mContext)) {
            list.add(new TorchShortcut(mContext));
        }
        list.add(new LocationModeShortcut(mContext));
        list.add(new WifiShortcut(mContext));
        list.add(new WifiApShortcut(mContext));
        if (!Utils.isWifiOnly(mContext)) {
            if (!mLaunchesFromLockscreen) {
                list.add(new SimSettingsShortcut(mContext));
            }
            list.add(new MobileDataShortcut(mContext));
            list.add(new NetworkModeShortcut(mContext));
            list.add(new SmartRadioShortcut(mContext));
        }
        list.add(new AirplaneModeShortcut(mContext));
        list.add(new BluetoothShortcut(mContext));
        if (Utils.hasNfc(mContext)) {
            list.add(new NfcShortcut(mContext));
        }
        list.add(new SyncShortcut(mContext));
        if (mInvokedFromGb) {
            list.add(new MediaControlShortcut(mContext));
        }
        list.add(new VolumePanelShortcut(mContext));
        list.add(new RingerModeShortcut(mContext));
        list.add(new AutoBrightnessShortcut(mContext));
        list.add(new RecentAppsShortcut(mContext));
        if (mInvokedFromGb && !mLaunchesFromLockscreen) {
            list.add(new KillAppShortcut(mContext));
            list.add(new SwitchAppShortcut(mContext));
        }
        list.add(new AppLauncherShortcut(mContext));
        if (!mLaunchesFromLockscreen) {
            list.add(new LauncherDrawerShortcut(mContext));
        }
        list.add(new RotationLockShortcut(mContext));
        list.add(new SleepShortcut(mContext));
        if (!LedSettings.isUncLocked(mContext)) {
            list.add(new QuietHoursShortcut(mContext));
        }

        mListAdapter = new IconListAdapter(mContext, list);
        setListAdapter(mListAdapter);
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onListItemClick(ListView list, View view, int position, long id) {
        AShortcut s = (AShortcut) mListAdapter.getItem(position);
        s.createShortcut(new CreateShortcutListener() {
            @Override
            public void onShortcutCreated(Intent intent) {
                ShortcutActivity.this.setResult(RESULT_OK, intent);
                finish();
            }
        });
    }
}
