package com.comp90018.contexttunes.utils;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Central place for actions and extras used by the speed/cadence pipeline.
 * Emitted by SpeedSensorService; received by HomeFragment / PlaylistFragment.
 */
public final class AppEvents {
    private AppEvents() {
    }

    // ===== Actions =====
    /**
     * Emitted ~1 Hz during a measuring window, plus a final frame at the end.
     */
    public static final String ACTION_SPEED_UPDATE =
            "com.comp90018.contexttunes.ACTION_SPEED_UPDATE";

    /**
     * Ask the service to perform an immediate measuring window (e.g. 30 seconds)
     */
    public static final String ACTION_SPEED_SAMPLE_NOW =
            "com.comp90018.contexttunes.ACTION_SPEED_SAMPLE_NOW";

    // Window config
    public static final String EXTRA_WINDOW_SECONDS = "window_seconds";

    // ===== Extras =====
    /**
     * float (km/h). Prefer this in UI.
     */
    public static final String EXTRA_SPEED_KMH = "extra_speed_kmh";
    /**
     * float (steps/min). Use Float.isNaN(...) to detect "not available".
     */
    public static final String EXTRA_CADENCE_SPM = "extra_cadence_spm";
    /**
     * String in {"-", "still", "walking", "running", "wheels"}.
     */
    public static final String EXTRA_ACTIVITY = "extra_activity";
    /**
     * "steps" or "gps" (optional, diagnostics).
     */
    public static final String EXTRA_SOURCE = "extra_source";
    /**
     * long, monotonic sequence number.
     */
    public static final String EXTRA_SEQ = "extra_seq";
    /**
     * boolean, true for the final aggregated frame at the end.
     */
    public static final String EXTRA_IS_FINAL = "extra_is_final";
}