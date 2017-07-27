/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wrbug.gravitybox.nougat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.service.notification.StatusBarNotification;
import android.widget.RemoteViews;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ProgressBarController implements BroadcastSubReceiver {
    private static final String TAG = "GB:ProgressBarController";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final long MAX_IDLE_TIME = 10000; // ms
    private static final int IDLE_CHECK_FREQUENCY = 5000; // ms

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static final List<String> SUPPORTED_PACKAGES = new ArrayList<String>(Arrays.asList(
            "com.android.providers.downloads",
            "com.android.bluetooth",
            "com.mediatek.bluetooth"
    ));

    public interface ProgressStateListener {
        void onProgressAdded(ProgressInfo pi);
        void onProgressUpdated(ProgressInfo pInfo);
        void onProgressRemoved(String id);
        void onProgressTrackingStarted(Mode mode);
        void onProgressTrackingStopped();
        void onProgressModeChanged(Mode mode);
        void onProgressPreferencesChanged(Intent intent);
    }

    public class ProgressInfo {
        String id;
        boolean hasProgressBar;
        int progress;
        int max;
        long lastUpdatedMs;

        public ProgressInfo(String id, boolean hasProgressBar, int progress, int max) {
            this.id = id;
            this.hasProgressBar = hasProgressBar;
            this.progress = progress;
            this.max = max;
            this.lastUpdatedMs = System.currentTimeMillis();
        }

        public float getFraction() {
            return (max > 0 ? ((float)progress/(float)max) : 0f);
        }

        boolean isIdle() {
            long idleTime = (System.currentTimeMillis() - this.lastUpdatedMs);
            if (DEBUG) log("ProgressInfo: '" + this.id + 
                    "' is idle for " + idleTime + "ms");
            return (idleTime > MAX_IDLE_TIME);
        }
    }

    public enum Mode { OFF, TOP, BOTTOM };

    private Context mContext;
    private List<ProgressStateListener> mListeners;
    private Mode mMode;
    private Map<String, ProgressInfo> mProgressList;
    private boolean mSoundEnabled;
    private String mSoundUri;
    private boolean mSoundWhenScreenOffOnly;
    private PowerManager mPowerManager;
    private Handler mHandler;

    private Runnable mRemoveIdleRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mProgressList) {
                List<String> toRemove = new ArrayList<>();
                for (ProgressInfo pi : mProgressList.values())
                    if (pi.isIdle()) toRemove.add(pi.id);
                for (String id : toRemove)
                    removeProgress(id, false);
                if (mProgressList.size() > 0) {
                    mHandler.postDelayed(this, IDLE_CHECK_FREQUENCY);
                }
            }
        }
    };

    public ProgressBarController(Context ctx, XSharedPreferences prefs) {
        mContext = ctx;
        mListeners = new ArrayList<ProgressStateListener>();
        mProgressList = new LinkedHashMap<String, ProgressInfo>();

        mMode = Mode.valueOf(prefs.getString(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS, "OFF"));
        mSoundEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_SOUND_ENABLE, false);
        mSoundUri = prefs.getString(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_SOUND,
                "content://settings/system/notification_sound");
        mSoundWhenScreenOffOnly = prefs.getBoolean(
                GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_SOUND_SCREEN_OFF, false);

        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mHandler = new Handler();
    }

    public void registerListener(ProgressStateListener listener) {
        if (listener == null) return;
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            }
        }
    }

    public void unregisterListener(ProgressStateListener listener) {
        if (listener == null) return;
        synchronized (mListeners) {
            if (mListeners.contains(listener)) {
                mListeners.remove(listener);
            }
        }
    }

    private void notifyProgressTrackingStarted() {
        synchronized (mListeners) {
            for (ProgressStateListener l : mListeners) {
                l.onProgressTrackingStarted(mMode);
            }
        }
    }

    private void notifyProgressTrackingStopped() {
        synchronized (mListeners) {
            for (ProgressStateListener l : mListeners) {
                l.onProgressTrackingStopped();
            }
        }
    }

    private void notifyProgressAdded(ProgressInfo pi) {
        synchronized (mListeners) {
            for (ProgressStateListener l : mListeners) {
                l.onProgressAdded(pi);
            }
        }
    }

    private void notifyProgressUpdated(ProgressInfo pInfo) {
        synchronized (mListeners) {
            for (ProgressStateListener l : mListeners) {
                l.onProgressUpdated(pInfo);
            }
        }
    }

    private void notifyProgressRemoved(String id) {
        synchronized (mListeners) {
            for (ProgressStateListener l : mListeners) {
                l.onProgressRemoved(id);
            }
        }
    }

    private void notifyModeChanged() {
        synchronized (mListeners) {
            for (ProgressStateListener l : mListeners) {
                l.onProgressModeChanged(mMode);
            }
        }
    }

    private void notifyPreferencesChanged(Intent intent) {
        synchronized (mListeners) {
            for (ProgressStateListener l : mListeners) {
                l.onProgressPreferencesChanged(intent);
            }
        }
    }

    private void addProgress(ProgressInfo pi) {
        synchronized (mProgressList) {
            if (!mProgressList.containsKey(pi.id)) {
                mProgressList.put(pi.id, pi);
                if (DEBUG) log("addProgress: added progress for '" + pi.id + "'");
                if (mProgressList.size() == 1) {
                    notifyProgressTrackingStarted();
                    resetIdleChecker();
                }
                notifyProgressAdded(pi);
            } else if (DEBUG) {
                log("addProgress: progress for '" + pi.id + "' already exists");
            }
        }
    }

    private void removeProgress(String id, boolean allowSound) {
        synchronized (mProgressList) {
            if (id == null) {
                mProgressList.clear();
                if (DEBUG) log("removeProgress: all cleared");
            } else if (mProgressList.containsKey(id)) {
                mProgressList.remove(id);
                notifyProgressRemoved(id);
                if (DEBUG) log("removeProgress: removed progress for '" + id + "'");
                if (allowSound) maybePlaySound();
            }
            if (mProgressList.size() == 0) {
                notifyProgressTrackingStopped();
                resetIdleChecker();
            }
        }
    }

    private void updateProgress(String id, int max, int progress) {
        ProgressInfo pi = mProgressList.get(id);
        if (pi != null) {
            pi.max = max;
            pi.progress = progress;
            pi.lastUpdatedMs = System.currentTimeMillis();
            if (DEBUG) {
                log("updateProgress: updated progress for '" + id + "': " +
                        "max=" + max + "; progress=" + progress);
            }
            notifyProgressUpdated(pi);
        }
    }

    private void resetIdleChecker() {
        mHandler.removeCallbacks(mRemoveIdleRunnable);
        if (mProgressList.size() > 0) {
            mHandler.postDelayed(mRemoveIdleRunnable, IDLE_CHECK_FREQUENCY);
        }
    }

    public Map<String, ProgressInfo> getList() {
        return Collections.unmodifiableMap(mProgressList);
    }

    public void onNotificationAdded(StatusBarNotification statusBarNotif) {
        if (mMode == Mode.OFF) return;

        ProgressInfo pi = verifyNotification(statusBarNotif);
        if (pi == null) {
            if (DEBUG) log("onNotificationAdded: ignoring unsupported notification");
            return;
        }

        addProgress(pi);
    }

    public void onNotificationUpdated(StatusBarNotification statusBarNotif) {
        if (mMode == Mode.OFF) return;

        ProgressInfo pi = verifyNotification(statusBarNotif);
        if (pi == null) {
            String id = getIdentifier(statusBarNotif);
            if (id != null && mProgressList.containsKey(id)) {
                removeProgress(id, true);
                if (DEBUG) log("onNotificationUpdated: removing no longer " +
                        "supported notification for '" + id + "'");
            } else if (DEBUG) {
                log("onNotificationUpdated: ignoring unsupported notification");
            }
            return;
        }

        if (!mProgressList.containsKey(pi.id)) {
            // treat it as if it was added, e.g. to show progress in case
            // feature has been enabled during already ongoing download
            addProgress(pi);
        } else {
            updateProgress(pi.id, pi.max, pi.progress);
        }
    }

    public void onNotificationRemoved(StatusBarNotification statusBarNotif) {
        if (mMode == Mode.OFF) return;

        String id = getIdentifier(statusBarNotif);
        if (id != null && mProgressList.containsKey(id)) {
            removeProgress(id, true);
        }
    }

    private ProgressInfo verifyNotification(StatusBarNotification statusBarNotif) {
        if (statusBarNotif == null)
            return null;

        String id = getIdentifier(statusBarNotif);
        if (id == null)
            return null;

        Notification n = statusBarNotif.getNotification();
        if (n != null && 
               (SUPPORTED_PACKAGES.contains(statusBarNotif.getPackageName()) ||
                n.extras.getBoolean(ModLedControl.NOTIF_EXTRA_PROGRESS_TRACKING))) {
            ProgressInfo pi = getProgressInfo(id, n);
            if (pi != null && pi.hasProgressBar)
                return pi;
        }
        return null;
    }

    private String getIdentifier(StatusBarNotification statusBarNotif) {
        if (statusBarNotif == null) return null;
        String pkgName = statusBarNotif.getPackageName();
        if (SUPPORTED_PACKAGES.get(0).equals(pkgName)) {
            String tag = statusBarNotif.getTag();
            if (tag != null && tag.contains(":")) {
                return pkgName + ":" + tag.substring(tag.indexOf(":")+1);
            }
            if (DEBUG) log("getIdentifier: Unexpected notification tag: " + tag);
        } else {
            return (pkgName + ":" + String.valueOf(statusBarNotif.getId()));
        }
        return null;
    }

    private ProgressInfo getProgressInfo(String id, Notification n) {
        if (id == null || n == null) return null;

        ProgressInfo pInfo = new ProgressInfo(id, false, 0, 0);

        // We have to extract the information from the content view
        RemoteViews views = n.bigContentView;
        if (views == null) views = n.contentView;
        if (views == null) return pInfo;

        try {
            @SuppressWarnings("unchecked")
            List<Parcelable> actions = (List<Parcelable>) 
                XposedHelpers.getObjectField(views, "mActions");
            if (actions == null) return pInfo;

            for (Parcelable p : actions) {
                Parcel parcel = Parcel.obtain();
                p.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);

                // The tag tells which type of action it is (2 is ReflectionAction)
                int tag = parcel.readInt();
                if (tag != 2)  {
                    parcel.recycle();
                    continue;
                }

                parcel.readInt(); // skip View ID
                String methodName = parcel.readString();
                if ("setMax".equals(methodName)) {
                    parcel.readInt(); // skip type value
                    pInfo.max = parcel.readInt();
                    if (DEBUG) log("getProgressInfo: total=" + pInfo.max);
                } else if ("setProgress".equals(methodName)) {
                    parcel.readInt(); // skip type value
                    pInfo.progress = parcel.readInt();
                    pInfo.hasProgressBar = true;
                    if (DEBUG) log("getProgressInfo: current=" + pInfo.progress);
                }

                parcel.recycle();
            }
        } catch (Throwable  t) {
            XposedBridge.log(t);
        }

        return pInfo;
    }

    private void maybePlaySound() {
        if (mSoundEnabled &&
                (!mPowerManager.isInteractive() || !mSoundWhenScreenOffOnly)) {
            try {
                final Ringtone sfx = RingtoneManager.getRingtone(mContext,
                        Uri.parse(mSoundUri));
                if (sfx != null) {
                    AudioAttributes attrs = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    sfx.setAudioAttributes(attrs);
                    sfx.play();
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_STATUSBAR_DOWNLOAD_PROGRESS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_ENABLED)) {
                mMode = Mode.valueOf(intent.getStringExtra(
                        GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_ENABLED));
                notifyModeChanged();
                if (mMode == Mode.OFF) {
                    removeProgress(null, false);
                }
            } else if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_SOUND_ENABLE)) {
                mSoundEnabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_SOUND_ENABLE, false);
            } else if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_SOUND)) {
                mSoundUri = intent.getStringExtra(
                            GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_SOUND);
            } else if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_SOUND_SCREEN_OFF)) {
                mSoundWhenScreenOffOnly = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_SOUND_SCREEN_OFF, false);
            } else {
                notifyPreferencesChanged(intent);
            }
        }
    }
}
