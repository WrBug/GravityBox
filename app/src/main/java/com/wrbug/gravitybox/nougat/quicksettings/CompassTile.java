/*
 * Copyright (C) 2015 The CyanogenMod Project
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.wrbug.gravitybox.nougat.quicksettings;

import com.wrbug.gravitybox.nougat.R;

import de.robv.android.xposed.XSharedPreferences;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

public class CompassTile extends QsTile implements SensorEventListener {
    private final static float ALPHA = 0.97f;

    private boolean mActive = false;
    private Float mNewDegree;

    private SensorManager mSensorManager;
    private Sensor mAccelerationSensor;
    private Sensor mGeomagneticFieldSensor;
    private WindowManager mWindowManager;

    private float[] mAcceleration;
    private float[] mGeomagnetic;

    private ImageView mImage;
    private boolean mListeningSensors;
    private int mCount;
    private boolean mUpdatePending;

    public CompassTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mAccelerationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGeomagneticFieldSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        setListeningSensors(false);
        mSensorManager = null;
        mAccelerationSensor = null;
        mGeomagneticFieldSensor = null;
        mWindowManager = null;
        mImage = null;
    }

    @Override
    public void onCreateTileView(View tileView) throws Throwable {
        super.onCreateTileView(tileView);

        mImage = (ImageView) tileView.findViewById(android.R.id.icon);
    }

    @Override
    public boolean supportsHideOnChange() {
        return false;
    }

    @Override
    public void handleClick() {
        mActive = !mActive;
        refreshState();
        setListeningSensors(mActive);
        super.handleClick();
    }

    private void setListeningSensors(boolean listening) {
        if (listening == mListeningSensors) return;
        mListeningSensors = listening;
        if (mListeningSensors) {
            mCount = 10;
            mUpdatePending = false;
            mSensorManager.registerListener(
                    this, mAccelerationSensor, SensorManager.SENSOR_DELAY_GAME);
            mSensorManager.registerListener(
                    this, mGeomagneticFieldSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        if (mActive) {
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_compass_on);
            if (mNewDegree != null) {
                mState.label = formatValueWithCardinalDirection(mNewDegree);

                float target = getBaseDegree() - mNewDegree;
                float relative = target - mImage.getRotation();
                if (relative > 180) relative -= 360;

                mImage.setRotation(mImage.getRotation() + relative / 2);
            } else {
                mState.label = mGbContext.getString(R.string.quick_settings_compass_init);
                mImage.setRotation(0);
            }
        } else {
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_compass_off);
            mState.label = mGbContext.getString(R.string.quick_settings_compass_off);
            mImage.setRotation(0);
        }

        mUpdatePending = false;
        
        super.handleUpdateState(state, arg);
    }

    @Override
    public void setListening(boolean listening) {
        if (!listening) {
            setListeningSensors(false);
            mActive = false;
        }
    }

    private float getBaseDegree() {
        switch (mWindowManager.getDefaultDisplay().getRotation()) {
            default:
            case Surface.ROTATION_0: return 360f;
            case Surface.ROTATION_90: return 270f;
            case Surface.ROTATION_180: return 180f;
            case Surface.ROTATION_270: return 90f;
        }
    }

    private String formatValueWithCardinalDirection(float degree) {
        int cardinalDirectionIndex = (int) (Math.floor(((degree - 22.5) % 360) / 45) + 1) % 8;
        String[] cardinalDirections = mGbContext.getResources().getStringArray(
                R.array.cardinal_directions);

        return mGbContext.getString(R.string.quick_settings_compass_value, degree,
                cardinalDirections[cardinalDirectionIndex]);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] values;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (mAcceleration == null) {
                mAcceleration = event.values.clone();
            }
            values = mAcceleration;
        } else {
            // Magnetic field sensor
            if (mGeomagnetic == null) {
                mGeomagnetic = event.values.clone();
            }
            values = mGeomagnetic;
        }

        for (int i = 0; i < 3; i++) {
            values[i] = ALPHA * values[i] + (1 - ALPHA) * event.values[i];
        }

        if (!mActive || !mListeningSensors || mUpdatePending ||
                mAcceleration == null || mGeomagnetic == null) {
            // Nothing to do at this moment
            return;
        }

        if (mCount++ <= 10) {
            return;
        }

        mCount = 0;
        float R[] = new float[9];
        float I[] = new float[9];
        if (!SensorManager.getRotationMatrix(R, I, mAcceleration, mGeomagnetic)) {
            // Rotation matrix couldn't be calculated
            return;
        }

        // Get the current orientation
        float[] orientation = new float[3];
        SensorManager.getOrientation(R, orientation);

        // Convert azimuth to degrees
        mNewDegree = Float.valueOf((float) Math.toDegrees(orientation[0]));
        mNewDegree = (mNewDegree + 360) % 360;

        mUpdatePending = true;
        refreshState();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // noop
    }
}
