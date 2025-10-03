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
}
