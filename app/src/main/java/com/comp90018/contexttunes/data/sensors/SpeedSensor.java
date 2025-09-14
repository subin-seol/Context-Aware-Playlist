package com.comp90018.contexttunes.data.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * SpeedSensor
 * - Primary: GPS (FusedLocationProviderClient) using Location.getSpeed() [m/s]
 * - Fallback: Step counter (TYPE_STEP_COUNTER) over a ~5s window
 * - One-shot "burst" session: startBurstSession(ms) -> emit one Measurement -> stopBurstSession()
 */
public class SpeedSensor implements SensorEventListener {

    /** Structured measurement result. */
    public static class Measurement {
        public final float speedMps;                // meters per second
        @Nullable public final Float cadenceSpm;    // steps per minute (if derived), else null
        @Nullable public final Integer targetBpm;   // suggested playlist BPM from cadence, else null
        public final String activity;               // still | walking | running
        public final String source;                 // gps | steps

        public Measurement(float speedMps,
                           @Nullable Float cadenceSpm,
                           @Nullable Integer targetBpm,
                           String activity,
                           String source) {
            this.speedMps = speedMps;
            this.cadenceSpm = cadenceSpm;
            this.targetBpm = targetBpm;
            this.activity = activity;
            this.source = source;
        }
    }

    /** Callback delivers exactly ONE measurement per burst. */
    public interface Callback {
        void onMeasurement(Measurement m);
        void onError(String reason);
    }

    private final Context app;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final FusedLocationProviderClient fusedClient;
    private final SensorManager sensorManager;
    @Nullable private final Sensor stepCounter;

    private boolean sessionActive = false;
    private boolean usingStepFallback = false;

    private float lastStepCount = -1f;
    private long  lastStepTimestampNs  = 0L;

    public SpeedSensor(Context ctx, Callback cb) {
        this.app = ctx.getApplicationContext();
        this.callback = cb;
        this.fusedClient = LocationServices.getFusedLocationProviderClient(app);
        this.sensorManager = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        this.stepCounter = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;
    }

    /** e.g., burstMillis = 5000. */
    public void startBurstSession(long burstMillis) {
        if (sessionActive) return;
        sessionActive = true;
        usingStepFallback = false;
        tryStartFused(burstMillis);
    }

    /** Always stop to free resources. */
    public void stopBurstSession() {
        if (!sessionActive) return;
        sessionActive = false;
        try { fusedClient.removeLocationUpdates(locationCallback); } catch (Exception ignored) {}
        if (usingStepFallback && sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    // ---- GPS preferred path ----
    private void tryStartFused(long burstMillis) {
        try {
            LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                    .setMinUpdateIntervalMillis(500L)
                    .setWaitForAccurateLocation(true)
                    .build();
            fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
            handler.postDelayed(this::stopIfRunning, burstMillis);
        } catch (SecurityException se) {
            startStepFallback(burstMillis);
        } catch (Exception e) {
            startStepFallback(burstMillis);
        }
    }

    private void stopIfRunning() {
        if (sessionActive) stopBurstSession();
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override public void onLocationResult(LocationResult result) {
            if (!sessionActive || result == null) return;
            Location loc = result.getLastLocation();
            if (loc != null) {
                float sp = loc.hasSpeed() ? loc.getSpeed() : 0f; // m/s
                if (sp > 0.2f) { // small noise filter
                    String activity = estimateActivityFromSpeed(sp);
                    Measurement m = new Measurement(sp, null, null, activity, "gps");
                    callback.onMeasurement(m);
                    stopBurstSession();
                    return;
                }
            }
            if (!usingStepFallback) startStepFallback(5000L);
        }

        @Override public void onLocationAvailability(LocationAvailability availability) {
            if (sessionActive && !availability.isLocationAvailable() && !usingStepFallback) {
                startStepFallback(5000L);
            }
        }
    };

    // ---- Step counter fallback ----
    private void startStepFallback(long burstMillis) {
        if (sensorManager == null || stepCounter == null) {
            Measurement m = new Measurement(0f, 0f, 0, "still", "steps");
            callback.onMeasurement(m);
            stopBurstSession();
            return;
        }
        usingStepFallback = true;
        lastStepCount = -1f;
        lastStepTimestampNs = 0L;
        sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_GAME);

        handler.postDelayed(() -> {
            if (sessionActive && usingStepFallback) {
                Measurement m = new Measurement(0f, 0f, 0, "still", "steps");
                callback.onMeasurement(m);
                stopBurstSession();
            }
        }, burstMillis);
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (!sessionActive || !usingStepFallback) return;
        if (e.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;

        float total = e.values[0];
        long ts = e.timestamp; // ns
        if (lastStepCount < 0f) {
            lastStepCount = total;
            lastStepTimestampNs  = ts;
            return;
        }
        float deltaSteps = total - lastStepCount;
        long  dtNs       = ts - lastStepTimestampNs;
        if (deltaSteps >= 1f && dtNs > 0) {
            float dtSec    = dtNs / 1_000_000_000f;
            float spm      = (deltaSteps / dtSec) * 60f;     // steps/min
            float stride   = 0.75f;                          // meters/step (tune if needed)
            float mps      = (deltaSteps / dtSec) * stride;  // speed estimate from steps
            String activity= estimateActivityFromCadence(spm);
            Integer bpm    = mapCadenceToTargetBpm(spm);

            Measurement m = new Measurement(mps, spm, bpm, activity, "steps");
            callback.onMeasurement(m);
            stopBurstSession();
        }
        lastStepCount = total;
        lastStepTimestampNs  = ts;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { /* no-op */ }

    // ---- Simple heuristics ----

    /** Speed-based activity (fallback when cadence not available). */
    private String estimateActivityFromSpeed(float mps) {
        // ~0.8 m/s ≈ 2.9 km/h walking threshold; ~2.5 m/s ≈ 9 km/h running threshold
        if (mps >= 2.5f) return "running";
        if (mps >= 0.8f) return "walking";
        return "still";
    }

    /** Cadence-based activity (preferred when available). */
    private String estimateActivityFromCadence(float spm) {
        // Rough thresholds; tune with field data if available
        if (spm >= 140f) return "running";
        if (spm >= 20f)  return "walking";
        return "still";
    }

    /** Map cadence to music BPM; clamp to a reasonable range [60..200]. */
    @Nullable
    private Integer mapCadenceToTargetBpm(float spm) {
        int bpm = Math.round(spm * 2f); // typical mapping
        if (bpm < 60)  bpm = 60;
        if (bpm > 200) bpm = 200;
        return bpm;
    }
}
