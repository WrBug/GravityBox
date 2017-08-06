package com.wrbug.gravitybox.nougat.util;

import android.view.View;
import android.view.ViewGroup;

import com.wrbug.gravitybox.nougat.BuildConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;

/**
 * LogUtils
 *
 * @author suanlafen
 * @since 2017/8/1
 */
public class LogUtils {
    private static void log(String tag, String log) {
        XposedBridge.log(tag + ": " + log);
    }

    private static void logLine(String tag, String title) {
        log(tag, "-----------" + title + "------------");
    }

    public static void showDeclaredField(String tag, Class c) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        Field[] fields = c.getDeclaredFields();
        showField(tag, c.getName(), fields);

    }

    public static void showField(String tag, Class c) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        Field[] fields = c.getFields();
        showField(tag, c.getName(), fields);
    }

    private static void showField(String tag, String className, Field[] fields) {
        log(tag, "");
        logLine(tag, "Field");
        log(tag, className);
        for (Field field : fields) {
            log(tag, field.getName() + " : " + field.getType().getName());
        }
        logLine(tag, "");
    }


    public static void showDeclaredMethod(String tag, Class c) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        Method[] methods = c.getDeclaredMethods();
        log(tag, "");
        logLine(tag, "Method");
        log(tag, c.getName());
        for (Method method : methods) {
            Class[] params = method.getParameterTypes();
            StringBuilder stringBuilder = new StringBuilder();
            for (Class param : params) {
                stringBuilder.append(param.toString()).append(" , ");
            }
            log(tag, method.getName() + " (" + stringBuilder.toString() + ") ");
        }
        logLine(tag, "");
    }

    public static void showViewGroup(String tag, ViewGroup viewGroup) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        log(tag, "");
        logLine(tag, "View");
        showViewGroup(tag, viewGroup, 1);
        logLine(tag, "");
    }

    private static void showViewGroup(String tag, ViewGroup viewGroup, int deep) {
        StringBuilder tab = new StringBuilder();
        for (int i = 0; i < deep; i++) {
            tab.append("\t\t");
        }
        log(tag, deep + ": " + tab + viewGroup.getClass().getName() + "{");
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View view = viewGroup.getChildAt(i);
            if (view instanceof ViewGroup) {
                showViewGroup(tag, (ViewGroup) view, deep + 1);
            } else {
                log(tag, (deep + 1) + ": " + tab.toString() + "\t\t" + view.getClass().getName());
            }
        }
        log(tag, tab + "\t\t" + "}");
    }
}
