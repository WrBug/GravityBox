/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.CallLog;
import android.service.notification.StatusBarNotification;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.wrbug.gravitybox.nougat.managers.KeyguardStateMonitor;
import com.wrbug.gravitybox.nougat.managers.NotificationDataMonitor;
import com.wrbug.gravitybox.nougat.managers.SysUiManagers;
import com.wrbug.gravitybox.nougat.preference.AppPickerPreference;
import com.wrbug.gravitybox.nougat.shortcuts.ShortcutActivity;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class LockscreenAppBar implements KeyguardStateMonitor.Listener,
                                         NotificationDataMonitor.Listener {
    private static final String TAG = "GB:LockscreenAppBar";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private Context mContext;
    private Context mGbContext;
    private ViewGroup mContainer;
    private Object mStatusBar;
    private PackageManager mPm;
    private List<AppInfo> mAppSlots;
    private ViewGroup mRootView;
    private XSharedPreferences mPrefs;
    private boolean mSafeLaunchEnabled;
    private boolean mShowBadges;
    private AppInfo mPendingAction;
    private Handler mHandler;
    private KeyguardStateMonitor mKgMonitor;
    private NotificationDataMonitor mNdMonitor;

    public LockscreenAppBar(Context ctx, Context gbCtx, ViewGroup container,
            Object statusBar, XSharedPreferences prefs) {
        mContext = ctx;
        mGbContext = gbCtx;
        mContainer = container;
        mStatusBar = statusBar;
        mPrefs = prefs;
        mPm = mContext.getPackageManager();
        mHandler = new Handler();
        mSafeLaunchEnabled = prefs.getBoolean(
                GravityBoxSettings.PREF_KEY_LOCKSCREEN_SHORTCUT_SAFE_LAUNCH, false);
        mShowBadges = prefs.getBoolean(
                GravityBoxSettings.PREF_KEY_LOCKSCREEN_SHORTCUT_SHOW_BADGES, false);

        mNdMonitor = SysUiManagers.NotifDataMonitor;
        if (mNdMonitor != null) {
            mNdMonitor.registerListener(this);
        }

        initAppSlots();

        mKgMonitor = SysUiManagers.KeyguardMonitor;
        mKgMonitor.registerListener(this);
    }

    private void initAppSlots() {
        LayoutInflater inflater = LayoutInflater.from(mGbContext);
        mRootView = (ViewGroup) inflater.inflate(R.layout.lockscreen_app_bar, mContainer, false);
        mContainer.addView(mRootView);

        mAppSlots = new ArrayList<AppInfo>(6);
        mAppSlots.add(new AppInfo(R.id.quickapp1));
        mAppSlots.add(new AppInfo(R.id.quickapp2));
        mAppSlots.add(new AppInfo(R.id.quickapp3));
        mAppSlots.add(new AppInfo(R.id.quickapp4));
        mAppSlots.add(new AppInfo(R.id.quickapp5));
        mAppSlots.add(new AppInfo(R.id.quickapp6));

        for (int i = 0; i < GravityBoxSettings.PREF_KEY_LOCKSCREEN_SHORTCUT.size(); i++) {
            updateAppSlot(i, mPrefs.getString(GravityBoxSettings.PREF_KEY_LOCKSCREEN_SHORTCUT.get(i),
                    null), false);
        }
        updateRootViewVisibility();
    }

    public void setSafeLaunchEnabled(boolean enabled) {
        mSafeLaunchEnabled = enabled;
    }

    public void setShowBadges(boolean showBadges) {
        mShowBadges = showBadges;
        onNotificationDataChanged(null);
    }

    public void updateAppSlot(int slot, String value) {
        updateAppSlot(slot, value, true);
    }

    private void updateAppSlot(int slot, String value, boolean updateRootViewVisibility) {
        mAppSlots.get(slot).initAppInfo(value);
        if (updateRootViewVisibility) {
            updateRootViewVisibility();
        }
    }

    private void updateRootViewVisibility() {
        boolean atLeastOneVisible = false;
        int childCount = mRootView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (mRootView.getChildAt(i).getVisibility() == View.VISIBLE) {
                atLeastOneVisible = true;
                break;
            }
        }
        mRootView.setVisibility(atLeastOneVisible ? View.VISIBLE : View.GONE);
        if (Utils.isOxygenOs35Rom()) {
            mContainer.getChildAt(mContainer.getChildCount()-2).setVisibility(
                    mRootView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onKeyguardStateChanged() {
        for (AppInfo ai : mAppSlots) {
            if (ai.isUnsafeAction()) {
                ai.setVisible(!mKgMonitor.isLocked());
            }
            if (ModTelecom.PACKAGE_NAME.equals(ai.getPackageName())) {
                ai.updateIcon();
            }
        }
    }

    @Override
    public void onNotificationDataChanged(final StatusBarNotification sbn) {
        for (AppInfo ai : mAppSlots) {
            if (ai.getPackageName() == null)
                continue;

            if (sbn == null || sbn.getPackageName() == null ||
                    sbn.getPackageName().equals(ai.getPackageName())) {
                ai.updateIcon();
            }
        }
    }

    private void startActivity(Intent intent) {
        // if intent is a GB action of broadcast type, handle it directly here
        if (ShortcutActivity.isGbBroadcastShortcut(intent)) {
            boolean isLaunchBlocked = mKgMonitor.isShowing() && mKgMonitor.isLocked() &&
                    !ShortcutActivity.isActionSafe(intent.getStringExtra(
                            ShortcutActivity.EXTRA_ACTION));
            if (DEBUG) log("isLaunchBlocked: " + isLaunchBlocked);

            if (!isLaunchBlocked) {
                Intent newIntent = new Intent(intent.getStringExtra(ShortcutActivity.EXTRA_ACTION));
                newIntent.putExtras(intent);
                mContext.sendBroadcast(newIntent);
            }
        // otherwise start activity dismissing keyguard
        } else {
            try {
                XposedHelpers.callMethod(mStatusBar, "postStartActivityDismissingKeyguard", intent, 0);
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    }

    private int getMissedCallCount() {
        try {
            String[] selection = { CallLog.Calls.TYPE };
            String where = CallLog.Calls.TYPE + "=" + CallLog.Calls.MISSED_TYPE +
                    " AND " + CallLog.Calls.NEW + "=1";
            Cursor c = mContext.getContentResolver().query(CallLog.Calls.CONTENT_URI, selection, where, null, null);
            return c.getCount();
        } catch (Throwable t) {
            XposedBridge.log(t);
            return 0;
        }
    }

    private Runnable pendingActionExpiredRunnable = new Runnable() {
        @Override
        public void run() {
            if (mPendingAction != null) {
                mPendingAction.zoomOut();
                mPendingAction = null;
            }
        }
    };

    private final class AppInfo implements View.OnClickListener {
        private Intent mIntent;
        private Resources mResources;
        private Resources mGbResources;
        private ImageView mView;
        private Drawable mIcon;

        public AppInfo(int resId) {
            mResources = mContext.getResources();
            mGbResources = mGbContext.getResources();
            mView = (ImageView) mRootView.findViewById(resId);
            mView.setVisibility(View.GONE);
            mView.setOnClickListener(this);
        }

        private void reset() {
            mIntent = null;
            mIcon = null;
            mView.setImageDrawable(null);
            mView.setVisibility(View.GONE);
        }

        public void initAppInfo(String value) {
            reset();
            if (value == null) {
                return;
            }

            try {
                mIntent = Intent.parseUri(value, 0);
                if (!mIntent.hasExtra("mode")) {
                    reset();
                    return;
                }
                final int mode = mIntent.getIntExtra("mode", AppPickerPreference.MODE_APP);

                final int iconResId = mIntent.getStringExtra("iconResName") != null ?
                        mGbResources.getIdentifier(mIntent.getStringExtra("iconResName"),
                        "drawable", mGbContext.getPackageName()) : 0;
                if (iconResId != 0) {
                    mIcon = mGbResources.getDrawable(iconResId, null);
                } else {
                    final String appIconPath = mIntent.getStringExtra("icon");
                    if (appIconPath != null) {
                        File f = new File(appIconPath);
                        if (f.exists() && f.canRead()) {
                            mIcon = Drawable.createFromPath(f.getAbsolutePath());
                        }
                    }
                }

                if (mIcon == null) {
                    if (mode == AppPickerPreference.MODE_APP) {
                        ActivityInfo ai = mPm.getActivityInfo(mIntent.getComponent(), 0);
                        mIcon = ai.loadIcon(mPm);
                    } else {
                        mIcon = mResources.getDrawable(android.R.drawable.ic_menu_help, null);
                    }
                }

                updateIcon();
                mView.setVisibility(View.VISIBLE);
                if (DEBUG) log("AppInfo initialized for: " + mIntent);
            } catch (NameNotFoundException e) {
                log("App not found: " + mIntent);
                reset();
            } catch (Exception e) {
                log("Unexpected error: " + e.getMessage());
                reset();
            }
        }

        private String getPackageName() {
            if (mIntent != null && mIntent.getComponent() != null &&
                    mIntent.getComponent().getPackageName() != null) {
                final String pkgName = mIntent.getComponent().getPackageName();
                return (pkgName.equals(Utils.getDefaultDialerPackageName(mContext)) ?
                        ModTelecom.PACKAGE_NAME : pkgName);
            }
            return null;
        }

        public void updateIcon() {
            if (mIcon == null || mIntent == null) return;

            Drawable d = mIcon;

            final int mode = mIntent.getIntExtra("mode", AppPickerPreference.MODE_APP);
            if (mShowBadges && mode == AppPickerPreference.MODE_APP) {
                int count = getNotifCount();
                if (count > 0) {
                    d = createBadgeDrawable(d, count);
                }
            }

            mView.setImageDrawable(d);
        }

        private int getNotifCount() {
            final String pkgName = getPackageName();
            if (ModTelecom.PACKAGE_NAME.equals(pkgName)) {
                return getMissedCallCount();
            } else if (mNdMonitor != null) {
                return mNdMonitor.getNotifCountFor(pkgName);
            } else {
                return 0;
            }
        }

        private Drawable createBadgeDrawable(Drawable d, int count) {
            if (d == null) return null;

            NumberFormat f = NumberFormat.getIntegerInstance();
            String countStr = count > 99 ? "99+" : f.format(count);

            Bitmap b = Utils.drawableToBitmap(d);
            b = b.copy(Bitmap.Config.ARGB_8888, true);
            Canvas c = new Canvas(b);

            Paint p = new Paint();
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Color.WHITE);
            p.setAntiAlias(true);
            p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10,
                    mResources.getDisplayMetrics()));

            Drawable bg = mGbResources.getDrawable(R.drawable.ic_notification_overlay, null);

            final int w = b.getWidth();
            final int h = b.getHeight();
            final Rect r = new Rect();
            p.getTextBounds(countStr, 0, countStr.length(), r);
            final int tw = r.right - r.left;
            final int th = r.bottom - r.top;
            bg.getPadding(r);
            int dw = r.left + tw + r.right;
            if (dw < bg.getMinimumWidth()) {
                dw = bg.getMinimumWidth();
            }
            int x = w-r.right-((dw-r.right-r.left)/2);
            int dh = r.top + th + r.bottom;
            if (dh < bg.getMinimumHeight()) {
                dh = bg.getMinimumHeight();
            }
            if (dw < dh) dw = dh;
            int y = h-r.bottom-((dh-r.top-th-r.bottom)/2);
            bg.setBounds(w-dw, h-dh, w, h);

            bg.draw(c);
            c.drawText(countStr, x, y, p);

            return new BitmapDrawable(mResources, b);
        }

        public boolean isUnsafeAction() {
            return (mIntent != null &&
                    !ShortcutActivity.isActionSafe(mIntent.getStringExtra(
                            ShortcutActivity.EXTRA_ACTION)));
        }

        public void setVisible(boolean visible) {
            if (mView != null) {
                mView.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        }

        public void zoomIn() {
            if (mView != null) {
                mView.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(100)
                    .start();
            }
        }

        public void zoomOut() {
            if (mView != null) {
                mView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start();
            }
        }

        @Override
        public void onClick(View v) {
            if (mSafeLaunchEnabled) {
                mHandler.removeCallbacks(pendingActionExpiredRunnable);
    
                if (mPendingAction == this) {
                    pendingActionExpiredRunnable.run();
                    if (mIntent != null) {
                        startActivity(mIntent);
                    }
                } else {
                    pendingActionExpiredRunnable.run();
                    mPendingAction = this;
                    zoomIn();
                    mHandler.postDelayed(pendingActionExpiredRunnable, 1300);
                }
            } else {
                if (mIntent != null) {
                    startActivity(mIntent);
                }
            }
        }
    }
}
