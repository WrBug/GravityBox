package com.wrbug.gravitybox.nougat.battery;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.StatusbarBattery;

import de.robv.android.xposed.XposedBridge;

/**
 * BatteryLayout
 *
 * @author suanlafen
 * @since 2017/7/29
 */
public class BatteryWithPercentLayout extends FrameLayout {
    private static final String TAG = "GB:BatteryWithPercentLayout";

    private static void log(String msg) {
        XposedBridge.log(TAG + ":" + msg);
    }

    public BatteryWithPercentLayout(@NonNull Context context) {
        this(context, null);
    }

    public BatteryWithPercentLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryWithPercentLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
    }

    public void addCmCircleBattery(CmCircleBattery battery, StatusbarBattery sb) {
        addView(battery, 0);
        if (sb != null && sb.getView() != null) {
            ((ViewGroup) sb.getView().getParent()).removeView(sb.getView());
            addView(sb.getView(), 0);
        }
        log("addCmCircleBattery:" + battery + sb);
    }

    public void addBatteryPercent(TextView view) {
        addView(view);
        log("addBatteryPercent:" + view);
    }
}
