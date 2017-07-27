/*
 * Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.wrbug.gravitybox.nougat.ledcontrol.QuietHours;
import com.wrbug.gravitybox.nougat.ledcontrol.QuietHoursActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.view.Surface;
import android.view.WindowManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModAudio {
    private static final String TAG = "GB:ModAudio";
    private static final String CLASS_AUDIO_SERVICE = "com.android.server.audio.AudioService";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static boolean mVolForceMusicControl;
    private static boolean mSwapVolumeKeys;
    private static HandleChangeVolume mHandleChangeVolume;
    private static XSharedPreferences mQhPrefs;
    private static QuietHours mQh;
    private static boolean mVolumesLinked;
    private static Object mAudioService;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_VOL_FORCE_MUSIC_CONTROL_CHANGED)) {
                mVolForceMusicControl = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_VOL_FORCE_MUSIC_CONTROL, false);
                if (DEBUG) log("Force music volume control set to: " + mVolForceMusicControl);
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_VOL_SWAP_KEYS_CHANGED)) {
                mSwapVolumeKeys = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VOL_SWAP_KEYS, false);
                if (DEBUG) log("Swap volume keys set to: " + mSwapVolumeKeys);
            }
            else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_LINK_VOLUMES_CHANGED)) {
                mVolumesLinked = intent.getBooleanExtra(GravityBoxSettings.EXTRA_LINKED, true);
                if (DEBUG) log("mVolumesLinked set to: " + mVolumesLinked);
                updateStreamVolumeAlias();
            } else if (intent.getAction().equals(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED) &&
                    mQhPrefs != null) {
                mQhPrefs.reload();
                mQh = new QuietHours(mQhPrefs);
            }
        }
    };

    public static void initAndroid(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> classAudioService = XposedHelpers.findClass(CLASS_AUDIO_SERVICE, classLoader);

            mQhPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "quiet_hours");
            mQhPrefs.makeWorldReadable();
            mQh = new QuietHours(mQhPrefs);

            mSwapVolumeKeys = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOL_SWAP_KEYS, false);
            mVolumesLinked = prefs.getBoolean(GravityBoxSettings.PREF_KEY_LINK_VOLUMES, true);

            XposedBridge.hookAllConstructors(classAudioService, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS, false)) {
                        int[] maxStreamVolume = (int[])
                                XposedHelpers.getStaticObjectField(classAudioService, "MAX_STREAM_VOLUME");
                        maxStreamVolume[AudioManager.STREAM_MUSIC] = prefs.getInt(
                                GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS_VALUE, 30);
                        if (DEBUG) log("MAX_STREAM_VOLUME for music stream set to " + 
                                maxStreamVolume[AudioManager.STREAM_MUSIC]);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mAudioService = param.thisObject;
                    Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    if (context != null) {
                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_VOL_FORCE_MUSIC_CONTROL_CHANGED);
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_VOL_SWAP_KEYS_CHANGED);
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_LINK_VOLUMES_CHANGED);
                        intentFilter.addAction(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED);
                        context.registerReceiver(mBroadcastReceiver, intentFilter);
                        if (DEBUG) log("AudioService constructed. Broadcast receiver registered");

                        mHandleChangeVolume = new HandleChangeVolume(context);
                        XposedHelpers.findAndHookMethod(classAudioService, "adjustStreamVolume", 
                                int.class, int.class, int.class, String.class, String.class, int.class,
                                mHandleChangeVolume);
                    }
                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS, false)) {
                        XposedHelpers.setIntField(param.thisObject, "mSafeMediaVolumeIndex", 150);
                        if (DEBUG) log("Default mSafeMediaVolumeIndex set to 150");
                    }
                }
            });

            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS, false)) {
                XposedHelpers.findAndHookMethod(classAudioService, "onConfigureSafeVolume",
                        boolean.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setObjectExtra("gbCurSafeMediaVolIndex",
                                XposedHelpers.getIntField(param.thisObject, "mSafeMediaVolumeIndex"));
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if ((Integer) param.getObjectExtra("gbCurSafeMediaVolIndex") !=
                                XposedHelpers.getIntField(param.thisObject, "mSafeMediaVolumeIndex")) {
                            int safeMediaVolIndex = XposedHelpers.getIntField(param.thisObject, "mSafeMediaVolumeIndex") * 2;
                            XposedHelpers.setIntField(param.thisObject, "mSafeMediaVolumeIndex", safeMediaVolIndex);
                            if (DEBUG) log("onConfigureSafeVolume: mSafeMediaVolumeIndex set to " + safeMediaVolIndex);
                        }
                    }
                });
            }

            mVolForceMusicControl = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_VOL_FORCE_MUSIC_CONTROL, false);
            XposedHelpers.findAndHookMethod(classAudioService, "getActiveStreamType",
                    int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mVolForceMusicControl) {
                        int activeStreamType = (int) param.getResult();
                        if (activeStreamType == AudioManager.STREAM_RING ||
                                activeStreamType == AudioManager.STREAM_NOTIFICATION) {
                            param.setResult(AudioManager.STREAM_MUSIC);
                            if (DEBUG) log("getActiveStreamType: Forcing STREAM_MUSIC");
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classAudioService, "playSoundEffectVolume",
                    int.class, float.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mQh.isSystemSoundMuted(QuietHours.SystemSound.TOUCH)) {
                        param.setResult(false);
                    }
                } 
            });

            XposedHelpers.findAndHookMethod(classAudioService, "updateStreamVolumeAlias",
                    boolean.class, String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if ((Boolean) XposedHelpers.callMethod(param.thisObject, "isPlatformVoice")) {
                        int[] streamVolumeAlias = (int[]) XposedHelpers.getObjectField(param.thisObject, "mStreamVolumeAlias");
                        streamVolumeAlias[AudioManager.STREAM_NOTIFICATION] = mVolumesLinked ? 
                                AudioManager.STREAM_RING : AudioManager.STREAM_NOTIFICATION;
                        XposedHelpers.setObjectField(param.thisObject, "mStreamVolumeAlias", streamVolumeAlias);
                        if (DEBUG) log("AudioService mStreamVolumeAlias updated, STREAM_NOTIFICATION set to: " + 
                                streamVolumeAlias[AudioManager.STREAM_NOTIFICATION]);
                    }
                }
            });
        } catch(Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void updateStreamVolumeAlias() {
        if (mAudioService == null) {
            if (DEBUG) log("updateStreamVolumeAlias: AudioService is null");
            return;
        }

        try {
            XposedHelpers.callMethod(mAudioService, "updateStreamVolumeAlias", true, "AudioService");
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static class HandleChangeVolume extends XC_MethodHook {
        private static List<String> sBlackList = new ArrayList<>(Arrays.asList(
                "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro"));

        private static boolean isPkgInBlackList(String pkg) {
            final boolean isInBlackList = sBlackList.contains(pkg);
            if (DEBUG) log(pkg + " blacklisted: " + isInBlackList);
            return isInBlackList;
        }

        private WindowManager mWm;

        public HandleChangeVolume(Context context) {
            mWm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            if (mSwapVolumeKeys && !isPkgInBlackList((String)param.args[3])) {
                try {
                    if ((Integer) param.args[1] != 0) {
                        if (DEBUG) log("Original direction = " + param.args[1]);
                        int orientation = getDirectionFromOrientation();
                        param.args[1] = orientation * (Integer) param.args[1];
                        if (DEBUG) log("Modified direction = " + param.args[1]);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        }

        private int getDirectionFromOrientation() {
            int rotation = mWm.getDefaultDisplay().getRotation();
            switch (rotation) {
                case Surface.ROTATION_0:
                    if (DEBUG) log("Rotation = 0");
                    return 1;
                case Surface.ROTATION_90:
                    if (DEBUG) log("Rotation = 90");
                    return -1;
                case Surface.ROTATION_180:
                    if (DEBUG) log("Rotation = 180");
                    return -1;
                case Surface.ROTATION_270:
                default:
                    if (DEBUG) log("Rotation = 270");
                    return 1;
            }
        }
    }
}
