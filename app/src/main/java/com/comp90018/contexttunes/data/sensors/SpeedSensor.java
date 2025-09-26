package com.comp90018.contexttunes.data.sensors;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * 5s burst sampler with STEPS-FIRST and GPS fallback.
 * - Filters poor GPS samples and applies a low-pass smoothing.
 * - Emits exactly one Measurement when the burst ends.
 */
public class SpeedSensor {

    public static class Measurement {
        public float  speedMps;                 // final speed (m/s)
        @Nullable public Float cadenceSpm;      // steps/min (if available)
        public String activity;                 // "still" | "walking" | "running"
        public String source;                   // "steps" | "gps"
    }

    public interface Callback {
        void onMeasurement(Measurement m);
        void onError(Exception e);
    }

    private static final String TAG = "SpeedSensor";

    // -------- Universal thresholds (km/h) --------
    private static final float STILL_MAX_KMH = 0.7f;
    private static final float WALK_MAX_KMH  = 6.0f;
    private static final float RUN_MAX_KMH   = 15.0f; // >=15 => WHEELS (GPS-only)

    // -------- GPS quality / smoothing --------
    private static final float ACCURACY_M_MAX       = 25f;  // ignore if worse
    private static final float SPEED_ACC_MPS_MAX    = 2.0f; // ignore if worse (when available)
    private static final float MIN_DT_S             = 0.4f; // compute-by-distance min time
    private static final float MIN_DISPLACEMENT_M   = 1.5f; // compute-by-distance min distance
    private static final float LPF_ALPHA            = 0.6f; // low-pass smoothing factor

    // -------- Cadence heuristics --------
    private static final float CADENCE_MIN_SPM   = 20f;   // ignore cadence below this
    private static final float STEP_LEN_WALK_M   = 0.70f; // lower bound step length
    private static final float STEP_LEN_RUN_M    = 1.20f; // upper bound step length
    private static final float CAD_WALK_REF      = 100f;  // start interpolation
    private static final float CAD_RUN_REF       = 160f;  // end interpolation

    private final Context app;
    private final Handler ui = new Handler(Looper.getMainLooper());

    // GPS
    private final FusedLocationProviderClient flp;
    private LocationCallback locCb;
    private boolean anyGps = false;
    private float   smoothedMps = 0f;
    private boolean smoothInit  = false;
    private Location lastLoc = null;
    private long     lastTimeMs = 0L;

    // Steps
    private final SensorManager sm;
    private SensorEventListener stepListener;
    private Sensor stepCounter;
    private float  startSteps = -1f;
    private float  endSteps   = -1f;

    private boolean running = false;

    public SpeedSensor(Context applicationContext) {
        this.app = applicationContext.getApplicationContext();
        this.flp = LocationServices.getFusedLocationProviderClient(app);
        this.sm  = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        this.stepCounter = (sm != null) ? sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;
    }

