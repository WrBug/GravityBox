/*
 * Copyright (C) 2011 The Android Open Source Project
 * Modifications Copyright (C) The OmniROM Project
 * Modifications Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
 *
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
 *
 * Per article 5 of the Apache 2.0 License, some modifications to this code
 * were made by the OmniROM Project.
 *
 * Modifications Copyright (C) 2013 The OmniROM Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.wrbug.gravitybox.nougat;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.widget.Toast;

import com.wrbug.gravitybox.nougat.util.SharedPreferencesUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScreenRecordingService extends Service {
    private static final String TAG = "GB:ScreenRecordingService";

    private static final int SCREENRECORD_NOTIFICATION_ID = 3;
    private static final int MSG_TASK_ENDED = 1;
    private static final int MSG_TASK_ERROR = 2;
    private static final String TMP_PATH = Environment.getExternalStorageDirectory() + "/__tmp_screenrecord.mp4";

    public static final String ACTION_SCREEN_RECORDING_START = "gravitybox.intent.action.SCREEN_RECORDING_START";
    public static final String ACTION_SCREEN_RECORDING_STOP = "gravitybox.intent.action.SCREEN_RECORDING_STOP";
    public static final String ACTION_TOGGLE_SCREEN_RECORDING = "gravitybox.intent.action.TOGGLE_SCREEN_RECORDING";
    public static final String ACTION_SCREEN_RECORDING_STATUS_CHANGED = "gravitybox.intent.action.SCREEN_RECORDING_STATUS_CHANGED";
    private static final String ACTION_TOGGLE_SHOW_TOUCHES = "gravitybox.intent.action.SCREEN_RECORDING_TOGGLE_SHOW_TOUCHES";
    public static final String EXTRA_RECORDING_STATUS = "recordingStatus";
    public static final String EXTRA_STATUS_MESSAGE = "statusMessage";

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_RECORDING = 1;
    public static final int STATUS_PROCESSING = 2;
    public static final int STATUS_ERROR = -1;

    private Handler mHandler;
    private Notification mRecordingNotif;
    private int mRecordingStatus;
    private int mShowTouchesDefault = 0;
    private SharedPreferences mPrefs;
    private boolean mUseStockBinary;

    private CaptureThread mCaptureThread;

    private class CaptureThread extends Thread {
        public void run() {
            try {
                // Firstly, make sure we are able to get to pid field of ProcessImpl class
                final Class<?> classProcImpl = Class.forName("java.lang.UNIXProcess");
                final Field fieldPid = classProcImpl.getDeclaredField("pid");
                fieldPid.setAccessible(true);

                // choose screenrecord binary and prepare command
                List<String> command = new ArrayList<String>();
                command.add(getBinaryPath());
//                if (!mUseStockBinary && 
//                        mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_SCREENRECORD_MICROPHONE, true)) {
//                    command.add("--microphone");
//                }
                String prefVal = mPrefs.getString(GravityBoxSettings.PREF_KEY_SCREENRECORD_SIZE, "default");
                if (!prefVal.equals("default")) {
                    command.add("--size");
                    command.add(prefVal);
                }
                prefVal = String.valueOf(mPrefs.getInt(GravityBoxSettings.PREF_KEY_SCREENRECORD_BITRATE, 4) * 1000000);
                command.add("--bit-rate");
                command.add(prefVal);
                if (!mUseStockBinary) {
                    prefVal = String.valueOf(mPrefs.getInt(GravityBoxSettings.PREF_KEY_SCREENRECORD_TIMELIMIT, 3) * 60);
                    command.add("--time-limit");
                    command.add(prefVal);
                }
                if (mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_SCREENRECORD_ROTATE, false)) {
                    command.add("--rotate");
                }

                command.add(TMP_PATH);

                // construct and start the process
                ProcessBuilder pb = new ProcessBuilder();
                pb.command(command);
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                // Get process PID to be used with native kill later
                final int pid = fieldPid.getInt(proc);
                Log.d(TAG, "Screenrecord PID = " + pid);

                BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));

                while (!isInterrupted()) {
                    if (br.ready()) {
                        Log.d(TAG, br.readLine());
                    }

                    try {
                        int code = proc.exitValue();

                        // If the recording is still running, we won't reach here,
                        // but will land in the catch block below.
                        Message msg = Message.obtain(mHandler, MSG_TASK_ENDED, code, 0, null);
                        mHandler.sendMessage(msg);

                        // No need to stop the process, so we can exit this method early
                        return;
                    } catch (IllegalThreadStateException ignore) {
                        // ignored
                    }
                }

                // Terminate the recording process
                Runtime.getRuntime().exec(new String[]{"kill", "-2", String.valueOf(pid)});
            } catch (IOException e) {
                // Notify something went wrong
                Message msg = Message.obtain(mHandler, MSG_TASK_ERROR, 0, 0, e.getMessage());
                mHandler.sendMessage(msg);

                // Log the error as well
                Log.e(TAG, "Error while starting the screenrecord process", e);
            } catch (NoSuchFieldException e) {
                Message msg = Message.obtain(mHandler, MSG_TASK_ERROR, 0, 0, e.getMessage());
                mHandler.sendMessage(msg);
                Log.e(TAG, "Error while starting the screenrecord process", e);
            } catch (IllegalArgumentException e) {
                Message msg = Message.obtain(mHandler, MSG_TASK_ERROR, 0, 0, e.getMessage());
                mHandler.sendMessage(msg);
                Log.e(TAG, "Error while starting the screenrecord process", e);
            } catch (IllegalAccessException e) {
                Message msg = Message.obtain(mHandler, MSG_TASK_ERROR, 0, 0, e.getMessage());
                mHandler.sendMessage(msg);
                Log.e(TAG, "Error while starting the screenrecord process", e);
            } catch (ClassNotFoundException e) {
                Message msg = Message.obtain(mHandler, MSG_TASK_ERROR, 0, 0, e.getMessage());
                mHandler.sendMessage(msg);
                Log.e(TAG, "Error while starting the screenrecord process", e);
            }
        }
    }

    ;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        final String prefsName = getPackageName() + "_preferences";
        mPrefs = SharedPreferencesUtils.getSharedPreferences(this, prefsName);

        mHandler = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what == MSG_TASK_ENDED) {
                    // The screenrecord process stopped, act as if user
                    // requested the record to stop.
                    stopScreenrecord();
                } else if (msg.what == MSG_TASK_ERROR) {
                    mCaptureThread = null;
                    updateStatus(STATUS_ERROR, (String) msg.obj);
                    Toast.makeText(ScreenRecordingService.this,
                            R.string.screenrecord_toast_error, Toast.LENGTH_SHORT).show();
                }
            }
        };

        mRecordingStatus = STATUS_IDLE;

        Notification.Builder builder = new Notification.Builder(this)
                .setTicker(getString(R.string.screenrecord_notif_ticker))
                .setContentTitle(getString(R.string.screenrecord_notif_title))
                .setSmallIcon(R.drawable.ic_sysbar_camera)
                .setWhen(System.currentTimeMillis());

        Intent stopIntent = new Intent(this, ScreenRecordingService.class);
        stopIntent.setAction(ACTION_SCREEN_RECORDING_STOP);
        PendingIntent stopPendIntent = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Intent pointerIntent = new Intent(this, ScreenRecordingService.class)
                .setAction(ACTION_TOGGLE_SHOW_TOUCHES);
        PendingIntent pointerPendIntent = PendingIntent.getService(this, 0, pointerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        builder
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_media_stop),
                        getString(R.string.screenrecord_notif_stop), stopPendIntent).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_text_dot),
                        getString(R.string.screenrecord_notif_pointer), pointerPendIntent).build());

        mRecordingNotif = builder.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(ACTION_SCREEN_RECORDING_START)) {
                startScreenrecord();
            } else if (intent.getAction().equals(ACTION_SCREEN_RECORDING_STOP)) {
                stopScreenrecord();
            } else if (intent.getAction().equals(ACTION_TOGGLE_SCREEN_RECORDING)) {
                toggleScreenrecord();
            } else if (intent.getAction().equals(ACTION_TOGGLE_SHOW_TOUCHES)) {
                toggleShowTouches();
            }
        } else {
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (isRecording()) {
            stopScreenrecord();
        }
        super.onDestroy();
    }

    private boolean isIdle() {
        return (mRecordingStatus == STATUS_IDLE);
    }

    private boolean isRecording() {
        return (mRecordingStatus == STATUS_RECORDING);
    }

    private boolean isProcessing() {
        return (mRecordingStatus == STATUS_PROCESSING);
    }

    private void updateStatus(int status, String message) {
        mRecordingStatus = status;
        if (isRecording()) {
            startForeground(SCREENRECORD_NOTIFICATION_ID, mRecordingNotif);
        } else {
            stopForeground(true);
            resetShowTouches();
        }

        Intent intent = new Intent(ACTION_SCREEN_RECORDING_STATUS_CHANGED);
        intent.putExtra(EXTRA_RECORDING_STATUS, mRecordingStatus);
        if (message != null) {
            intent.putExtra(EXTRA_STATUS_MESSAGE, message);
        }
        sendBroadcast(intent);
    }

    private void updateStatus(int status) {
        updateStatus(status, null);
    }

    private void toggleShowTouches() {
        sendBroadcast(new Intent(ModHwKeys.ACTION_TOGGLE_SHOW_TOUCHES));
    }

    private void resetShowTouches() {
        Intent intent = new Intent(ModHwKeys.ACTION_TOGGLE_SHOW_TOUCHES);
        intent.putExtra(ModHwKeys.EXTRA_SHOW_TOUCHES, mShowTouchesDefault);
        sendBroadcast(intent);
    }

    private void toggleScreenrecord() {
        if (isRecording()) {
            stopScreenrecord();
        } else {
            startScreenrecord();
        }
    }

    private boolean isScreenrecordSupported() {
        // Exynos devices are currently known to have issues
        if (Utils.isExynosDevice()) {
            Log.e(TAG, "isScreenrecordSupported: screen recording not supported on Exynos devices");
        }
        // check if screenrecord and kill binaries exist and are executable
        File f = new File(getBinaryPath());
        final boolean scrBinaryOk = f.exists() && f.canExecute();
        if (!scrBinaryOk) {
            Log.e(TAG, "isScreenrecordSupported: screenrecord binary doesn't exist or is not executable");
        }
        f = new File("/system/bin/kill");
        final boolean killBinaryOk = f.exists() && f.canExecute();
        if (!killBinaryOk) {
            Log.e(TAG, "isScreenrecordSupported: kill binary doesn't exist or is not executable");
        }
        return (!Utils.isExynosDevice() && scrBinaryOk && killBinaryOk);
    }

    private void startScreenrecord() {
        mUseStockBinary = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_SCREENRECORD_USE_STOCK, false);
        if (!isScreenrecordSupported()) {
            Log.e(TAG, "startScreenrecord: System does not support screen recording");
            Toast.makeText(this, "Your system does not support screen recording", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isRecording()) {
            Log.e(TAG, "startScreenrecord: Recording is already running, ignoring screenrecord start request");
            return;
        } else if (isProcessing()) {
            Log.e(TAG, "startScreenrecord: Previous recording is still being processed, " +
                    "ignoring screenrecord start request");
            Toast.makeText(this, R.string.screenrecord_toast_processing, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            mShowTouchesDefault = Settings.System.getInt(getContentResolver(),
                    ModHwKeys.SETTING_SHOW_TOUCHES);
            if (mShowTouchesDefault == 0) {
                toggleShowTouches();
            }
        } catch (SettingNotFoundException e) {
            //
        }
        mCaptureThread = new CaptureThread();
        mCaptureThread.start();
        updateStatus(STATUS_RECORDING);
    }

    private void stopScreenrecord() {
        if (!isRecording()) {
            Log.e(TAG, "Cannot stop recording that's not active");
            return;
        }

        updateStatus(STATUS_PROCESSING);

        try {
            mCaptureThread.interrupt();
        } catch (Exception e) { /* ignore */ }

        // Wait a bit for capture thread to finish
        while (mCaptureThread.isAlive()) {
            // wait...
        }

        // Give a second to screenrecord to process the file
        mHandler.postDelayed(new Runnable() {
            public void run() {
                mCaptureThread = null;

                String fileName = "SCR_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";

                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                if (!picturesDir.exists()) {
                    if (!picturesDir.mkdir()) {
                        Log.e(TAG, "Cannot create Pictures directory");
                        return;
                    }
                }

                File screenrecord = new File(picturesDir, "Screenrecord");
                if (!screenrecord.exists()) {
                    if (!screenrecord.mkdir()) {
                        Log.e(TAG, "Cannot create Screenrecord directory");
                        return;
                    }
                }

                File input = new File(TMP_PATH);
                final File output = new File(screenrecord, fileName);

                Log.d(TAG, "Copying file to " + output.getAbsolutePath());

                try {
                    copyFileUsingStream(input, output);
                    input.delete();
                    Toast.makeText(ScreenRecordingService.this,
                            String.format(getString(R.string.screenrecord_toast_saved),
                                    output.getPath()), Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Log.e(TAG, "Unable to copy output file", e);
                    Toast.makeText(ScreenRecordingService.this,
                            R.string.screenrecord_toast_save_error, Toast.LENGTH_SHORT).show();
                }

                // Make it appear in gallery, run MediaScanner
                MediaScannerConnection.scanFile(ScreenRecordingService.this,
                        new String[]{output.getAbsolutePath()}, null,
                        new MediaScannerConnection.OnScanCompletedListener() {
                            public void onScanCompleted(String path, Uri uri) {
                                Log.i(TAG, "MediaScanner done scanning " + path);
                            }
                        });

                updateStatus(STATUS_IDLE);
            }
        }, 3000);
    }

    private String getBinaryPath() {
        return (mUseStockBinary ? "/system/bin/screenrecord" : getFilesDir() + "/screenrecord");
    }

    private static void copyFileUsingStream(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            if (is != null) {
                is.close();
            }
            if (os != null) {
                os.close();
            }
        }
    }
}
