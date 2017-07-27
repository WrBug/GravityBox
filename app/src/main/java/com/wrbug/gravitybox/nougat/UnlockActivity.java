package com.wrbug.gravitybox.nougat;
import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.view.ContextThemeWrapper;
import android.widget.Toast;

public class UnlockActivity extends Activity implements GravityBoxResultReceiver.Receiver {
    private static final String PKG_UNLOCKER = "com.ceco.gravitybox.unlocker";
    private static final String ACTION_UNLOCK = "gravitybox.intent.action.UNLOCK";
    private static final String ACTION_CHECK_POLICY = "gravitybox.intent.action.CHECK_POLICY";
    private static final String PERMISSION_UNLOCK = "gravitybox.permission.UNLOCK";

    protected interface CheckPolicyHandler {
        void onPolicyResult(boolean ok);
    } 

    private GravityBoxResultReceiver mReceiver;
    private Handler mHandler;
    private Dialog mAlertDialog;
    private ProgressDialog mProgressDialog;
    private int mDlgThemeId;

    private Runnable mGetSystemPropertiesTimeout = new Runnable() {
        @Override
        public void run() {
            dismissProgressDialog();
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    new ContextThemeWrapper(UnlockActivity.this, mDlgThemeId))
                .setTitle(R.string.app_name)
                .setMessage(R.string.gb_startup_error)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                });
            mAlertDialog = builder.create();
            mAlertDialog.show();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        File file = new File(getFilesDir() + "/" + GravityBoxSettings.FILE_THEME_DARK_FLAG);
        mDlgThemeId = file.exists() ? android.R.style.Theme_Material_Dialog :
            android.R.style.Theme_Material_Light_Dialog;
        super.onCreate(savedInstanceState);

        mHandler = new Handler();
        mReceiver = new GravityBoxResultReceiver(mHandler);
        mReceiver.setReceiver(this);
        Intent intent = new Intent();
        intent.setAction(SystemPropertyProvider.ACTION_GET_SYSTEM_PROPERTIES);
        intent.putExtra("receiver", mReceiver);
        intent.putExtra("settings_uuid", SettingsManager.getInstance(this).getOrCreateUuid());
        mProgressDialog = new ProgressDialog(
                new ContextThemeWrapper(UnlockActivity.this, mDlgThemeId));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setTitle(R.string.app_name);
        mProgressDialog.setMessage(getString(R.string.gb_startup_progress));
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
        mHandler.postDelayed(mGetSystemPropertiesTimeout, 5000);
        sendBroadcast(intent);
    }

    @Override
    protected void onDestroy() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mGetSystemPropertiesTimeout);
            mHandler = null;
        }
        mReceiver = null;
        dismissProgressDialog();
        dismissAlertDialog();

        super.onDestroy();
    }

    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        if (mHandler != null) {
            mHandler.removeCallbacks(mGetSystemPropertiesTimeout);
            mHandler = null;
        }
        dismissProgressDialog();
        if (resultCode == SystemPropertyProvider.RESULT_SYSTEM_PROPERTIES) {
            dismissProgressDialog();
            Intent intent = new Intent(SystemPropertyProvider.ACTION_REGISTER_UUID);
            intent.putExtra(SystemPropertyProvider.EXTRA_UUID,
                    SettingsManager.getInstance(this).getOrCreateUuid());
            intent.putExtra(SystemPropertyProvider.EXTRA_UUID_TYPE, "Unlocker");
            sendBroadcast(intent);
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    new ContextThemeWrapper(UnlockActivity.this, mDlgThemeId))
                .setTitle(R.string.app_name)
                .setMessage(R.string.premium_unlocked_message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                });
            mAlertDialog = builder.create();
            mAlertDialog.show();
        } else {
            finish();
        }
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        mProgressDialog = null;
    }

    private void dismissAlertDialog() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
        mAlertDialog = null;
    }

    public static class UnlockReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_UNLOCK)) {
                if (intent.getParcelableExtra("receiver") instanceof ResultReceiver) {
                    ((ResultReceiver)intent.getParcelableExtra("receiver")).send(0, null);
                }
                Intent i = new Intent(context, UnlockActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        }
    }

    public static class PkgManagerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Uri data = intent.getData();
            String pkgName = data == null ? null : data.getSchemeSpecificPart();
            if (!PKG_UNLOCKER.equals(pkgName)) return;

            if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED) &&
                    !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                maybeRunUnlocker(context);
            }

            if (intent.getAction().equals(Intent.ACTION_PACKAGE_FULLY_REMOVED)) {
                SettingsManager.getInstance(context).resetUuid();
            }
        }
    }

    protected static void maybeRunUnlocker(Context context) {
        try {
            PackageInfo pkgInfo = context.getPackageManager()
                    .getPackageInfo(PKG_UNLOCKER, 0);
            if (pkgInfo.versionCode < 16) {
                Toast.makeText(context, context.getString(R.string.msg_unlocker_old),
                        Toast.LENGTH_LONG).show();
            } else {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setPackage(PKG_UNLOCKER);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } catch (Exception e) { 
            //e.printStackTrace();
        }
    }

    private static class CheckPolicyReceiver extends BroadcastReceiver {
        private Context mContext;
        private Handler mHandler;
        private CheckPolicyHandler mPolicyHandler;
        private boolean mIsRegistered;

        private Runnable mExpiredRunnable = new Runnable() {
            @Override
            public void run() {
                unregister();
                mPolicyHandler.onPolicyResult(false);
            }
        };

        CheckPolicyReceiver(Context context, CheckPolicyHandler policyHandler) {
            mContext = context;
            mPolicyHandler = policyHandler;
            mHandler = new Handler();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mHandler.removeCallbacks(mExpiredRunnable);
            mPolicyHandler.onPolicyResult(true);
            unregister();
        }

        private void register() {
            mContext.registerReceiver(this, new IntentFilter(ACTION_CHECK_POLICY),
                    PERMISSION_UNLOCK, null);
            mIsRegistered = true;
            mHandler.postDelayed(mExpiredRunnable, 3000);
        }

        private void unregister() {
            if (mIsRegistered) {
                mContext.unregisterReceiver(this);
                mIsRegistered = false;
            }
        }
    };

    protected static void checkPolicyOk(final Context context, final CheckPolicyHandler policyHandler) {
        final CheckPolicyReceiver receiver = new CheckPolicyReceiver(context, policyHandler);;
        try {
            PackageInfo pkgInfo = context.getPackageManager()
                    .getPackageInfo(PKG_UNLOCKER, 0);
            if (pkgInfo.versionCode < 16) {
                policyHandler.onPolicyResult(false);
                Toast.makeText(context, context.getString(R.string.msg_unlocker_old),
                        Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent = new Intent(ACTION_CHECK_POLICY);
            intent.setComponent(new ComponentName(pkgInfo.packageName,
                    pkgInfo.packageName+".CheckPolicyService"));
            receiver.register();
            context.startService(intent);
        } catch (NameNotFoundException nnfe) {
            policyHandler.onPolicyResult(false);
            Toast.makeText(context, context.getString(R.string.msg_unlocker_missing),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            receiver.unregister();
            policyHandler.onPolicyResult(false);
            Toast.makeText(context,
                    String.format("%s: %s",
                    context.getString(R.string.msg_unlocker_error),
                    e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }
}
