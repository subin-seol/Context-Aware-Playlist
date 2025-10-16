package com.comp90018.contexttunes.domain;

import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;
import com.comp90018.contexttunes.data.weather.WeatherService.WeatherState;

import java.util.Collections;
import java.util.List;

/**
 * Represents the user's current context derived from multiple sensors and APIs.
 * Used by both RuleEngine and AI inference for playlist recommendations.
 */
public class Context {
    public final LightBucket lightLevel;
    public final String timeOfDay;
    public final String activity;
    public final WeatherState weather;

    // Location context
    public final String placeTag;              // "Home", "Gym", "Library", null if not at tagged location
    public final List<String> nearbyPlaceTypes; // ["gym", "park", "cafe"] from Google Places API

    /**
     * Full constructor with location data.
     */
    public Context(LightBucket lightLevel, String timeOfDay, String activity,
                   WeatherState weather, String placeTag, List<String> nearbyPlaceTypes) {
        this.lightLevel = lightLevel;
        this.timeOfDay = timeOfDay;
        this.activity = activity;
        this.weather = weather;
        this.placeTag = placeTag;
        this.nearbyPlaceTypes = nearbyPlaceTypes != null ? nearbyPlaceTypes : Collections.emptyList();
    }

    /**
     * Backward-compatible constructor without location data.
     * Used by existing RuleEngine code.
     */
    public Context(LightBucket lightLevel, String timeOfDay, String activity, WeatherState weather) {
        this(lightLevel, timeOfDay, activity, weather, null, Collections.emptyList());
    }

    /**
     * Check if location context is available.
     */
    public boolean hasLocationContext() {
        return placeTag != null || !nearbyPlaceTypes.isEmpty();
    }

    /**
     * Get a human-readable location summary.
     */
    public String getLocationSummary() {
        if (placeTag != null) {
            return "at " + placeTag;
        } else if (!nearbyPlaceTypes.isEmpty()) {
            return "near " + String.join(", ", nearbyPlaceTypes.subList(0, Math.min(3, nearbyPlaceTypes.size())));
        } else {
            return "location unknown";
        }
    }
}