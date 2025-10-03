package com.comp90018.contexttunes.data.sensors;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

// Main API to access location from google play services
import com.google.android.gms.location.FusedLocationProviderClient;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

// Java's functional interface so we can use lambda expressions for callbacks
import java.util.function.Consumer;

/**
 * LocationSensor provides a simple interface to get the device's current location (latitude/longitude).
 * Usage:
 *   LocationSensor sensor = new LocationSensor(activity);
 *   sensor.getCurrentLocation((location) -> { ... });
 */
public class LocationSensor {

    // Used when asking user for location permission
    private static final int REQUEST_CODE_LOCATION = 1001;

    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    public LocationSensor(@NonNull Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    /**
     * Requests the current location and returns it via the callback.
     * Handles permission requests automatically.
     *
     * For active sensing (current implementation):
     *   - Always requests a new location update when called.
     *   - Good for on-demand, user-triggered location fetches.
     *
     * To change to a hybrid approach for passive/background sensing:
     *   - Implement a method to start continuous location updates (e.g., startBackgroundLocationUpdates()).
     *   - Use a LocationCallback that receives updates at a regular interval or when location changes.
     *   - Store or process location updates in the background as needed.
     *   - Stop updates when no longer needed (e.g., stopBackgroundLocationUpdates()).
     *   - Consider using foreground services for background location on Android 10+.
     *   - Ensure you request ACCESS_BACKGROUND_LOCATION permission for background updates (Android 10+).
     *
     * Example for hybrid usage:
     *   // For background updates
     *   locationSensor.startBackgroundLocationUpdates(location -> { ... });
     *   // To stop
     *   locationSensor.stopBackgroundLocationUpdates();
     *
     * See Android documentation for best practices:
     * https://developer.android.com/training/location
     *
     * @param callback Consumer<Location> to receive the location result.
     */

    /** Requires ACCESS_FINE_LOCATION to already be granted. */
    public void getCurrentLocation(@NonNull Consumer<Location> callback) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationSensor", "Permission not granted. Call PermissionManager.requestLocation() first.");
            callback.accept(null);
            return;
        }
        requestLocationUpdate(callback);
    }

    // Request a location update if last location is unavailable
    private void requestLocationUpdate(@NonNull Consumer<Location> callback) {
        // Use the new LocationRequest.Builder API (deprecated methods replaced)
        LocationRequest locationRequest = new LocationRequest.Builder(
                1000L // interval in milliseconds
        )
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .setMaxUpdates(1) // Only need one update
        .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    callback.accept(location);
                }
                // Remove updates after receiving one, good for battery life
                fusedLocationClient.removeLocationUpdates(this);
            }
        };

        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            } catch (SecurityException se) {
                Log.e("LocationSensor", "Permission revoked mid-call", se);
                callback.accept(null);
            }
        } else {
            Log.e("LocationSensor", "Location permission not granted. Cannot request location updates.");
            callback.accept(null);
        }
    }

//    /**
//     * Call this from your Activity's onRequestPermissionsResult to handle permission result.
//     * Example:
//     *   @Override
//     *   public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//     *       locationSensor.handlePermissionResult(requestCode, grantResults, (location) -> { ... });
//     *   }
//     */
//    public void handlePermissionResult(int requestCode, @NonNull int[] grantResults, @NonNull Consumer<Location> callback) {
//        if (requestCode == REQUEST_CODE_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            getCurrentLocation(callback);
//        }
//    }
}
