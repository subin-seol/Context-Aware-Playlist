package com.comp90018.contexttunes.data.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Provides ambient light readings as simple buckets: DIM, NORMAL, BRIGHT.
 * Safe on devices without a light sensor.
 */
public class LightSensor implements SensorEventListener {

    // ----- Buckets -----
    public enum LightBucket {
        DIM, NORMAL, BRIGHT, UNKNOWN
    }

    // ----- Callback -----
    public interface Callback {
        void onBucket(@NonNull LightBucket bucket);
    }

    private final Context appContext;
    private final SensorManager sensorManager;
    private final Sensor lightSensor;
    private final PowerManager powerManager;
    private final Callback callback;

    private boolean started = false;
    private @Nullable LightBucket lastBucket = null;

    public LightSensor(@NonNull Context context, @NonNull Callback callback) {
        this.appContext = context.getApplicationContext();
        this.callback = callback;
        this.sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        this.lightSensor = (sensorManager != null) ? sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) : null;
        this.powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
    }

    public boolean isAvailable() {
        return lightSensor != null;
    }

    public void start() {
        if (started) return;
        started = true;

        if (!isAvailable()) {
            emit(LightBucket.UNKNOWN);
            return;
        }
        if (!isInteractive()) {
            emit(LightBucket.UNKNOWN);
            return;
        }

        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void stop() {
        if (!started) return;
        started = false;
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        lastBucket = null;
    }

    private boolean isInteractive() {
        return powerManager != null && powerManager.isInteractive();
    }

    private void emit(@NonNull LightBucket bucket) {
        callback.onBucket(bucket);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values.length == 0) return;
        float lux = event.values[0];
        LightBucket bucket = classify(lux, lastBucket);
        if (bucket != lastBucket) {
            lastBucket = bucket;
            emit(bucket);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // not needed
    }

    // ----- Classifier -----
    private static final float DIM_MAX = 100f;
    private static final float NORMAL_MAX = 500f;
    private static final float HYSTERESIS = 10f;

    private static LightBucket classify(float lux, @Nullable LightBucket prev) {
        if (Float.isNaN(lux) || lux < 0f) return LightBucket.UNKNOWN;
        if (prev == null) return raw(lux);

        switch (prev) {
            case DIM:
                if (lux > DIM_MAX + HYSTERESIS) return raw(lux);
                return LightBucket.DIM;
            case NORMAL:
                if (lux < DIM_MAX - HYSTERESIS) return LightBucket.DIM;
                if (lux > NORMAL_MAX + HYSTERESIS) return LightBucket.BRIGHT;
                return LightBucket.NORMAL;
            case BRIGHT:
                if (lux < NORMAL_MAX - HYSTERESIS) return raw(lux);
                return LightBucket.BRIGHT;
            case UNKNOWN:
            default:
                return raw(lux);
        }
    }

    private static LightBucket raw(float lux) {
        if (lux < DIM_MAX) return LightBucket.DIM;
        if (lux <= NORMAL_MAX) return LightBucket.NORMAL;
        return LightBucket.BRIGHT;
    }
}
