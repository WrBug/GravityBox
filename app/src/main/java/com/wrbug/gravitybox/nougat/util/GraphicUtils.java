package com.wrbug.gravitybox.nougat.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.util.LruCache;
import android.util.SparseArray;

import java.lang.ref.SoftReference;
import java.util.Map;

/**
 * GraphicUtils
 *
 * @author suanlafen
 * @since 2017/7/31
 */
public class GraphicUtils {
    private static SparseArray<SoftReference<Bitmap>> cache = new SparseArray<>();

    public static Bitmap getBackGroundBitmap(int color, int width, int height) {
        int key = color & width + (width * height) & color;
        if (cache.get(key) != null) {
            Bitmap bitmap = cache.get(key).get();
            if (bitmap != null && !bitmap.isRecycled()) {
                return bitmap;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(color);
        cache.put(key, new SoftReference<>(bitmap));
        return bitmap;
    }

    public static Bitmap getAlplaBitmap(Bitmap sourceImg, int alpha) {
        int[] argb = new int[sourceImg.getWidth() * sourceImg.getHeight()];
        sourceImg.getPixels(argb, 0, sourceImg.getWidth(), 0, 0, sourceImg.getWidth(), sourceImg.getHeight());
        alpha = alpha * 255 / 100;
        for (int i = 0; i < argb.length; i++) {
            argb[i] = (alpha << 24) | (argb[i] & 0x00FFFFFF);
        }
        sourceImg = Bitmap.createBitmap(argb, sourceImg.getWidth(), sourceImg.getHeight(), Bitmap.Config.ARGB_8888);
        return sourceImg;
    }

    public static Bitmap mergeBitmap(Bitmap firstBitmap, Bitmap secondBitmap) {
        Bitmap bitmap = Bitmap.createBitmap(firstBitmap.getWidth(), firstBitmap.getHeight(), firstBitmap.getConfig());
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(firstBitmap, new Matrix(), null);
        canvas.drawBitmap(secondBitmap, 0, 0, null);
        return bitmap;
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return bitmap;
    }
}
