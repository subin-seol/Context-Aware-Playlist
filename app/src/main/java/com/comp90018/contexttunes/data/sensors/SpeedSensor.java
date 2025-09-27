package com.comp90018.contexttunes.data.sensors;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.annotation.Nullable;

/**
 * Cadence-first sensor wrapper.
 *
 * Priority:
 *  1) TYPE_STEP_DETECTOR / TYPE_STEP_COUNTER (may require ACTIVITY_RECOGNITION on Android 10+).
 *  2) Accelerometer fallback with gravity removal + peak picking when step sensors are absent,
 *     permission is denied, or vendor blocks events.
 *
 * API:
 *  - start()/stop()
 *  - boolean isCadenceAlive(): true if a step seen within ALIVE_MS
 *  - int pollCadenceBPM(): current cadence (steps/min), decays to 0 when idle
 *  - static double estimateSpeedFromCadenceEma(int spm, double prevKmh): stride-based mapping
 */
public class SpeedSensor implements SensorEventListener {

    private static final double CADENCE_EMA_ALPHA = 0.30;
    private static final long   ALIVE_MS          = 2000L;

    // Accelerometer fallback (conservative to avoid false steps at rest)
    private static final double LP_ALPHA           = 0.90;  // gravity low-pass
    private static final double PEAK_THRESHOLD_G   = 0.12;  // peak threshold after gravity removal
    private static final long   PEAK_REFRACTORY_MS = 280L;  // min spacing between peaks

    private final SensorManager sm;
    @Nullable private final Sensor stepDetector;
    @Nullable private final Sensor stepCounter;
    @Nullable private final Sensor accelerometer;

    private boolean haveCounterBase = false;
    private long    counterBase     = 0;
    private long    lastStepAtMs    = 0;
    private double  cadenceEma      = 0.0;

    // fallback state
    private double  lpMag           = 0.0;
    private long    lastPeakAt      = 0L;

    public SpeedSensor(SensorManager sm) {
        this.sm = sm;
        this.stepDetector  = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        this.stepCounter   = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        this.accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /** Always call start(); we guard runtime-permission failures via try/catch. */
    public void start() {
        try { if (stepDetector != null) sm.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_NORMAL); }
        catch (SecurityException ignore) {}
        try { if (stepCounter != null)  sm.registerListener(this, stepCounter,  SensorManager.SENSOR_DELAY_NORMAL); }
        catch (SecurityException ignore) {}

        if (accelerometer != null) {
            sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME); // ~50 Hz
        }

        haveCounterBase = false;
        cadenceEma = 0.0;
        lastStepAtMs = 0L;
        lastPeakAt = 0L;
        lpMag = 0.0;
    }

    public void stop() { sm.unregisterListener(this); }

    public boolean isCadenceAlive() {
        return (System.currentTimeMillis() - lastStepAtMs) <= ALIVE_MS;
    }

    public int pollCadenceBPM() {
        double ema = cadenceEma;
        if (!isCadenceAlive()) {
            // decay faster when idle
            ema *= 0.85;
            if (ema < 1.0) ema = 0.0;
            cadenceEma = ema;
        }
        return (int) Math.round(cadenceEma);
    }

    /**
     * Cadence→speed using a piecewise stride-length model:
     *  - empirical average adult step length 0.60–0.80 m when walking, 1.0–1.2 m when running
     *  - we let stride increase with cadence, then v = cadence * stride / 60 (m/s)
     *  - convert to km/h and blend slightly with previous to avoid jumps when sources switch
     */
    public static double estimateSpeedFromCadenceEma(int spm, double prevKmh) {
        double stride; // meters per step
        if (spm < 100) {
            // 60 spm -> 0.60 m ; 100 spm -> 0.80 m
            stride = 0.60 + (spm - 60) * 0.005;
        } else if (spm < 150) {
            // 100 spm -> 0.80 m ; 150 spm -> 1.10 m
            stride = 0.80 + (spm - 100) * 0.006;
        } else {
            // 150 spm -> 1.10 m ; 180 spm -> ~1.22 m
            stride = 1.10 + (spm - 150) * 0.004;
        }
        if (stride < 0.50) stride = 0.50;
        if (stride > 1.30) stride = 1.30;

        double vKmh = (spm / 60.0) * stride * 3.6;   // km/h
        // light smoothing against the previously estimated km/h
        vKmh = 0.30 * vKmh + 0.70 * Math.max(0, prevKmh);
        return Math.max(0.0, Math.min(vKmh, 18.0));  // reasonable cap
    }

    // ---- Sensor callbacks ----

    @Override public void onSensorChanged(SensorEvent e) {
        long now = System.currentTimeMillis();

        if (e.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            if (e.values != null && e.values.length > 0 && e.values[0] > 0.5f) onStep(now);
            return;
        }
        if (e.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            long total = (long) e.values[0];
            if (!haveCounterBase) {
                counterBase = total;
                haveCounterBase = true;
            } else {
                long inc = total - counterBase;
                if (inc > 0) {
                    counterBase = total;
                    onStep(now);
                }
            }
            return;
        }
        if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            final float ax = e.values[0], ay = e.values[1], az = e.values[2];
            final double mag = Math.sqrt(ax * ax + ay * ay + az * az) / SensorManager.GRAVITY_EARTH; // g
            lpMag = LP_ALPHA * lpMag + (1.0 - LP_ALPHA) * mag;
            final double high = mag - lpMag; // gravity removed
            if (high > PEAK_THRESHOLD_G && (now - lastPeakAt) > PEAK_REFRACTORY_MS) {
                lastPeakAt = now;
                onStep(now);
            }
        }
    }

    private void onStep(long now) {
        if (lastStepAtMs > 0) {
            long dt = now - lastStepAtMs;
            if (dt > 0) {
                double instSpm = 60_000.0 / dt; // steps/min
                cadenceEma = CADENCE_EMA_ALPHA * instSpm + (1.0 - CADENCE_EMA_ALPHA) * cadenceEma;
            }
        } else {
            // bootstrap from 0 so the first mapping to speed responds
            cadenceEma = Math.max(cadenceEma, 20.0);
        }
        lastStepAtMs = now;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
