package com.comp90018.contexttunes.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.comp90018.contexttunes.R;
import com.comp90018.contexttunes.data.sensors.SpeedSensor;
import com.comp90018.contexttunes.utils.AppEvents;

/**
 * Foreground service that:
 * 1) Runs a short 5s speed sampling burst every PERIOD_MS (power-friendly)
 * 2) Supports an "immediate burst" command via ACTION_SPEED_SAMPLE_NOW
 * 3) Broadcasts ACTION_SPEED_UPDATE with speed/cadence/activity/BPM/source
 */
public class SpeedSensorService extends Service implements SpeedSensor.Callback {

    private static final String CHANNEL_ID = "ctx_speed_channel";
    private static final int NOTIF_ID = 12001;

    /** Change this to 10 * 60 * 1000L if you want every 10 minutes. */
    private static final long PERIOD_MS = 15 * 60 * 1000L; // every 15 minutes
    private static final long BURST_MS  = 5 * 1000L;       // 5 seconds burst

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeedSensor speedSensor;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (speedSensor != null) speedSensor.startBurstSession(BURST_MS);
            handler.postDelayed(this, PERIOD_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher) // replace with your drawable if needed
                .setContentTitle("Auto Mode · Speed Sensing")
                .setContentText("Short-burst speed sampling for playlist recommendation")
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, n);

        speedSensor = new SpeedSensor(getApplicationContext(), this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(tick);

        // Immediate 5s burst when ACTION_SPEED_SAMPLE_NOW is received
        if (intent != null && AppEvents.ACTION_SPEED_SAMPLE_NOW.equals(intent.getAction())) {
            if (speedSensor != null) speedSensor.startBurstSession(BURST_MS);
            handler.postDelayed(tick, PERIOD_MS);  // keep periodic schedule
        } else {
            // Normal periodic schedule: run now, then every PERIOD_MS
            handler.post(tick);
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(tick);
        if (speedSensor != null) speedSensor.stopBurstSession();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    // ---- SpeedSensor.Callback ----
    @Override public void onMeasurement(SpeedSensor.Measurement m) {
        float kmh = m.speedMps * 3.6f;

        // 1) Broadcast the result to the rest of the app
        Intent i = new Intent(AppEvents.ACTION_SPEED_UPDATE);
        i.putExtra(AppEvents.EXTRA_SPEED_MPS, m.speedMps);
        i.putExtra(AppEvents.EXTRA_SPEED_KMH, kmh);
        if (m.cadenceSpm != null) i.putExtra(AppEvents.EXTRA_CADENCE_SPM, m.cadenceSpm);
        if (m.activity   != null) i.putExtra(AppEvents.EXTRA_ACTIVITY,    m.activity);
        if (m.targetBpm  != null) i.putExtra(AppEvents.EXTRA_TARGET_BPM,  m.targetBpm);
        if (m.source     != null) i.putExtra(AppEvents.EXTRA_SOURCE,      m.source);
        sendBroadcast(i);

        // 2) Optional: update the foreground notification (useful during dev/demo)
        String sub = String.format("~%.1f km/h · %s", kmh, (m.activity != null ? m.activity : "unknown"));
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Auto Mode · Speed Sensing")
                .setContentText(sub)
                .setOngoing(true)
                .build();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, n);

        if (speedSensor != null) speedSensor.stopBurstSession();
    }

    @Override public void onError(String reason) {
        if (speedSensor != null) speedSensor.stopBurstSession();
    }

    private void createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Speed Sensor", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}
