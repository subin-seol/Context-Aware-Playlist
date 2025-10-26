package com.comp90018.contexttunes.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

public final class PermissionManager {

    private PermissionManager() {}

    // Centralized request codes
    public static final int REQ_LOCATION = 1001;
    public static final int REQ_CAMERA   = 1002;

    public static final int REQ_ACTIVITY = 1003;
    public static final int REQ_LOCATION_MULTI = 1004;

    // ==================== CHECKS ====================

    public static boolean hasLocationPermission(@NonNull Context ctx) {
        return ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasCameraPermission(@NonNull Context ctx) {
        return ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasAnyLocation(@NonNull Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasActivityRecognition(@NonNull Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ==================== REQUESTS (Fragment-first) ====================

    // Request from the Fragment so that Fragment.onRequestPermissionsResult gets invoked.
    public static void requestLocation(@NonNull Fragment fragment) {
        fragment.requestPermissions(
                new String[]{ android.Manifest.permission.ACCESS_FINE_LOCATION },
                REQ_LOCATION
        );
    }

    public static void requestCamera(@NonNull Fragment fragment) {
        fragment.requestPermissions(
                new String[]{ android.Manifest.permission.CAMERA },
                REQ_CAMERA
        );
    }

    public static void requestLocationFineAndCoarse(@NonNull Fragment fragment) {
        fragment.requestPermissions(
                new String[]{ Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION },
                REQ_LOCATION_MULTI
        );
    }

    public static void requestActivity(@NonNull Fragment fragment) {
        fragment.requestPermissions(new String[]{ Manifest.permission.ACTIVITY_RECOGNITION }, REQ_ACTIVITY);
    }

    // Convenience for the speed pipeline: AR + any location
    public static void requestSpeedSensing(@NonNull Fragment fragment) {
        java.util.ArrayList<String> need = new java.util.ArrayList<>();
        Context ctx = fragment.requireContext();

        if (!hasActivityRecognition(ctx)) {
            need.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (!hasAnyLocation(ctx)) {
            // request both; system will grant at most one depending on user choice
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!need.isEmpty()) {
            fragment.requestPermissions(need.toArray(new String[0]), REQ_LOCATION_MULTI);
        }
    }
}
