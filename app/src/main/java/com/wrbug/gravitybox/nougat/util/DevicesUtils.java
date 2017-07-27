package com.wrbug.gravitybox.nougat.util;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

/**
 * DevicesUtils
 *
 * @author wrbug
 * @since 2017/7/24
 */
public class DevicesUtils {
    public static String getIMEI(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        String id = telephonyManager.getDeviceId();
        return TextUtils.isEmpty(id) ? "" : id;
    }

    public static String[] getAbis() {
        return Build.SUPPORTED_ABIS;
    }

    public static String getBrand() {
        return Build.FINGERPRINT;
    }
}
