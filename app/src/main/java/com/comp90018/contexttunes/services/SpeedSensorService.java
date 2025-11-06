package com.comp90018.contexttunes.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.comp90018.contexttunes.BuildConfig;
import com.comp90018.contexttunes.R;
import com.comp90018.contexttunes.data.sensors.SpeedSensor;
import com.comp90018.contexttunes.utils.AppEvents;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/**
 * 20 second measuring window with clear priority:
 *   1) Prefer step-derived speed (cadence EMA) when step pipeline is alive.
 *   2) Only if steps are NOT alive, fall back to GPS speed (gated & smoothed).
 *
 * Activity label depends ONLY on speed thresholds (more realistic cutoffs).
 * "wheels" appears only at high speed AND no steps alive (sustained) to avoid false positives.
 *
 * Emits ACTION_SPEED_UPDATE ~1Hz during the window and once more at the end (EXTRA_IS_FINAL=true).
 */
public class SpeedSensorService extends Service {
    private static final String TAG   = "SpeedSensorService";
    private static final String CH_ID = "ctx_speed_channel";
    private static final int    NOTIF_ID = 42;

    // ===== Window / pacing =====
    private static final long MEASURE_WINDOW_MS = 10_000L;  // 10 seconds for now
    private static final long LOOP_TICK_MS      = 200L;    // internal loop cadence
    private static final long EMIT_PERIOD_MS    = 1_000L;  // throttle broadcasts to ~1Hz

    // ===== Scientifically grounded speed thresholds (km/h) =====
    // References: gait studies & common run pace ranges
    // - STILL:    < ~1.3 km/h (postural sway / micro-movements shouldn't count as walking)
    // - WALKING:  ~1.3–8.3 km/h (covers slow stroll to brisk walk)
    // - RUNNING:  >= ~8.3 km/h (≈ 7:14 min/km pace)
    // - WHEELS:   >= 14 km/h sustained, and no steps alive (bike/scooter/skates, etc.)
    private static final float STILL_MAX_KMH   = 1.3f;
    private static final float RUN_MIN_KMH     = 8.3f;
    private static final float WHEEL_MIN_KMH   = 14.0f;

    // Hysteresis (km/h) and label hold (ms) to reduce flicker around boundaries.
    private static final float HYSTERESIS_KMH   = 0.6f;
    private static final long  LABEL_MIN_HOLD_MS= 800L;

    // Wheels requires a brief sustained period to confirm.
    private static final long  WHEEL_CONFIRM_MS = 2_000L;

    // ===== GPS guards & smoothing =====
    private static final double GPS_EMA_ALPHA   = 0.30;  // EMA on km/h
    private static final float  ACCURACY_MAX_M  = 25f;   // discard poor fixes
    private static final float  STILL_EPS_M     = 1.5f;  // tiny move clamp
    private static final double NEAR_ZERO_KMH   = 0.30;  // clamp near-zero jitter to 0

    private Handler handler;
    private FusedLocationProviderClient fused;
    private LocationCallback gpsCb;
    private Location lastLoc;

    // Step pipeline (cadence + cadence→speed heuristic)
    private SpeedSensor speedSensor;

    // Window state
    private boolean windowActive = false;
    private long    windowEndMono = 0L;

    // Emit state
    private long lastEmitWall = 0L;
    private long seq          = 0L;

    // Chosen speed sources (km/h)
    private float  stepSpeedKmh = 0f;   // from cadence EMA
    private float  gpsSpeedKmh  = 0f;   // EMA of GPS speed
    private boolean gpsGood     = false;
    private boolean stepsAlive  = false;

    // Label stability
    private String lastLabel = "-";
    private long   lastLabelAt = 0L;
    private long   wheelSince  = 0L;

    @Override public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        fused   = LocationServices.getFusedLocationProviderClient(this);

