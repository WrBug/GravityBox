package com.wrbug.gravitybox.nougat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.wrbug.gravitybox.nougat.KeyButtonView;

/**
 * KeyButtonContainer
 *
 * @author wrbug
 * @since 2017/7/21
 */
public class KeyButtonContainer extends FrameLayout {
    private KeyButtonView mKeyButtonView;

    public KeyButtonContainer(Context context) {
        super(context);
        mKeyButtonView = new KeyButtonView(context);
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mKeyButtonView.setLayoutParams(params);
        addView(mKeyButtonView);
    }

    public void setKeyCode(int keyCode) {
        mKeyButtonView.setKeyCode(keyCode);
    }

    public void setGlowColor(int color) {
        mKeyButtonView.setGlowColor(color);
    }

    public void setQuiescentAlpha(float alpha, boolean animate) {
        mKeyButtonView.setQuiescentAlpha(alpha, animate);
    }

    public float getQuiescentAlpha() {
        return mKeyButtonView.getQuiescentAlpha();
    }

    public float getDrawingAlpha() {
        return mKeyButtonView.getDrawingAlpha();
    }

    public void setDrawingAlpha(float x) {
        mKeyButtonView.setDrawingAlpha(x);
    }

    public void sendEvent(int action, int flags) {
        mKeyButtonView.sendEvent(action, flags);
    }

    public void sendEvent(int action, int flags, long when) {
        mKeyButtonView.sendEvent(action, flags, when);
    }

    public void setScaleType(ImageView.ScaleType scaleType) {
        mKeyButtonView.setScaleType(scaleType);
    }

    public void setImageDrawable(Drawable drawable) {
        mKeyButtonView.setImageDrawable(drawable);
    }

    public ImageView.ScaleType getScaleType() {
        return mKeyButtonView.getScaleType();
    }
}