    public void startBurstSession(long durationMs, Callback cb) {
        if (running) {
            Log.w(TAG, "burst already running");
            return;
        }
        running = true;

        // reset state
        anyGps = false;
        smoothedMps = 0f; smoothInit = false;
        lastLoc = null; lastTimeMs = 0L;
        startSteps = -1f; endSteps = -1f;

        // ---- STEPS (priority) ----
        if (sm != null && stepCounter != null) {
            stepListener = new SensorEventListener() {
                @Override public void onSensorChanged(SensorEvent e) {
                    if (e.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                        float v = e.values[0];
                        if (startSteps < 0f) startSteps = v;
                        endSteps = v;
                    }
                }
                @Override public void onAccuracyChanged(Sensor s, int a) {}
            };
            sm.registerListener(stepListener, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Log.d(TAG, "no step-counter sensor on this device/emulator");
        }

        // ---- GPS (fallback) ----
        boolean locPerm =
                ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (locPerm) {
            LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 800L)
                    .setMinUpdateIntervalMillis(500L)
                    .build();
            locCb = new LocationCallback() {
                @Override public void onLocationResult(LocationResult result) {
                    if (result == null) return;
                    for (Location loc : result.getLocations()) {
                        if (!isUsable(loc)) continue;
                        anyGps = true;

                        float sp = 0f; // m/s
                        if (loc.hasSpeed()) {
                            sp = Math.max(0f, loc.getSpeed());
                        } else if (lastLoc != null && lastTimeMs > 0L) {
                            long now = System.currentTimeMillis();
                            float dt = (now - lastTimeMs) / 1000f;
                            if (dt >= MIN_DT_S) {
                                float d = loc.distanceTo(lastLoc);
                                if (d >= MIN_DISPLACEMENT_M) {
                                    sp = Math.max(0f, d / dt);
                                }
                            }
                        }

                        // low-pass smoothing
                        if (!smoothInit) {
                            smoothedMps = sp;
                            smoothInit = true;
                        } else {
                            smoothedMps = smoothedMps + LPF_ALPHA * (sp - smoothedMps);
                        }

                        lastLoc = loc;
                        lastTimeMs = System.currentTimeMillis();
                    }
                }
            };
            try {
                flp.requestLocationUpdates(req, locCb, Looper.getMainLooper());
            } catch (Exception e) {
                Log.w(TAG, "requestLocationUpdates failed: " + e);
            }
        } else {
            Log.w(TAG, "no location permission; GPS disabled for this burst.");
        }

        // ---- stop & emit once at the end ----
        ui.postDelayed(() -> {
            stopInternal();

            Measurement m = new Measurement();
            // cadence
            Float cadenceSpm = null;
            if (startSteps >= 0f && endSteps >= 0f && endSteps > startSteps && durationMs > 0) {
                float delta = endSteps - startSteps; // steps in burst
                cadenceSpm = delta * (60_000f / durationMs); // steps/min
            }
            m.cadenceSpm = cadenceSpm;

            if (cadenceSpm != null && cadenceSpm >= CADENCE_MIN_SPM) {
                // --- STEPS-FIRST ---
                float stepLen = stepLengthFromCadence(cadenceSpm);
                float mpsFromCadence = (cadenceSpm / 60f) * stepLen;

                m.speedMps = mpsFromCadence;
                float kmh = m.speedMps * 3.6f;
                if (kmh < STILL_MAX_KMH)      m.activity = "still";
                else if (kmh < WALK_MAX_KMH)  m.activity = "walking";
                else                           m.activity = "running";
                m.source = "steps";
            } else if (anyGps) {
                // --- GPS fallback (smoothed) ---
                m.speedMps = Math.max(0f, smoothedMps);
                float kmh = m.speedMps * 3.6f;
                if (kmh < STILL_MAX_KMH)      m.activity = "still";
                else if (kmh < WALK_MAX_KMH)  m.activity = "walking";
                else                           m.activity = "running"; // wheels tagging happens in service
                m.source = "gps";
            } else {
                // absolutely nothing -> STILL
                m.speedMps = 0f;
                m.activity = "still";
                m.source   = "gps";
            }

            try { cb.onMeasurement(m); }
            catch (Exception e) { Log.e(TAG, "callback failed", e); cb.onError(e); }

        }, durationMs);
    }

    private void stopInternal() {
        if (!running) return;
        running = false;

        if (locCb != null) {
            try { flp.removeLocationUpdates(locCb); } catch (Exception ignore) {}
            locCb = null;
        }
        if (sm != null && stepListener != null) {
            try { sm.unregisterListener(stepListener); } catch (Exception ignore) {}
            stepListener = null;
        }
    }

    // ---- helpers ----
    private boolean isUsable(Location loc) {
        if (loc == null) return false;
        if (loc.hasAccuracy() && loc.getAccuracy() > ACCURACY_M_MAX) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (loc.hasSpeedAccuracy() && loc.getSpeedAccuracyMetersPerSecond() > SPEED_ACC_MPS_MAX) {
                return false;
            }
        }
        return true;
    }

    private static float stepLengthFromCadence(float cadenceSpm) {
        // Linear interpolation between walk and run step length based on cadence 100..160 spm
        float t = (cadenceSpm - CAD_WALK_REF) / (CAD_RUN_REF - CAD_WALK_REF);
        t = Math.max(0f, Math.min(1f, t));
        return STEP_LEN_WALK_M + t * (STEP_LEN_RUN_M - STEP_LEN_WALK_M);
    }
}
