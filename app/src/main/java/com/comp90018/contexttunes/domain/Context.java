package com.comp90018.contexttunes.domain;

import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;
import com.comp90018.contexttunes.data.weather.WeatherService.WeatherState;

import java.util.Collections;
import java.util.List;

/**
 * Represents the user's current context derived from multiple sensors and APIs.
 * Used by the AI inference for playlist search string + reason.
 */
public class Context {
    public final LightBucket lightLevel;
    public final String timeOfDay;
    public final String activity;
    public final WeatherState weather;

    // Location context
    public final String placeTag;               // "Home", "Gym", "Library", null if not tagged
    public final List<String> nearbyPlaceTypes; // e.g. ["gym", "park", "cafe"]

    // Camera recognition (optional): top-N labels (strings only; confidences not required by AI)
    public final List<String> imageLabels;      // e.g. ["Beach", "Bicycle", "Sunset"]

    /** Full constructor with location + camera labels. */
    public Context(LightBucket lightLevel, String timeOfDay, String activity,
                   WeatherState weather, String placeTag, List<String> nearbyPlaceTypes,
                   List<String> imageLabels) {
        this.lightLevel = lightLevel;
        this.timeOfDay = timeOfDay;
        this.activity = activity;
        this.weather = weather;
        this.placeTag = placeTag;
        this.nearbyPlaceTypes = nearbyPlaceTypes != null ? nearbyPlaceTypes : Collections.emptyList();
        this.imageLabels = imageLabels != null ? imageLabels : Collections.emptyList();
    }

    /** Backward-compatible constructor without location or camera labels. */
    public Context(LightBucket lightLevel, String timeOfDay, String activity, WeatherState weather) {
        this(lightLevel, timeOfDay, activity, weather, null, Collections.emptyList(), Collections.emptyList());
    }

    /** Convenience constructor without camera labels. */
    public Context(LightBucket lightLevel, String timeOfDay, String activity,
                   WeatherState weather, String placeTag, List<String> nearbyPlaceTypes) {
        this(lightLevel, timeOfDay, activity, weather, placeTag, nearbyPlaceTypes, Collections.emptyList());
    }

    public boolean hasLocationContext() {
        return placeTag != null || !nearbyPlaceTypes.isEmpty();
    }

    public boolean hasImageContext() {
        return imageLabels != null && !imageLabels.isEmpty();
    }

    public String getLocationSummary() {
        if (placeTag != null) {
            return "at " + placeTag;
        } else if (!nearbyPlaceTypes.isEmpty()) {
            int n = Math.min(3, nearbyPlaceTypes.size());
            return "near " + String.join(", ", nearbyPlaceTypes.subList(0, n));
        } else {
            return "location unknown";
        }
    }
}
