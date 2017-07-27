package com.wrbug.gravitybox.nougat;

import de.robv.android.xposed.XposedHelpers;

import com.wrbug.gravitybox.nougat.ModStatusBar.ContainerType;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class StatusbarSignalClusterMtk extends StatusbarSignalCluster {
    protected boolean mRoamingIndicatorsDisabled;
    protected static ImageView[] mMobileRoam = null;

    public StatusbarSignalClusterMtk(ContainerType containerType, LinearLayout view) throws Throwable {
        super(containerType, view);
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        super.onBroadcastReceived(context, intent);

        if (intent.getAction().equals(GravityBoxSettings.ACTION_DISABLE_ROAMING_INDICATORS_CHANGED)) {
            mRoamingIndicatorsDisabled = intent.getBooleanExtra(
                    GravityBoxSettings.EXTRA_INDICATORS_DISABLED, false);
            update();
        }
    }

    @Override
    public void initPreferences() {
        super.initPreferences();

        mRoamingIndicatorsDisabled = sPrefs.getBoolean(
                GravityBoxSettings.PREF_KEY_DISABLE_ROAMING_INDICATORS, false);
        mDataActivityEnabled = false;
        mNetworkTypeIndicatorsDisabled = false;
    }

    protected void updateRoamingIndicator() {
        try {
            if (mRoamingIndicatorsDisabled) {
                Object mobileRoam = XposedHelpers.getObjectField(mView, "mMobileRoam");
                if (mMobileRoam == null) {
                    mMobileRoam = (mobileRoam instanceof ImageView) ?
                            new ImageView[] { (ImageView) mobileRoam, Utils.hasGeminiSupport() ?
                                    (ImageView) XposedHelpers.getObjectField(mView, "mMobileRoamGemini") : null } :
                                        (ImageView[]) mobileRoam;
                } else {
                    for (ImageView iv : mMobileRoam) {
                        if (iv != null) {
                            iv.setVisibility(View.GONE);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logAndMute("updateRoamingIndicator", t);
        }
    }
}
