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

//    // ===== Permission helper for the GO flow =====
//    public static final class Perms {
//        private Perms() {}
//
//        public static final int REQ_SPEED = 4242;
//
//        /**
//         * Ensure ACTIVITY_RECOGNITION (Android 10+) and at least one of location permissions.
//         * If already granted, invoke onGranted immediately; otherwise trigger the sheet.
//         */
//        public static void ensureForSpeed(@NonNull Activity activity, @NonNull Runnable onGranted) {
//            java.util.ArrayList<String> need = new java.util.ArrayList<>();
//
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                if (ContextCompat.checkSelfPermission(activity,
//                        android.Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
//                    need.add(android.Manifest.permission.ACTIVITY_RECOGNITION);
//                }
//            }
//
//            boolean fine = ContextCompat.checkSelfPermission(activity,
//                    android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
//            boolean coarse = ContextCompat.checkSelfPermission(activity,
//                    android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
//
//            if (!fine && !coarse) {
//                need.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
//                need.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
//            }
//
//            if (need.isEmpty()) {
//                onGranted.run();
//            } else {
//                ActivityCompat.requestPermissions(activity,
//                        need.toArray(new String[0]), REQ_SPEED);
//            }
//        }
//
//        /**
//         * Forward onRequestPermissionsResult here if you want to start measuring immediately
//         * after the user grants permissions from the first GO click.
//         */
//        public static boolean handleResult(@NonNull Activity activity,
//                                           int requestCode,
//                                           @NonNull String[] permissions,
//                                           @NonNull int[] grantResults,
//                                           @NonNull Runnable onGranted) {
//            if (requestCode != REQ_SPEED) return false;
//            boolean locGranted =
//                    ContextCompat.checkSelfPermission(activity,
//                            android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
//                            || ContextCompat.checkSelfPermission(activity,
//                            android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
//            if (locGranted) onGranted.run();
//            return true;
//        }
//    }
//}