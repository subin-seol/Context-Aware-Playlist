package com.comp90018.contexttunes.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.comp90018.contexttunes.R;
import com.comp90018.contexttunes.data.sensors.SpeedSensor;
import com.comp90018.contexttunes.utils.AppEvents;

/**
 * Foreground service that performs an on-demand 5s speed burst.
 * - Steps-first; GPS fallback.
 * - Emits ACTION_SPEED_UPDATE with a 4-way tag and target BPM.
 */
public class SpeedSensorService extends Service {

    private static final String TAG = "SpeedSensorService";
    private static final String CHANNEL_ID = "ctx_speed_channel";
    private static final int NOTI_ID = 1138;

    // --- Same universal thresholds (km/h) ---
    private static final float STILL_MAX_KMH = 0.7f;
    private static final float WALK_MAX_KMH  = 6.0f;
    private static final float RUN_MAX_KMH   = 15.0f;

    // BPM mapping
    private static final float CADENCE_MULTIPLIER = 2.0f; // bpm ~= cadence*2
    private static final int   BPM_MIN = 60;
    private static final int   BPM_MAX = 190;
    private static final float CADENCE_MIN_SPM = 20f;

    private SpeedSensor sensor;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();

        int smallIcon = R.drawable.ic_stat_speed; // ensure this drawable exists
        Notification noti = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setContentTitle("Auto Mode · Speed Sensing")
                .setContentText("Ready")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(NOTI_ID, noti);
        Log.d(TAG, "startForeground posted");

        sensor = new SpeedSensor(getApplicationContext());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null) ? intent.getAction() : null;
        Log.d(TAG, "onStartCommand action=" + action);
        if (AppEvents.ACTION_SPEED_SAMPLE_NOW.equals(action)) {
            Log.d(TAG, "runBurst5s()");
            runBurst5s();
        }
        return START_STICKY;
    }

    private void emit(Intent i) {
        // Use an explicit package-scoped broadcast so only our app receives it
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void runBurst5s() {
        sensor.startBurstSession(5000L, new SpeedSensor.Callback() {
            @Override public void onMeasurement(SpeedSensor.Measurement m) {
                float kmh = m.speedMps * 3.6f;
                String tag = classifyTag(kmh, m.cadenceSpm, m.source);
                int targetBpm = bpmFromCadenceOrSpeed(m.cadenceSpm, kmh);

                Intent i = new Intent(AppEvents.ACTION_SPEED_UPDATE);
                i.putExtra(AppEvents.EXTRA_SPEED_MPS, m.speedMps);
                i.putExtra(AppEvents.EXTRA_SPEED_KMH, kmh);
                if (m.cadenceSpm != null) i.putExtra(AppEvents.EXTRA_CADENCE_SPM, m.cadenceSpm);
                i.putExtra(AppEvents.EXTRA_ACTIVITY, m.activity);
                i.putExtra(AppEvents.EXTRA_TARGET_BPM, targetBpm);
                i.putExtra(AppEvents.EXTRA_SOURCE, m.source);
                i.putExtra(AppEvents.EXTRA_SPEED_TAG, tag);

                Log.d(TAG, "emit: " + String.format("~%.2f km/h · %s (src=%s)", kmh, tag, m.source));
                emit(i);

                updateNotification(String.format("~%.1f km/h · %s", kmh, tag));
            }
            @Override public void onError(Exception e) {
                Log.e(TAG, "measurement error", e);
                updateNotification("No measurement");
            }
        });
    }

    private void updateNotification(String subtitle) {
        int smallIcon = R.drawable.ic_stat_speed;
        Notification noti = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setContentTitle("Auto Mode · Speed Sensing")
                .setContentText(subtitle)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(NOTI_ID, noti);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Speed Sensor", NotificationManager.IMPORTANCE_LOW);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    // ---- tag + BPM mapping (must mirror thresholds above) ----
    private static String classifyTag(float speedKmh, @Nullable Float cadenceSpm, String source) {
        // Always treat very low speed as STILL
        if (speedKmh < STILL_MAX_KMH) return "STILL";

        // Prefer cadence when present: never output WHEELS from steps
        if (cadenceSpm != null && cadenceSpm >= CADENCE_MIN_SPM) {
            return (speedKmh >= WALK_MAX_KMH) ? "RUN" : "WALK";
        }

        // GPS-only: allow WHEELS at high speed
        if (speedKmh >= RUN_MAX_KMH && !"steps".equals(source)) return "WHEELS";
        if (speedKmh >= WALK_MAX_KMH) return "RUN";
        return "WALK";
    }

    private static int bpmFromCadenceOrSpeed(@Nullable Float cadenceSpm, float speedKmh) {
        int bpm;
        if (cadenceSpm != null && cadenceSpm >= CADENCE_MIN_SPM) {
            bpm = Math.round(cadenceSpm * CADENCE_MULTIPLIER);
        } else {
            if (speedKmh < STILL_MAX_KMH)      bpm = 70;   // STILL
            else if (speedKmh < WALK_MAX_KMH)  bpm = 100;  // WALK
            else if (speedKmh < RUN_MAX_KMH)   bpm = 155;  // RUN
            else                               bpm = 110;  // WHEELS cruising
        }
        return Math.max(BPM_MIN, Math.min(BPM_MAX, bpm));
    }
}
