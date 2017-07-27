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
package com.wrbug.gravitybox.nougat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class LockscreenPinScrambler {
    public static final String TAG = "GB:LockscreenPinScrambler";

    private static final String CLASS_NUMPAD_KEY = "com.android.keyguard.NumPadKey";
    private static List<Integer> NUMBERS = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 0);

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private ViewGroup mContainer;

    public LockscreenPinScrambler(ViewGroup container) {
        mContainer = container;
    }

    public void scramble() throws Throwable {
        Collections.shuffle(NUMBERS);
        List<Object> keys = getNumpadKeys();
        if (keys.size() != NUMBERS.size()) {
            log("Unexpected size of NumPadKey array (" + keys.size() + ")");
            return;
        }
        for (int i = 0; i < NUMBERS.size(); i++) {
            changeDigit(keys.get(i), NUMBERS.get(i));
        }
    }

    private List<Object> getNumpadKeys() {
        return getNumpadKeysRecursive(mContainer);
    }

    private List<Object> getNumpadKeysRecursive(ViewGroup vg) {
        List<Object> keys = new ArrayList<>();
        int childCount = vg.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = vg.getChildAt(i);
            if (child.getClass().getName().equals(CLASS_NUMPAD_KEY)) {
                keys.add(child);
            } else if (child instanceof ViewGroup) {
                keys.addAll(getNumpadKeysRecursive((ViewGroup) child));
            }
        }
        return keys;
    }

    private void changeDigit(Object key, int digit) {
        XposedHelpers.setIntField(key, "mDigit", digit);
        ((TextView)XposedHelpers.getObjectField(key, "mDigitText"))
            .setText(Integer.toString(digit));

        TextView kt = (TextView) XposedHelpers.getObjectField(
                key, "mKlondikeText");
        kt.setText("");
        kt.setVisibility(View.INVISIBLE);
        if (digit >= 0) {
            String[] sKlondike = (String[]) XposedHelpers.getStaticObjectField(
                    key.getClass(), "sKlondike");
            if (sKlondike != null && sKlondike.length > digit) {
                String klondike = sKlondike[digit];
                if (klondike.length() > 0) {
                    kt.setText(klondike);
                    kt.setVisibility(View.VISIBLE);
                }
            }
        }

        ((View)key).setContentDescription(Integer.toString(digit));
    }
}
