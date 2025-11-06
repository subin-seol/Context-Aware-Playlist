package com.comp90018.contexttunes.utils;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.comp90018.contexttunes.data.api.GooglePlacesAPI;
import com.google.android.libraries.places.api.model.Place;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to determine user's location context.
 * Checks tagged locations first, then falls back to Google Places API.
 */
public class LocationContextHelper {

    private static final String TAG = "LocationContextHelper";
    private static final double GEOFENCE_RADIUS_METERS = 100.0; // 100m radius for tagged locations

    public interface LocationContextCallback {
        void onLocationContextReady(@Nullable String placeTag, @NonNull List<String> nearbyPlaceTypes);
    }

    private final Context appContext;
    private final SettingsManager settingsManager;
    private final GooglePlacesAPI placesAPI;

    public LocationContextHelper(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.settingsManager = new SettingsManager(appContext);
        this.placesAPI = GooglePlacesAPI.getInstance(appContext);
    }

    /**
     * Determine location context from user's current location.
     * Strategy:
     * 1. Check if user is at a tagged location (Home, Gym, etc.)
     * 2. If not, fetch nearby places from Google Places API
     * 3. If that fails, return empty context
     */
    public void getLocationContext(@Nullable Location userLocation, @NonNull LocationContextCallback callback) {
        if (userLocation == null) {
            Log.w(TAG, "No location available, returning empty context");
            callback.onLocationContextReady(null, new ArrayList<>());
            return;
        }

        // Step 1: Check tagged locations
        String taggedPlace = getTaggedPlaceAtLocation(userLocation);
        if (taggedPlace != null) {
            Log.d(TAG, "User is at tagged location: " + taggedPlace);
            callback.onLocationContextReady(taggedPlace, new ArrayList<>());
            return;
        }

        // Step 2: Fetch nearby places from Google Places API
        Log.d(TAG, "Not at tagged location, fetching nearby places");
        placesAPI.getNearbyPlaces(userLocation, 300, new GooglePlacesAPI.NearbyPlacesCallback() {
            @Override
            public void onPlacesFound(List<Place> places) {
                List<String> placeTypes = extractPlaceTypes(places);
                Log.d(TAG, "Found nearby place types: " + placeTypes);
                callback.onLocationContextReady(null, placeTypes);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to fetch nearby places", e);
                callback.onLocationContextReady(null, new ArrayList<>());
            }
        });
    }

    /**
     * Check if user is at any tagged location (Home, Gym, Office, etc.).
     * Returns the tag name if within geofence radius, null otherwise.
     */
    @Nullable
    private String getTaggedPlaceAtLocation(@NonNull Location userLocation) {
        String[] tags = {"Home", "Gym", "Office", "Library", "Park", "Cafe"};

        for (String tag : tags) {
            String savedLocation = settingsManager.getLocation(tag);
            if (savedLocation != null) {
                String[] parts = savedLocation.split(",");
                if (parts.length == 2) {
                    try {
                        double tagLat = Double.parseDouble(parts[0]);
                        double tagLon = Double.parseDouble(parts[1]);

                        float[] distance = new float[1];
                        Location.distanceBetween(
                                userLocation.getLatitude(), userLocation.getLongitude(),
                                tagLat, tagLon,
                                distance
                        );

                        if (distance[0] <= GEOFENCE_RADIUS_METERS) {
                            return tag;
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Invalid coordinates for tag: " + tag, e);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Extract primary place types from Google Places results.
     * Returns simplified, human-readable place types.
     */
    @NonNull
    private List<String> extractPlaceTypes(@NonNull List<Place> places) {
        List<String> types = new ArrayList<>();

        for (Place place : places) {
            String primaryType = place.getPrimaryType();
            if (primaryType != null && !primaryType.isEmpty()) {
                // Convert technical types to human-readable
                String readable = convertPlaceType(primaryType);
                if (!types.contains(readable)) {
                    types.add(readable);
                }
            }
        }

        return types;
    }

    /**
     * Convert Google Places API types to human-readable names.
     */
    @NonNull
    private String convertPlaceType(@NonNull String apiType) {
        switch (apiType.toLowerCase()) {
            case "library":
                return "library";
            case "school":
            case "university":
                return "school";
            case "park":
                return "park";
            case "restaurant":
                return "restaurant";
            case "cafe":
                return "cafe";
            case "bar":
            case "night_club":
                return "nightlife";
            case "gym":
            case "stadium":
                return "gym";
            case "store":
            case "shopping_mall":
                return "shopping";
            default:
                return apiType;
        }
    }
}