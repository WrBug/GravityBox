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

package com.wrbug.gravitybox.nougat.quicksettings;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.Utils;
import com.wrbug.gravitybox.nougat.preference.AppPickerPreference;
import com.wrbug.gravitybox.nougat.shortcuts.ShortcutActivity;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.TextUtils.TruncateAt;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.TextView;

public class QuickAppTile extends QsTile {
    private String KEY_QUICKAPP_DEFAULT = GravityBoxSettings.PREF_KEY_QUICKAPP_DEFAULT;
    private String KEY_QUICKAPP_SLOT1 = GravityBoxSettings.PREF_KEY_QUICKAPP_SLOT1;
    private String KEY_QUICKAPP_SLOT2 = GravityBoxSettings.PREF_KEY_QUICKAPP_SLOT2;
    private String KEY_QUICKAPP_SLOT3 = GravityBoxSettings.PREF_KEY_QUICKAPP_SLOT3;
    private String KEY_QUICKAPP_SLOT4 = GravityBoxSettings.PREF_KEY_QUICKAPP_SLOT4;
    private String ACTION_PREF_QUICKAPP_CHANGED = GravityBoxSettings.ACTION_PREF_QUICKAPP_CHANGED;

    private AppInfo mMainApp;
    private List<AppInfo> mAppSlots;
    private PackageManager mPm;
    private Dialog mDialog;
    private Handler mHandler;
    private int mId = 1;

    private final class AppInfo {
        private String mAppName;
        private Drawable mAppIcon;
        private Drawable mAppIconSmall;
        private String mValue;
        private int mResId;
        private Intent mIntent;
        private Resources mResources;

        public AppInfo(int resId) {
            mResId = resId;
            mResources = mGbContext.getResources();
        }

        public int getResId() {
            return mResId;
        }

        public String getAppName() {
            return (mAppName == null ? 
                    mGbContext.getString(R.string.qs_tile_quickapp) : mAppName);
        }

        public Drawable getAppIcon() {
            return (mAppIcon == null ? 
                    mResources.getDrawable(android.R.drawable.ic_menu_help) : mAppIcon);
        }

        public Drawable getAppIconSmall() {
            return (mAppIconSmall == null ? 
                    mResources.getDrawable(android.R.drawable.ic_menu_help) : mAppIconSmall);
        }

        public String getValue() {
            return mValue;
        }

        public Intent getIntent() {
            return mIntent;
        }

        private void reset() {
            mValue = mAppName = null;
            mAppIcon = mAppIconSmall = null;
            mIntent = null;
        }

