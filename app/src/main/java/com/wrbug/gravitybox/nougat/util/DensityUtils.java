package com.wrbug.gravitybox.nougat.util;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;

/**
 * DensityUtils
 *
 * @author suanlafen
 * @since 2017/7/31
 */
public class DensityUtils {
    public static int[] getWidthAndHeight(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int widthPixels = dm.widthPixels;
        int heightPixels = dm.heightPixels;
        float density = dm.density;
        int screenWidth = (int) (widthPixels * density);
        int screenHeight = (int) (heightPixels * density);
        return new int[]{screenWidth, screenHeight};
    }

    public static int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }
}
