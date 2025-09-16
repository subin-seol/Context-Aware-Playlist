package com.comp90018.contexttunes.domain;

import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;
import java.util.Calendar;

public class RuleEngine {

    // Our 4 fixed playlists
    private static final Playlist FOCUS_PLAYLIST = new Playlist(
            "Focus",
            "spotify:playlist:37i9dQZF1DX0XUsuxWHRQd",
            "https://open.spotify.com/playlist/37i9dQZF1DX0XUsuxWHRQd"
    );

    private static final Playlist PUMP_UP_PLAYLIST = new Playlist(
            "Pump-up",
            "spotify:playlist:37i9dQZF1DX4JAvHpjipBk",
            "https://open.spotify.com/playlist/37i9dQZF1DX4JAvHpjipBk"
    );

    private static final Playlist CHILL_PLAYLIST = new Playlist(
            "Chill",
            "spotify:playlist:37i9dQZF1DWYcDQ1hSjOpY",
            "https://open.spotify.com/playlist/37i9dQZF1DWYcDQ1hSjOpY"
    );

    private static final Playlist MORNING_PLAYLIST = new Playlist(
            "Morning",
            "spotify:playlist:37i9dQZF1DX0yEZaMOXna3",
            "https://open.spotify.com/playlist/37i9dQZF1DX0yEZaMOXna3"
    );

    public static Recommendation getRecommendation(Context context) {
        String timeOfDay = context.timeOfDay;
        LightBucket light = context.lightLevel;

        // rules
        if (timeOfDay.equals("morning") && light == LightBucket.BRIGHT) {
            return new Recommendation(MORNING_PLAYLIST, "Bright morning light detected");
        }

        if (light == LightBucket.DIM && (timeOfDay.equals("evening") || timeOfDay.equals("night"))) {
            return new Recommendation(CHILL_PLAYLIST, "Dim lighting suggests relaxation time");
        }

        if (light == LightBucket.BRIGHT && context.activity.equals("still")) {
            return new Recommendation(FOCUS_PLAYLIST, "Bright light and stationary - perfect for focus");
        }

        // fallback
        return new Recommendation(PUMP_UP_PLAYLIST, "Ready for some energy");
    }

    public static String getCurrentTimeOfDay() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        if (hour >= 6 && hour < 12) return "morning";
        if (hour >= 12 && hour < 17) return "afternoon";
        if (hour >= 17 && hour < 21) return "evening";
        return "night";
    }
}