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
package com.wrbug.gravitybox.nougat.managers;

import java.util.ArrayList;
import java.util.List;

import com.wrbug.gravitybox.nougat.BroadcastSubReceiver;
import com.wrbug.gravitybox.nougat.GravityBox;
import com.wrbug.gravitybox.nougat.GravityBoxService;
import com.wrbug.gravitybox.nougat.Utils;
import com.wrbug.gravitybox.nougat.ledcontrol.QuietHours;
import com.wrbug.gravitybox.nougat.ledcontrol.QuietHoursActivity;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import android.content.Context;
import android.content.Intent;

public class StatusbarQuietHoursManager implements BroadcastSubReceiver {

    private static final Object lock = new Object();
    private static StatusbarQuietHoursManager sManager;

    private Context mContext;
    private XSharedPreferences mPrefs;
    private QuietHours mQuietHours;
    private List<QuietHoursListener> mListeners;

    public interface QuietHoursListener {
        public void onQuietHoursChanged();
        public void onTimeTick();
    }

    protected static StatusbarQuietHoursManager getInstance(Context context) {
        synchronized(lock) {
            if (sManager == null) {
                sManager = new StatusbarQuietHoursManager(context);
            }
            return sManager;
        }
    }

    private StatusbarQuietHoursManager(Context context) {
        mContext = context;
        mListeners = new ArrayList<QuietHoursListener>();

        refreshState();
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_TIME_TICK) ||
                action.equals(Intent.ACTION_TIME_CHANGED) ||
                action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
            notifyTimeTick();
        } else if (action.equals(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED)) {
            refreshState();
            notifyQuietHoursChange();
        }
    }

    public void registerListener(QuietHoursListener listener) {
        if (listener == null) return;

        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void unregisterListener(QuietHoursListener listener) {
        if (listener == null) return;

        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    private void refreshState() {
        try {
            if (mPrefs == null) {
                mPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "quiet_hours");
            } else {
                mPrefs.reload();
            }
            mQuietHours = new QuietHours(mPrefs);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void notifyTimeTick() {
        for (QuietHoursListener l : mListeners) {
            l.onTimeTick();
        }
    }

    private void notifyQuietHoursChange() {
        for (QuietHoursListener l : mListeners) {
            l.onQuietHoursChanged();
        }
    }

    public QuietHours getQuietHours() {
        return mQuietHours;
    }

    public void setMode(QuietHours.Mode mode) {
        try {
            Context gbContext = Utils.getGbContext(mContext);
            Intent intent = new Intent(gbContext, GravityBoxService.class);
            intent.setAction(QuietHoursActivity.ACTION_SET_QUIET_HOURS_MODE);
            intent.putExtra(QuietHoursActivity.EXTRA_QH_MODE, mode.toString());
            gbContext.startService(intent);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
