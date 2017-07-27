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
package com.wrbug.gravitybox.nougat.managers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.wrbug.gravitybox.nougat.BroadcastSubReceiver;
import com.wrbug.gravitybox.nougat.BuildConfig;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.Utils;
import com.wrbug.gravitybox.nougat.adapters.IIconListAdapterItem;
import com.wrbug.gravitybox.nougat.adapters.IconListAdapter;
import com.wrbug.gravitybox.nougat.shortcuts.AShortcut;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.view.WindowManager;
import android.widget.Toast;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SubscriptionManager implements BroadcastSubReceiver {
    public static final String TAG="GB:SubscriptionManager";
    private static boolean DEBUG = BuildConfig.DEBUG;

    public static final String ACTION_CHANGE_DEFAULT_SIM_SLOT = 
            "gravitybox.intent.action.CHANGE_DEFAULT_SIM_SLOT";
    public static final String EXTRA_SUB_TYPE = "subType";
    public static final String EXTRA_SIM_SLOT = "simSlot";

    public static final String ACTION_GET_DEFAULT_SIM_SLOT = 
            "gravitybox.intent.action.GET_DEFAULT_SIM_SLOT";
    public static final String ACTION_REPORT_DEFAULT_SIM_SLOT = 
            "gravitybox.intent.action.REPORT_DEFAULT_SIM_SLOT";
    public static final String EXTRA_SIM_SLOT_VOICE = "simSlotVoice";
    public static final String EXTRA_SIM_SLOT_SMS = "simSlotSms";
    public static final String EXTRA_SIM_SLOT_DATA = "simSlotData";

    private static final int MSG_HANDLE_SUB_CHANGE = 1;
    private static final int MSG_HANDLE_REPORT = 2;

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    public enum SubType { VOICE, SMS, DATA };

    private Context mContext;
    private H mHandler;
    private android.telephony.SubscriptionManager mSubMgr;

    protected SubscriptionManager(Context context) {
        mContext = context;
        mHandler = new H();
        mSubMgr = android.telephony.SubscriptionManager.from(mContext);
        if (DEBUG) log("SubscriptionManager created");
    }

    class H extends Handler {
        H() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG) log("H:handleMessage: what=" + msg.what + "; " +
                    "obj=" + msg.obj + "; arg1=" + msg.arg1);

            if (msg.what == MSG_HANDLE_SUB_CHANGE) {
                if (!(msg.obj instanceof SubType))
                    return;

                final SubType subType = (SubType) msg.obj;
                final boolean showToast = (msg.arg2 == 1);
                if (subType == SubType.VOICE && msg.arg1 == 2) {
                    changeDefaultSub(subType, -1, showToast);
                } else if (msg.arg1 < 0 || msg.arg1 > 1) {
                    changeDefaultSub(subType, showToast);
                } else {
                    changeDefaultSub(subType, msg.arg1, showToast);
                }
            } else if (msg.what == MSG_HANDLE_REPORT) {
                sendReportingIntent();
            }
        }
    }

    private Context getGbContext() {
        try {
            return Utils.getGbContext(mContext);
        } catch (Throwable t) {
            return null;
        }
    }

    public void changeDefaultSub(final SubType subType, final int simSlot, final boolean showToast) {
        boolean result;
        SubscriptionInfo si = mSubMgr.getActiveSubscriptionInfoForSimSlotIndex(simSlot);
        if (subType == SubType.VOICE) {
            if (si == null && (simSlot == 0 || simSlot == 1)) {
                result = false;
            } else {
                result = setDefaultVoiceSubscription(si);
            }
        } else {
            result = setDefaultSubscription(subType, si);
        }
        if (showToast || !result) {
            final String msg = result ? getChangeOkMsg(subType, getSubDisplayName(si)) :
                getChangeFailedMsg(subType);
            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
        }
    }

    public void changeDefaultSub(final SubType subType, final boolean showToast) {
        final IconListAdapter arrayAdapter = new IconListAdapter(
                mContext, getSubItemList(subType));
        AlertDialog d = new AlertDialog.Builder(mContext)
                .setTitle(getDialogTitleFor(subType))
                .setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SubListItem item = (SubListItem) arrayAdapter.getItem(which);
                        if (DEBUG) log("Dialog onClick: which=" + item.getText());
                        dialog.dismiss();
                        int simSlot = item.getSubInfo() == null ? -1 :
                            item.getSubInfo().getSimSlotIndex();
                        changeDefaultSub(subType, simSlot, showToast);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        d.show();
    }

    private List<IIconListAdapterItem> getSubItemList(final SubType subType) {
        List<IIconListAdapterItem> list = new ArrayList<>();
        if (subType == SubType.VOICE) {
            list.add(new SubListItem(null));
            final TelecomManager telecomManager = 
                    (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            final TelephonyManager telephonyManager =
                    (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            final Iterator<PhoneAccountHandle> phoneAccounts =
                    telecomManager.getCallCapablePhoneAccounts().listIterator();
            while (phoneAccounts.hasNext()) {
                final PhoneAccount phoneAccount =
                        telecomManager.getPhoneAccount(phoneAccounts.next());
                int subId = getSubIdForPhoneAccount(telephonyManager, phoneAccount);
                if (subId != -1) {
                    list.add(new SubListItem(mSubMgr.getActiveSubscriptionInfo(subId)));
                }
            }
        } else {
            for (SubscriptionInfo si : mSubMgr.getActiveSubscriptionInfoList())
                if (si != null)
                    list.add(new SubListItem(si));
        }
        return list;
    }

    private boolean setDefaultSubscription(final SubType subType, final SubscriptionInfo subInfo) {
        if (subInfo == null)
            return false;

        try {
            if (subType == SubType.SMS) {
                XposedHelpers.callMethod(mSubMgr, "setDefaultSmsSubId",
                        subInfo.getSubscriptionId());
            } else if (subType == SubType.DATA) {
                XposedHelpers.callMethod(mSubMgr, "setDefaultDataSubId",
                        subInfo.getSubscriptionId());
            }
            return true;
        } catch (Throwable t) {
            XposedBridge.log(t);
            return false;
        }
    }

    private boolean setDefaultVoiceSubscription(final SubscriptionInfo subInfo) {
        try {
            final TelecomManager telecomManager =
                (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            XposedHelpers.callMethod(telecomManager,
                    "setUserSelectedOutgoingPhoneAccount",
                    subscriptionToPhoneAccountHandle(subInfo));
            return true;
        } catch (Throwable t) {
            XposedBridge.log(t);
            return false;
        }
    }

    private int getSubIdForPhoneAccount(final TelephonyManager tm, final PhoneAccount account) {
        try {
            return (int) XposedHelpers.callMethod(tm, "getSubIdForPhoneAccount", account);
        } catch (Throwable t) {
            XposedBridge.log(t);
            return -1;
        }
    }

    private PhoneAccountHandle subscriptionToPhoneAccountHandle(final SubscriptionInfo subInfo) {
        if (subInfo == null)
            return null;

        final TelecomManager telecomManager =
                (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        final TelephonyManager telephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        final Iterator<PhoneAccountHandle> phoneAccounts =
                telecomManager.getCallCapablePhoneAccounts().listIterator();
        while (phoneAccounts.hasNext()) {
            final PhoneAccountHandle phoneAccountHandle = phoneAccounts.next();
            final PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
            if (subInfo.getSubscriptionId() == getSubIdForPhoneAccount(telephonyManager, phoneAccount)) {
                return phoneAccountHandle;
            }
        }

        return null;
    }

    private int getDefaultSubscriptionSimSlot(final SubType subType) {
        try {
            SubscriptionInfo si = null;
            if (subType == SubType.SMS) {
                si = (SubscriptionInfo) XposedHelpers.callMethod(mSubMgr,
                        "getDefaultSmsSubscriptionInfo");
            } else if (subType == SubType.DATA) {
                si = (SubscriptionInfo) XposedHelpers.callMethod(mSubMgr,
                        "getDefaultDataSubscriptionInfo");
            }
            return (si == null ? -1 : si.getSimSlotIndex());
        } catch (Throwable t) {
            XposedBridge.log(t);
            return -1;
        }
    }

    private int getDefaultVoiceSubscriptionSimSlot() {
        try {
            final TelecomManager telecomManager =
                    (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            final TelephonyManager telephonyManager =
                    (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            PhoneAccountHandle pah = (PhoneAccountHandle) XposedHelpers.callMethod(telecomManager,
                    "getUserSelectedOutgoingPhoneAccount");
            if (pah != null) {
                PhoneAccount pa = telecomManager.getPhoneAccount(pah);
                int subId = getSubIdForPhoneAccount(telephonyManager, pa);
                SubscriptionInfo si = mSubMgr.getActiveSubscriptionInfo(subId);
                if (si != null) {
                    return si.getSimSlotIndex();
                }
            }
            return -1;
        } catch (Throwable t) {
            XposedBridge.log(t);
            return -1;
        }
    }

    private void sendReportingIntent() {
        Intent intent = new Intent(ACTION_REPORT_DEFAULT_SIM_SLOT);
        intent.putExtra(EXTRA_SIM_SLOT_VOICE, getDefaultVoiceSubscriptionSimSlot());
        intent.putExtra(EXTRA_SIM_SLOT_SMS, getDefaultSubscriptionSimSlot(SubType.SMS));
        intent.putExtra(EXTRA_SIM_SLOT_DATA, getDefaultSubscriptionSimSlot(SubType.DATA));
        mContext.sendBroadcast(intent);
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        Message msg = null;
        if (intent.getAction().equals(ACTION_CHANGE_DEFAULT_SIM_SLOT)) {
            msg = obtainMessageFor(intent.getStringExtra(EXTRA_SUB_TYPE),
                    intent.getIntExtra(EXTRA_SIM_SLOT, -1),
                    intent.getBooleanExtra(AShortcut.EXTRA_SHOW_TOAST, true));
        } else if (intent.getAction().equals(ACTION_GET_DEFAULT_SIM_SLOT)) {
            msg = mHandler.obtainMessage(MSG_HANDLE_REPORT);
        }
        if (msg != null) {
            mHandler.sendMessage(msg);
        }
    }

    private Message obtainMessageFor(String subTypeStr, int simSlot, boolean showToast) {
        if (subTypeStr == null)
            return null;

        SubType subType = SubType.valueOf(subTypeStr);
        if (subType == null)
            return null;

        return mHandler.obtainMessage(MSG_HANDLE_SUB_CHANGE,
                simSlot, (showToast ? 1 : 0), subType);
    }

    private String getDialogTitleFor(final SubType subType) {
        Context gbContext = getGbContext();
        if (gbContext == null)
            return subType.toString();

        switch (subType) {
            case VOICE: return gbContext.getString(R.string.sm_choose_voice_sub);
            case SMS: return gbContext.getString(R.string.sm_choose_sms_sub);
            case DATA: return gbContext.getString(R.string.sm_choose_data_sub);
            default: return subType.toString();
        }
    }

    private String getChangeOkMsg(final SubType subType, final String name) {
        Context gbContext = getGbContext();
        if (gbContext == null)
            return String.format(Locale.getDefault(), "Default SIM for %s set to: %s",
                    subType, name);

        switch (subType) {
            case VOICE:
                return String.format(Locale.getDefault(), "%s %s",
                        gbContext.getString(R.string.sm_sub_change_voice_ok), name);
            case SMS:
                return String.format(Locale.getDefault(), "%s %s",
                        gbContext.getString(R.string.sm_sub_change_sms_ok), name);
            case DATA:
                return String.format(Locale.getDefault(), "%s %s",
                        gbContext.getString(R.string.sm_sub_change_data_ok), name);
            default: return String.format(Locale.getDefault(), "Default SIM for %s set to: %s",
                    subType, name);
        }
    }

    private String getChangeFailedMsg(final SubType subType) {
        Context gbContext = getGbContext();
        if (gbContext == null)
            return String.format(Locale.getDefault(), "Failed to set default SIM for %s", subType);

        switch (subType) {
            case VOICE: return gbContext.getString(R.string.sm_sub_change_voice_failed);
            case SMS: return gbContext.getString(R.string.sm_sub_change_sms_failed);
            case DATA: return gbContext.getString(R.string.sm_sub_change_data_failed);
            default: return String.format(Locale.getDefault(), "Failed to set default SIM for %s", subType);
        }
    }

    private String getSubDisplayName(final SubscriptionInfo si) {
        if (si == null) {
            Context gbContext = getGbContext();
            return gbContext == null ? "Ask every time" :
                gbContext.getString(R.string.sm_voice_ask);
        } else {
            return si.getDisplayName() == null ?
                String.format(Locale.getDefault(), "SIM %d", (si.getSimSlotIndex()+1)) :
                    si.getDisplayName().toString();
        }
    }

    private class SubListItem implements IIconListAdapterItem {
        private SubscriptionInfo mSubInfo;

        SubListItem(SubscriptionInfo subInfo) {
            mSubInfo = subInfo;
        }

        @Override
        public String getText() {
            return getSubDisplayName(mSubInfo);
        }

        public SubscriptionInfo getSubInfo() {
            return mSubInfo;
        }

        @Override
        public String getSubText() {
            return null;
        }

        @Override
        public Drawable getIconLeft() {
            if (mSubInfo != null) {
                return new BitmapDrawable(mContext.getResources(),
                        mSubInfo.createIconBitmap(mContext));
            }
            return null;
        }

        @Override
        public Drawable getIconRight() {
            return null;
        }
    }
}
