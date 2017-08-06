package com.wrbug.gravitybox.nougat.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;

import com.wrbug.gravitybox.nougat.GravityBox;

import java.io.File;
import java.io.IOException;

import de.robv.android.xposed.XposedBridge;

/**
 * SharedPreferencesUtils
 *
 * @author wrbug
 * @since 2017/8/5
 */
public class SharedPreferencesUtils {
    public static SharedPreferences getSharedPreferences(Context context, String prefFileName) {
        SharedPreferences prefs = context.getSharedPreferences(
                prefFileName, Context.MODE_WORLD_READABLE);
        return prefs;
    }

    public static SharedPreferences getSharedPreferences(PreferenceManager preferenceManager, PreferenceScreen preferenceScreen) {
        preferenceManager.setSharedPreferencesMode(Context.MODE_WORLD_READABLE);
        return preferenceScreen.getSharedPreferences();
    }

    public static SharedPreferences getSharedPreferences(PreferenceManager preferenceManager, String prefName) {
        if (!TextUtils.isEmpty(prefName)) {
            preferenceManager.setSharedPreferencesName(prefName);
        }
        preferenceManager.setSharedPreferencesMode(Context.MODE_WORLD_READABLE);
        return preferenceManager.getSharedPreferences();
    }
}
