package com.wrbug.gravitybox.nougat.util;

import android.util.ArrayMap;

/**
 * ArrayUtils
 *
 * @author wrbug
 * @since 2017/7/26
 */
public class ArrayUtils {

    public static boolean arrayHas(String[] array, String data) {

        if (array == null || array.length == 0) {
            return false;
        }
        for (String s : array) {
            if (s.equals(data)) {
                return true;
            }
        }
        return false;
    }

    public static boolean arrayHas(int[] array, int value) {
        if (array == null || array.length == 0) {
            return false;
        }
        for (int i : array) {
            if (value == i) {
                return true;
            }
        }
        return false;
    }
}