        // Foreground notification channel + tile
        createNotifChannelIfNeeded();
        startForeground(NOTIF_ID, buildNotif("Idle"));

        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);

        boolean hasAR = androidx.core.content.ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean hwPresent =
                sm.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_DETECTOR) != null
                        || sm.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)  != null;

        // Only allow accelerometer fallback if we can't use HW steps (no AR perm OR no HW)
        boolean allowAccelFallback = !(hasAR && hwPresent);

        speedSensor = new com.comp90018.contexttunes.data.sensors.SpeedSensor(sm, allowAccelFallback);
        speedSensor.start();

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "stepsPolicy: AR=" + hasAR
                    + " hwPresent=" + hwPresent
                    + " allowAccelFallback=" + allowAccelFallback);
        }

        // Start the control loop
        handler.post(loop);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && AppEvents.ACTION_SPEED_SAMPLE_NOW.equals(intent.getAction())) {
            armNewWindow();  // the method that resets state and starts the 20s window
        }
        return START_STICKY;
    }

    private void armNewWindow() {
        // Arm a fresh 20s window
        windowActive  = true;
        windowEndMono = SystemClock.elapsedRealtime() + MEASURE_WINDOW_MS;

        seq = 0; lastEmitWall = 0;
        lastLabel = "-"; lastLabelAt = 0L; wheelSince = 0L;

        stepSpeedKmh = 0f; gpsSpeedKmh = 0f; gpsGood = false; lastLoc = null;

        if (speedSensor != null) speedSensor.resetDebugCounts();
        updateNotif("Measuring 10s…");
        if (BuildConfig.DEBUG) Log.d(TAG, "Measuring window started");
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            long nowMono = SystemClock.elapsedRealtime();
            long nowWall = System.currentTimeMillis();

            if (!windowActive) {
                stopGpsIfAny();
                handler.postDelayed(this, LOOP_TICK_MS);
                return;
            }

            // Window end → emit a final frame and go idle
            if (nowMono >= windowEndMono) {
                float chosen = chooseSpeedKmh();
                String label = classifyBySpeed(chosen, stepsAlive, nowWall);

                emit(label,
                        chosen,
                        stepsAlive ? (float) speedSensor.pollCadenceBPM() : Float.NaN,
                        sourceOf(chosen),
                        true,
                        nowWall);
                windowActive = false;
                stopGpsIfAny();

                // Kill the ongoing notification and this service instance
                stopForeground(true);
                stopSelf();

                if (BuildConfig.DEBUG) Log.d(TAG, "Measuring window ended");
                handler.postDelayed(this, LOOP_TICK_MS);
                return;
            }

            // 1) STEP-FIRST: if cadence alive → update stepSpeed, and keep GPS off
            int spm = speedSensor.pollCadenceBPM();
            stepsAlive = speedSensor.isCadenceAlive();
            if (stepsAlive && spm > 0) {
                stopGpsIfAny(); // not needed while steps are alive
                stepSpeedKmh = (float) SpeedSensor.estimateSpeedFromCadenceEma(spm, stepSpeedKmh);
            } else {
                // 2) GPS FALLBACK: only when steps are not alive and we have location permission
                if (ActivityCompat.checkSelfPermission(SpeedSensorService.this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(SpeedSensorService.this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    // Permissions present → (re)start GPS if needed
                    startGpsIfNeeded();
                } else {
                    stopGpsIfAny();
                }
            }

            // Choose speed and classify label (label depends ONLY on speed)
            float  chosen = chooseSpeedKmh();
            String label  = classifyBySpeed(chosen, stepsAlive, nowWall);

            Log.d(TAG, "src=" + sourceOf(chosen) + " detail=" + speedSensor.debugSource()
                    + " spm=" + (stepsAlive ? spm : -1) + " kmh=" + chosen);

            maybeEmit(label,
                    chosen,
                    stepsAlive ? (float) spm : Float.NaN,
                    sourceOf(chosen),
                    nowWall);

            handler.postDelayed(this, LOOP_TICK_MS);
        }
    };

    /** Pick the active speed: step-derived when steps are alive; otherwise GPS if valid; else 0. */
    private float chooseSpeedKmh() {
        if (stepsAlive) return stepSpeedKmh;
        return gpsGood ? gpsSpeedKmh : 0f;
    }

    /** Debug source string. */
    private String sourceOf(float chosen) {
        return stepsAlive ? "steps" : "gps";
    }

    // ===== GPS =====

    @RequiresPermission(anyOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    })
    private void startGpsIfNeeded() {
        if (gpsCb != null) return; // already running

        boolean fine = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED;

        LocationRequest.Builder b = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, /* interval */ 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(0.5f)
                .setWaitForAccurateLocation(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL);
        }
        LocationRequest req = b.build();

        gpsCb = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) handleLocation(loc, System.currentTimeMillis());
            }
        };

        //noinspection MissingPermission
        fused.requestLocationUpdates(req, gpsCb, Looper.getMainLooper());

        // One-shot for faster first fix inside the window
        try {
            CancellationTokenSource cts = new CancellationTokenSource();
            fused.getCurrentLocation(
                    fine ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cts.getToken()
            ).addOnSuccessListener(loc -> { if (loc != null) handleLocation(loc, System.currentTimeMillis()); });
        } catch (Throwable ignore) {}
    }

    private void stopGpsIfAny() {
        if (gpsCb != null) {
            fused.removeLocationUpdates(gpsCb);
            gpsCb = null;
        }
        lastLoc = null;
        // keep gpsSpeedKmh so UI can still display last EMA within the window
    }

    /** Convert a location fix into an EMA speed with guards (accuracy / tiny move / near-zero). */
    private void handleLocation(Location loc, long nowWall) {
        // 1) accuracy gate
        if (loc.hasAccuracy() && loc.getAccuracy() > ACCURACY_MAX_M) return;

        // 2) raw speed (prefer sensor speed; else distance-over-time)
        double vMs;
        float  moved = 0f;
        long   dtMs  = 0L;

        if (loc.hasSpeed()) {
            vMs = Math.max(0.0, loc.getSpeed());
        } else if (lastLoc != null) {
            moved = loc.distanceTo(lastLoc);
            dtMs  = Math.max(1L, loc.getTime() - lastLoc.getTime());
            vMs   = dtMs > 0 ? moved / (dtMs / 1000.0) : 0.0;
        } else {
            vMs = 0.0;
        }

        // 3) tiny movement clamp (reduce jitter to 0)
        if (lastLoc != null && moved > 0 && moved < STILL_EPS_M && dtMs >= 800) {
            vMs = 0.0;
        }

        // 4) to km/h + near-zero clamp
        double vKmh = vMs * 3.6;
        if (vKmh < NEAR_ZERO_KMH) vKmh = 0.0;

        // 5) EMA
        gpsSpeedKmh = (float) (GPS_EMA_ALPHA * vKmh + (1.0 - GPS_EMA_ALPHA) * gpsSpeedKmh);
        gpsGood     = true;
        lastLoc     = loc;
    }

    // ===== Classify & emit =====

    /**
     * Label depends ONLY on speed bands; “wheels” requires high speed AND no steps alive,
     * sustained for WHEEL_CONFIRM_MS (or immediately if very fast).
     */
    private String classifyBySpeed(float vKmh, boolean stepsAliveNow, long nowWall) {
        // wheels (sustained, no steps)
        if (!stepsAliveNow && vKmh >= WHEEL_MIN_KMH) {
            if (wheelSince == 0L) wheelSince = nowWall;
            if ((nowWall - wheelSince) >= WHEEL_CONFIRM_MS || vKmh >= 20f) {
                return applyHold("wheels", nowWall);
            }
            // While confirming, fall through to running for UI continuity.
        } else {
            wheelSince = 0L;
        }

        // hysteresis around boundaries to avoid flicker
        if ("running".equals(lastLabel) && vKmh >= (RUN_MIN_KMH - HYSTERESIS_KMH)) {
            return applyHold("running", nowWall);
        }
        if ("walking".equals(lastLabel)
                && vKmh >= (STILL_MAX_KMH + HYSTERESIS_KMH)
                && vKmh <  (RUN_MIN_KMH   + HYSTERESIS_KMH)) {
            return applyHold("walking", nowWall);
        }

        if (vKmh < STILL_MAX_KMH)  return applyHold("still",   nowWall);
        if (vKmh >= RUN_MIN_KMH)   return applyHold("running", nowWall);
        return applyHold("walking", nowWall);
    }

    /** Enforce a minimum hold time when switching labels. */
    private String applyHold(String candidate, long nowWall) {
        if (!candidate.equals(lastLabel) && (nowWall - lastLabelAt) < LABEL_MIN_HOLD_MS) {
            return lastLabel;
        }
        if (!candidate.equals(lastLabel)) {
            lastLabel = candidate;
            lastLabelAt = nowWall;
        }
        return lastLabel;
    }

    private void maybeEmit(String label, float speedKmh, Float cadenceSpm, String source, long nowWall) {
        if ((nowWall - lastEmitWall) < EMIT_PERIOD_MS) return;
        emit(label, speedKmh, cadenceSpm, source, false, nowWall);
    }

    /** Broadcast one frame to both LocalBroadcastManager and the global receiver path. */
    private void emit(String label,
                      float speedKmh,
                      Float cadenceSpm,
                      String source,
                      boolean isFinal,
                      long nowWall) {
        lastEmitWall = nowWall;
        seq++;

        Intent i = new Intent(AppEvents.ACTION_SPEED_UPDATE);
        i.putExtra(AppEvents.EXTRA_SPEED_KMH,   speedKmh);
        i.putExtra(AppEvents.EXTRA_CADENCE_SPM, cadenceSpm == null ? Float.NaN : cadenceSpm);
        i.putExtra(AppEvents.EXTRA_ACTIVITY,    label == null ? "-" : label);
        i.putExtra(AppEvents.EXTRA_SOURCE,      source);
        i.putExtra(AppEvents.EXTRA_SEQ,         seq);
        i.putExtra(AppEvents.EXTRA_IS_FINAL,    isFinal);

        LocalBroadcastManager.getInstance(this).sendBroadcast(i);

        if (BuildConfig.DEBUG) {
            Log.d(TAG, (isFinal ? "emit[FINAL]" : "emit")
                    + " seq=" + seq
                    + " label=" + label
                    + " kmh=" + String.format("%.2f", speedKmh)
                    + " spm=" + (cadenceSpm == null ? "NaN" : String.format("%.0f", cadenceSpm))
                    + " src=" + source);
        }
    }

    // ===== Foreground notification helpers =====
    private void createNotifChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "ContextTunes Speed", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Speed sensing");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotif(String subtitle) {
        Intent open = new Intent(this, com.comp90018.contexttunes.MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                this, 0, open, android.app.PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CH_ID)
                .setSmallIcon(R.drawable.ic_stat_speed)
                .setContentTitle("ContextTunes")
                .setContentText("Speed sensing • " + subtitle)
                .setOngoing(true)
                .setContentIntent(pi)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void updateNotif(String subtitle) {
        Notification n = buildNotif(subtitle);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, n);
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopGpsIfAny();
        if (speedSensor != null) speedSensor.stop();
        super.onDestroy();
    }
}