        public void initAppInfo(String value) {
            mValue = value;
            if (mValue == null) {
                reset();
                return;
            }

            try {
                mIntent = Intent.parseUri(value, 0);
                if (!mIntent.hasExtra("mode")) {
                    reset();
                    return;
                }
                final int mode = mIntent.getIntExtra("mode", AppPickerPreference.MODE_APP);

                Bitmap appIcon = null;
                final int iconResId = mIntent.getStringExtra("iconResName") != null ?
                        mResources.getIdentifier(mIntent.getStringExtra("iconResName"),
                        "drawable", mGbContext.getPackageName()) : 0;
                if (iconResId != 0) {
                    appIcon = Utils.drawableToBitmap(mResources.getDrawable(iconResId));
                } else {
                    final String appIconPath = mIntent.getStringExtra("icon");
                    if (appIconPath != null) {
                        File f = new File(appIconPath);
                        if (f.exists() && f.canRead()) {
                            FileInputStream fis = new FileInputStream(f);
                            appIcon = BitmapFactory.decodeStream(fis);
                            fis.close();
                        }
                    }
                }

                if (mode == AppPickerPreference.MODE_APP) {
                    ActivityInfo ai = mPm.getActivityInfo(mIntent.getComponent(), 0);
                    mAppName = ai.loadLabel(mPm).toString();
                    if (appIcon == null) {
                        appIcon = Utils.drawableToBitmap(ai.loadIcon(mPm));
                    }
                } else if (mode == AppPickerPreference.MODE_SHORTCUT) {
                    mAppName = mIntent.getStringExtra("label");
                }
                if (appIcon != null) {
                    int sizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, 
                            mResources.getDisplayMetrics());
                    int sizePxSmall = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 35, 
                            mResources.getDisplayMetrics());
                    Bitmap scaledIcon;
                    scaledIcon = Bitmap.createScaledBitmap(appIcon, sizePx, sizePx, true);
                    mAppIcon = new BitmapDrawable(mResources, scaledIcon);
                    scaledIcon = Bitmap.createScaledBitmap(appIcon, sizePxSmall, sizePxSmall, true);
                    mAppIconSmall = new BitmapDrawable(mResources, scaledIcon);
                }
                if (DEBUG) log(getKey() + ": AppInfo initialized for: " + getAppName());
            } catch (NameNotFoundException e) {
                log(getKey() + ": App not found: " + mIntent);
                reset();
            } catch (Exception e) {
                log(getKey() + ": Unexpected error: " + e.getMessage());
                reset();
            }
        }
    }

    private Runnable mDismissDialogRunnable = new Runnable() {

        @Override
        public void run() {
            if (mDialog != null && mDialog.isShowing()) {
                mDialog.dismiss();
                mDialog = null;
            }
        }
    };

    View.OnClickListener mOnSlotClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mHandler.removeCallbacks(mDismissDialogRunnable);
            if (mDialog != null && mDialog.isShowing()) {
                mDialog.dismiss();
                mDialog = null;
            }

            AppInfo aiProcessing = null;
            try {
                for(AppInfo ai : mAppSlots) {
                    aiProcessing = ai;
                    if (v.getId() == ai.getResId()) {
                        startActivity(ai.getIntent());
                        return;
                    }
                }
            } catch (Exception e) {
                log(getKey() + ": Unable to start activity: " + e.getMessage());
                if (aiProcessing != null) {
                    aiProcessing.initAppInfo(null);
                }
            }
        }
    };

    
    public QuickAppTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        this(host, key, prefs, eventDistributor, 1);
    }

    public QuickAppTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor, int id) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mHandler = new Handler();
        mPm = mContext.getPackageManager();

        mId = id;
        if (mId > 1) {
            KEY_QUICKAPP_DEFAULT += "_" + mId;
            KEY_QUICKAPP_SLOT1 += "_" + mId;
            KEY_QUICKAPP_SLOT2 += "_" + mId;
            KEY_QUICKAPP_SLOT3 += "_" + mId;
            KEY_QUICKAPP_SLOT4 += "_" + mId;
            ACTION_PREF_QUICKAPP_CHANGED += "_" + mId;
        }

        mMainApp = new AppInfo(mId);
        mAppSlots = new ArrayList<AppInfo>();
        mAppSlots.add(new AppInfo(R.id.quickapp1));
        mAppSlots.add(new AppInfo(R.id.quickapp2));
        mAppSlots.add(new AppInfo(R.id.quickapp3));
        mAppSlots.add(new AppInfo(R.id.quickapp4));
        updateMainApp(mPrefs.getString(KEY_QUICKAPP_DEFAULT, null));
        updateSubApp(0, mPrefs.getString(KEY_QUICKAPP_SLOT1, null));
        updateSubApp(1, mPrefs.getString(KEY_QUICKAPP_SLOT2, null));
        updateSubApp(2, mPrefs.getString(KEY_QUICKAPP_SLOT3, null));
        updateSubApp(3, mPrefs.getString(KEY_QUICKAPP_SLOT4, null));
    }

    private void startActivity(Intent intent) {
        if (intent == null) {
            if (DEBUG) log(getKey() + ": startActivity called with null intent");
            return;
        }
        // if intent is a GB action of broadcast type, handle it directly here
        if (ShortcutActivity.isGbBroadcastShortcut(intent)) {
            String action = intent.getStringExtra(ShortcutActivity.EXTRA_ACTION);
            if (ShortcutActivity.isActionSafe(action) || 
                    !(mKgMonitor.isShowing() && mKgMonitor.isLocked())) {
                Intent newIntent = new Intent(action);
                newIntent.putExtras(intent);
                mContext.sendBroadcast(newIntent);
            }
        // otherwise let super class handle it
        } else {
            startSettingsActivity(intent);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        super.onBroadcastReceived(context, intent);
        if (DEBUG) log(getKey() + ": onBroadcastReceived: " + intent.toString());

        if (intent.getAction().equals(ACTION_PREF_QUICKAPP_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QUICKAPP_DEFAULT)) {
                updateMainApp(intent.getStringExtra(GravityBoxSettings.EXTRA_QUICKAPP_DEFAULT));
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QUICKAPP_SLOT1)) {
                updateSubApp(0, intent.getStringExtra(GravityBoxSettings.EXTRA_QUICKAPP_SLOT1));
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QUICKAPP_SLOT2)) {
                updateSubApp(1, intent.getStringExtra(GravityBoxSettings.EXTRA_QUICKAPP_SLOT2));
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QUICKAPP_SLOT3)) {
                updateSubApp(2, intent.getStringExtra(GravityBoxSettings.EXTRA_QUICKAPP_SLOT3));
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QUICKAPP_SLOT4)) {
                updateSubApp(3, intent.getStringExtra(GravityBoxSettings.EXTRA_QUICKAPP_SLOT4));
            }
        }
    }

    private void updateMainApp(String value) {
        if (mMainApp.getValue() == null || !mMainApp.getValue().equals(value)) {
            mMainApp.initAppInfo(value);
        }
    }

    private void updateSubApp(int slot, String value) {
        AppInfo ai;
        ai = mAppSlots.get(slot);
        if (ai.getValue() == null || !ai.getValue().equals(value)) {
            ai.initAppInfo(value);
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        mState.label = mMainApp.getAppName();
        mState.icon = mMainApp.getAppIconSmall();
        super.handleUpdateState(state, arg);
    }

    @Override
    public boolean supportsHideOnChange() {
        // starting activity collapses panel anyway
        return false;
    }

    @Override
    public void handleClick() {
        try {
            startActivity(mMainApp.getIntent());
        } catch (Exception e) {
            log(getKey() + ": Unable to start activity: " + e.getMessage());
        }
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        LayoutInflater inflater = LayoutInflater.from(mGbContext);
        View appv = inflater.inflate(R.layout.quick_settings_app_dialog, null);
        int count = 0;
        AppInfo lastAppInfo = null;
        for (AppInfo ai : mAppSlots) {
            TextView tv = (TextView) appv.findViewById(ai.getResId());
            if (ai.getValue() == null) {
                tv.setVisibility(View.GONE);
                continue;
            }

            tv.setText(ai.getAppName());
            tv.setTextSize(1, 10);
            tv.setMaxLines(2);
            tv.setEllipsize(TruncateAt.END);
            tv.setCompoundDrawablesWithIntrinsicBounds(null, ai.getAppIcon(), null, null);
            tv.setClickable(true);
            tv.setOnClickListener(mOnSlotClick);
            count++;
            lastAppInfo = ai;
        }

        if (count == 1) {
            try {
                startActivity(lastAppInfo.getIntent());
            } catch (Throwable t) {
                log(getKey() + ": Unable to start activity: " + t.getMessage());
            }
        } else if (count > 1) {
            mDialog = new Dialog(mContext, android.R.style.Theme_Material_Dialog_NoActionBar);
            mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mDialog.setContentView(appv);
            mDialog.setCanceledOnTouchOutside(true);
            mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL);
            int pf = XposedHelpers.getIntField(mDialog.getWindow().getAttributes(), "privateFlags");
            pf |= 0x00000010;
            XposedHelpers.setIntField(mDialog.getWindow().getAttributes(), "privateFlags", pf);
            mDialog.getWindow().clearFlags(LayoutParams.FLAG_DIM_BEHIND);
            mDialog.show();
            mHandler.removeCallbacks(mDismissDialogRunnable);
            mHandler.postDelayed(mDismissDialogRunnable, 4000);
        }
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mMainApp = null;
        if (mAppSlots != null) {
            mAppSlots.clear();
            mAppSlots = null;
        }
        mPm = null;
        mDialog = null;
        mHandler = null;
    }
}
