package com.comp90018.contexttunes.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages app settings using SharedPreferences.
 * Provides methods to save and retrieve user preferences for sensors, detection modes, and locations.
 */
public class SettingsManager {
    private static final String PREFS_NAME = "ContextTunesPrefs";

    // Detection Mode Keys
    private static final String KEY_DETECTION_MODE = "detection_mode";
    private static final String MODE_PASSIVE = "passive";
    private static final String MODE_ACTIVE = "active";

    // Sensor Permission Keys
    private static final String KEY_LOCATION_ENABLED = "location_enabled";
    private static final String KEY_CAMERA_ENABLED = "camera_enabled";
    private static final String KEY_LIGHT_ENABLED = "light_enabled";
    private static final String KEY_ACCELEROMETER_ENABLED = "accelerometer_enabled";

    // Location Tagging Keys (stores latitude,longitude as string)
    private static final String KEY_HOME_LOCATION = "home_location";
    private static final String KEY_GYM_LOCATION = "gym_location";
    private static final String KEY_OFFICE_LOCATION = "office_location";
    private static final String KEY_LIBRARY_LOCATION = "library_location";
    private static final String KEY_PARK_LOCATION = "park_location";
    private static final String KEY_CAFE_LOCATION = "cafe_location";

    // Notification Keys
    private static final String KEY_PLAYLIST_SUGGESTIONS = "playlist_suggestions";
    private static final String KEY_CONTEXT_CHANGES = "context_changes";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ===== Detection Mode =====

    public void setDetectionMode(boolean isPassive) {
        prefs.edit().putString(KEY_DETECTION_MODE, isPassive ? MODE_PASSIVE : MODE_ACTIVE).apply();
    }

    public boolean isPassiveMode() {
        return MODE_PASSIVE.equals(prefs.getString(KEY_DETECTION_MODE, MODE_ACTIVE));
    }

    // ===== Sensor Permissions =====

    public void setLocationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOCATION_ENABLED, enabled).apply();
    }

    public boolean isLocationEnabled() {
        return prefs.getBoolean(KEY_LOCATION_ENABLED, true); // default true
    }

    public void setCameraEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CAMERA_ENABLED, enabled).apply();
    }

    public boolean isCameraEnabled() {
        return prefs.getBoolean(KEY_CAMERA_ENABLED, true);
    }

    public void setLightEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LIGHT_ENABLED, enabled).apply();
    }

    public boolean isLightEnabled() {
        return prefs.getBoolean(KEY_LIGHT_ENABLED, true);
    }

    public void setAccelerometerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ACCELEROMETER_ENABLED, enabled).apply();
    }

    public boolean isAccelerometerEnabled() {
        return prefs.getBoolean(KEY_ACCELEROMETER_ENABLED, true);
    }

    // ===== Location Tagging =====

    public void saveLocation(String tag, double latitude, double longitude) {
        String locationKey;
        switch (tag.toLowerCase()) {
            case "home":
                locationKey = KEY_HOME_LOCATION;
                break;
            case "gym":
                locationKey = KEY_GYM_LOCATION;
                break;
            case "office":
                locationKey = KEY_OFFICE_LOCATION;
                break;
            case "library":
                locationKey = KEY_LIBRARY_LOCATION;
                break;
            case "park":
                locationKey = KEY_PARK_LOCATION;
                break;
            case "cafe":
                locationKey = KEY_CAFE_LOCATION;
                break;
            default:
                return;
        }

        String locationValue = latitude + "," + longitude;
        prefs.edit().putString(locationKey, locationValue).apply();
    }

    public String getLocation(String tag) {
        String locationKey;
        switch (tag.toLowerCase()) {
            case "home":
                locationKey = KEY_HOME_LOCATION;
                break;
            case "gym":
                locationKey = KEY_GYM_LOCATION;
                break;
            case "office":
                locationKey = KEY_OFFICE_LOCATION;
                break;
            case "library":
                locationKey = KEY_LIBRARY_LOCATION;
                break;
            case "park":
                locationKey = KEY_PARK_LOCATION;
                break;
            case "cafe":
                locationKey = KEY_CAFE_LOCATION;
                break;
            default:
                return null;
        }
        return prefs.getString(locationKey, null);
    }

    public boolean hasLocation(String tag) {
        return getLocation(tag) != null;
    }

    public void clearLocation(String tag) {
        String locationKey;
        switch (tag.toLowerCase()) {
            case "home":
                locationKey = KEY_HOME_LOCATION;
                break;
            case "gym":
                locationKey = KEY_GYM_LOCATION;
                break;
            case "office":
                locationKey = KEY_OFFICE_LOCATION;
                break;
            case "library":
                locationKey = KEY_LIBRARY_LOCATION;
                break;
            case "park":
                locationKey = KEY_PARK_LOCATION;
                break;
            case "cafe":
                locationKey = KEY_CAFE_LOCATION;
                break;
            default:
                return;
        }
        prefs.edit().remove(locationKey).apply();
    }

    // ===== Notifications =====

    public void setPlaylistSuggestionsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PLAYLIST_SUGGESTIONS, enabled).apply();
    }

    public boolean isPlaylistSuggestionsEnabled() {
        return prefs.getBoolean(KEY_PLAYLIST_SUGGESTIONS, true);
    }

    public void setContextChangesEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CONTEXT_CHANGES, enabled).apply();
    }

    public boolean isContextChangesEnabled() {
        return prefs.getBoolean(KEY_CONTEXT_CHANGES, true);
    }
}