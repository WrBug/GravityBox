package com.wrbug.gravitybox.nougat.util;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

/**
 * LogUtils
 *
 * @author suanlafen
 * @since 2017/8/1
 */
public class LogUtils {
    public static String showDeclaredMethod(Class c) {
        Method[] methods = c.getDeclaredMethods();
        StringBuilder builder = new StringBuilder();
        builder.append(c.getName()).append("{\n");
        for (Method method : methods) {
            Class[] params = method.getParameterTypes();
            StringBuilder stringBuilder = new StringBuilder();
            for (Class param : params) {
                stringBuilder.append(param.toString()).append(" , ");
            }
            builder.append(method.getName()).append(" (").append(stringBuilder).append(") ").append("\n");
        }
        return builder.toString();
    }

    public static String showViewGroup(ViewGroup viewGroup) {
        return showViewGroup(viewGroup, 1);
    }

    public static String showViewGroup(ViewGroup viewGroup, int deep) {
        String tab = "";
        for (int i = 0; i < deep; i++) {
            tab += "\t\t";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(tab).append(viewGroup.getClass().getName()).append("{").append("\n");
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View view = viewGroup.getChildAt(i);
            if (view instanceof ViewGroup) {
                builder.append(showViewGroup((ViewGroup) view, deep + 1));
            } else {
                builder.append(tab).append(tab).append(view.getClass().getName()).append("\n");
            }
        }
        builder.append(tab).append("}").append("\n");
        return builder.toString();
    }
}
