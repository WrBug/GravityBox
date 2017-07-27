/*
 * Copyright (C) 2016 Peter Gregus for GravityBox Project (C3C076@xda)
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

import java.security.KeyStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import com.wrbug.gravitybox.nougat.BuildConfig;
import com.wrbug.gravitybox.nougat.Utils;
import com.wrbug.gravitybox.nougat.managers.AppLauncher.AppInfo;
import com.wrbug.gravitybox.nougat.BroadcastSubReceiver;
import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.R;
import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintManager.AuthenticationResult;
import android.hardware.fingerprint.FingerprintManager.CryptoObject;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.Handler;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class FingerprintLauncher implements BroadcastSubReceiver {
    private static final String TAG = "GB:FingerprintLauncher";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String KEY_NAME = "gravitybox.fingeprint.launcher";
    private static final String CLASS_FP_SERVICE_WRAPPER = 
            "com.android.server.fingerprint.FingerprintService.FingerprintServiceWrapper";
    private static final String UID_SYSTEM_UI = "android.uid.systemui";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    // Fingerprint service
    public static final void initAndroid(final ClassLoader classLoader) {
        if (DEBUG) log("service init");

        try {
            XposedHelpers.findAndHookMethod(CLASS_FP_SERVICE_WRAPPER, classLoader,
                    "isRestricted", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object service = XposedHelpers.getSurroundingThis(param.thisObject);
                    Context ctx = (Context) XposedHelpers.getObjectField(service, "mContext");
                    String pkg = ctx.getPackageManager().getNameForUid(Binder.getCallingUid());
                    if (pkg != null) {
                        if (pkg.contains(":")) {
                            pkg = pkg.split(":")[0];
                        }
                        if (DEBUG) log("service: isRestricted: pkg=" + pkg);
                        if (UID_SYSTEM_UI.equals(pkg)) {
                            param.setResult(false);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    // SystemUI
    private Context mContext;
    private Context mGbContext;
    private XSharedPreferences mPrefs;
    private FingerprintManager mFpManager;
    private FingerprintHandler mFpHandler;
    private String mQuickApp;
    private Map<String,String> mFingerAppMap;
    private boolean mIsPaused;
    private boolean mShowToast;

    public FingerprintLauncher(Context ctx, XSharedPreferences prefs) throws Throwable {
        if (ctx == null)
            throw new IllegalArgumentException("Context cannot be null");

        mContext = ctx;
        mGbContext = Utils.getGbContext(mContext);
        mPrefs = prefs;

        mQuickApp = mPrefs.getString(GravityBoxSettings.PREF_KEY_FINGERPRINT_LAUNCHER_APP, null);
        mShowToast = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_FINGERPRINT_LAUNCHER_SHOW_TOAST, true);

        initFingerprintManager();
        initFingerAppMap(prefs);
    }

    private void initFingerprintManager() throws Throwable {
        mFpManager = (FingerprintManager) mContext.getSystemService(Context.FINGERPRINT_SERVICE);
        if (!mFpManager.isHardwareDetected())
            throw new IllegalStateException("Fingerprint hardware not present");

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyStore.load(null);
        keyGenerator.init(new KeyGenParameterSpec.Builder(
                KEY_NAME, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .build());
        keyGenerator.generateKey();

        Cipher cipher = Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_AES + "/" +
                KeyProperties.BLOCK_MODE_CBC + "/" +
                KeyProperties.ENCRYPTION_PADDING_PKCS7);
        SecretKey key = (SecretKey) keyStore.getKey(KEY_NAME, null);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        mFpHandler = new FingerprintHandler(cipher);

        if (DEBUG) log("Fingeprint manager initialized");
    }

    private void initFingerAppMap(XSharedPreferences prefs) {
        mFingerAppMap = new HashMap<>();
        int[] ids = getEnrolledFingerprintIds();
        if (ids != null) {
            for (int i = 0; i < ids.length; i++) {
                String key = GravityBoxSettings.PREF_KEY_FINGERPRINT_LAUNCHER_FINGER +
                        String.valueOf(i);
                String[] data = parseFingerPrefSet(prefs.getStringSet(key, null));
                if (data[0] != null) {
                    mFingerAppMap.put(data[0], data[1]);
                }
            }
        }
    }

    private String[] parseFingerPrefSet(Set<String> set) {
        String[] retVal = new String[2];
        if (set == null) return retVal;
        for (String val : set) {
            String[] data = val.split(":", 2);
            if ("fingerId".equals(data[0])) {
                retVal[0] = data[1];
            } else if ("app".equals(data[0])) {
                retVal[1] = data[1];
            }
        }
        return retVal;
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            onUserPresentChanged(false);
        } else if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
            onUserPresentChanged(true);
        } else if (intent.getAction().equals(GravityBoxSettings.ACTION_FPL_SETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_FPL_APP)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_FPL_FINGER_ID)) {
                    mFingerAppMap.put(intent.getStringExtra(GravityBoxSettings.EXTRA_FPL_FINGER_ID),
                            intent.getStringExtra(GravityBoxSettings.EXTRA_FPL_APP));
                } else {
                    mQuickApp = intent.getStringExtra(GravityBoxSettings.EXTRA_FPL_APP);
                }
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_FPL_PAUSE)) {
                pause();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_FPL_SHOW_TOAST)) {
                mShowToast = intent.getBooleanExtra(GravityBoxSettings.EXTRA_FPL_SHOW_TOAST, true);
            }
        }
    }

    private void onUserPresentChanged(boolean present) {
        if (DEBUG) log("onUserPresentChanged: present=" + present);
        if (present) {
            mIsPaused = false;
            mFpHandler.startListeningDelayed(500);
        } else {
            mFpHandler.stopListening();
        }
    }

    private void pause() {
        if (!mIsPaused) {
            mIsPaused = true;
            mFpHandler.stopListening();
            if (mShowToast) {
                Toast.makeText(mContext, String.format("%s\n%s",
                    TAG, mGbContext.getString(R.string.fingerprint_launcher_paused)),
                    Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startActivity(String fingerId) {
        if (DEBUG) log("starting activity");
        try {
            AppInfo appInfo = SysUiManagers.AppLauncher.createAppInfo();
            if (fingerId != null) {
                if (mFingerAppMap.containsKey(fingerId)) {
                    appInfo.initAppInfo(mFingerAppMap.get(fingerId), false);
                }
            } else {
                appInfo.initAppInfo(mQuickApp, false);
            }
            if (appInfo.getIntent() != null) {
                SysUiManagers.AppLauncher.startActivity(mContext, appInfo.getIntent());
            } else if (mShowToast) {
                Toast.makeText(mContext, String.format("%s\n%s",
                        TAG, mGbContext.getString(R.string.fingerprint_no_app)),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            log("Error starting activity: " + t.getMessage());
        }
    }

    private class FingerprintHandler extends FingerprintManager.AuthenticationCallback {
        private CryptoObject mCryptoObject;
        private CancellationSignal mCancellationSignal;
        private Handler mHandler;

        private FingerprintHandler(Cipher cipher) {
            mCryptoObject = new CryptoObject(cipher);
            mHandler = new Handler();
        }

        private void startListening() {
            if (mCancellationSignal != null) {
                if (DEBUG) log("Already listening");
                return;
            }
            mCancellationSignal = new CancellationSignal();
            mFpManager.authenticate(mCryptoObject, mCancellationSignal, 0, this, null);
            if (DEBUG) log("FingerprintHandler: listening started");
        }

        private void stopListening() {
            mHandler.removeCallbacks(mStartListeningRunnable);
            if (mCancellationSignal != null && !mCancellationSignal.isCanceled()) {
                mCancellationSignal.cancel();
                if (DEBUG) log("FingerprintHandler: listening stopped");
            }
            mCancellationSignal = null;
        }

        private void startListeningDelayed(long delayMs) {
            mHandler.postDelayed(mStartListeningRunnable, delayMs);
        }

        private void restartListeningDelayed(long delayMs) {
            if (DEBUG) log("Restarting listening in " + delayMs + "ms");

            stopListening();
            mHandler.postDelayed(mStartListeningRunnable, delayMs);
        }

        private Runnable mStartListeningRunnable = new Runnable() {
            @Override
            public void run() {
                startListening();
            }
        };

        @Override
        public void onAuthenticationError(int errMsgId, CharSequence errString) {
            if (DEBUG) log("onAuthenticationError: " + errMsgId + " - " + errString);

            if (mIsPaused)
                return;

            switch (errMsgId) {
                case FingerprintManager.FINGERPRINT_ERROR_HW_UNAVAILABLE:
                case FingerprintManager.FINGERPRINT_ERROR_CANCELED:
                    if (mShowToast) {
                        Toast.makeText(mContext, String.format("%s\n%s",
                            TAG, mGbContext.getString(R.string.fingerprint_sensor_unavail)),
                            Toast.LENGTH_SHORT).show();
                    }
                    restartListeningDelayed(10000);
                    break;
                case FingerprintManager.FINGERPRINT_ERROR_UNABLE_TO_PROCESS:
                    restartListeningDelayed(3000);
                    break;
                case FingerprintManager.FINGERPRINT_ERROR_TIMEOUT:
                    restartListeningDelayed(2000);
                    break;
                case FingerprintManager.FINGERPRINT_ERROR_LOCKOUT:
                    restartListeningDelayed(35000);
                    Toast.makeText(mContext, String.format("%s\n%s",
                            TAG, mGbContext.getString(R.string.fingerprint_sensor_locked)),
                            Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            if (DEBUG) log("onAuthenticationHelp: " + helpMsgId + " - " + helpString);

            if (helpMsgId == FingerprintManager.FINGERPRINT_ACQUIRED_TOO_FAST ||
                    helpMsgId == FingerprintManager.FINGERPRINT_ACQUIRED_INSUFFICIENT ||
                    helpMsgId == FingerprintManager.FINGERPRINT_ACQUIRED_PARTIAL) {
                startActivity(null);
            } else if (mShowToast) {
                Toast.makeText(mContext, TAG + "\n" + helpString,Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onAuthenticationFailed() {
            if (DEBUG) log("onAuthenticationFailed");

            if (mShowToast) {
                Toast.makeText(mContext, String.format("%s\n%s",
                    TAG, mGbContext.getString(R.string.fingerprint_auth_failed)),
                        Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onAuthenticationSucceeded(AuthenticationResult result) {
            startActivity(getFingerIdFromResult(result));
            restartListeningDelayed(1000);
        }

        private String getFingerIdFromResult(Object result) {
            String id = null;
            try {
                Object fp = XposedHelpers.callMethod(result, "getFingerprint");
                if (fp != null) {
                    id = String.valueOf(XposedHelpers.callMethod(fp, "getFingerId"));
                    if (DEBUG) log("getFingerPrintIdFromResult: id=" + id);
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
            return id;
        }
    }

    public int[] getEnrolledFingerprintIds() {
        int[] ids = null;
        try {
            List<?> fpList = (List<?>) XposedHelpers.callMethod(mFpManager,
                    "getEnrolledFingerprints", Utils.getCurrentUser());
            if (fpList.size() > 0) {
                ids = new int[fpList.size()];
                for (int i = 0; i < fpList.size(); i++) {
                    ids[i] = (int) XposedHelpers.callMethod(fpList.get(i), "getFingerId");
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        return ids;
    }
}
