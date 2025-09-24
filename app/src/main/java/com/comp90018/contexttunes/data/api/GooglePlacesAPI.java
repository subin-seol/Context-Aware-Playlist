package com.comp90018.contexttunes.data.api;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchNearbyRequest;

import java.util.Arrays;
import java.util.List;

public class GooglePlacesAPI {

    private PlacesClient placesClient;

    public GooglePlacesAPI(Context context, String apiKey) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("DEFAULT_API_KEY")) {
            throw new IllegalArgumentException("No valid API key provided to GooglePlacesAPI");
        }

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context, apiKey);
        }
        this.placesClient = Places.createClient(context);
        Log.d("GooglePlacesAPI", "Places API initialized");
    }


    /**
     * Get nearby places around the given location.
     *
     * @param location Current user location
     * @param radius   Search radius in meters (e.g. 500 = 500m)
     * @param callback A callback to return the list of Place objects
     */
    // From: https://developers.google.com/maps/documentation/places/android-sdk/nearby-search
    public void getNearbyPlaces(Location location, int radius, NearbyPlacesCallback callback) {
        if (placesClient == null) {
            Log.e("GooglePlacesAPI", "Places client not initialized");
            callback.onError(new IllegalStateException("Places client not initialized"));
            return;
        }

        LatLng center = new LatLng(location.getLatitude(), location.getLongitude());
        CircularBounds circle = CircularBounds.newInstance(center, radius);

        // Fields you want to retrieve from each place
        final List<Place.Field> placeFields = Arrays.asList(
                Place.Field.ID,
                Place.Field.DISPLAY_NAME,
                Place.Field.PRIMARY_TYPE,
                Place.Field.TYPES,
                Place.Field.LAT_LNG
        );

        // From: https://developers.google.com/maps/documentation/places/android-sdk/place-types#table-a
        // Types relevant for context-aware music
        final List<String> includedTypes = Arrays.asList(
                "library", "school", "university", "park", "restaurant", "pub", "bar", "cafe",
                "gym", "stadium", "beach"
        );

        SearchNearbyRequest request = SearchNearbyRequest.builder(circle, placeFields)
                .setIncludedTypes(includedTypes)
                .setMaxResultCount(10)
                .build();

        placesClient.searchNearby(request)
                .addOnSuccessListener(response -> {
                    List<Place> places = response.getPlaces();
                    Log.d("GooglePlacesAPI", "Found " + places.size() + " places");
                    callback.onPlacesFound(places);
                })
                .addOnFailureListener(exception -> {
                    Log.e("GooglePlacesAPI", "Nearby search failed: ", exception);
                    callback.onError(exception);
                });
    }

    // Callback interface for returning results
    public interface NearbyPlacesCallback {
        void onPlacesFound(List<Place> places);
        void onError(Exception e);
    }
}
