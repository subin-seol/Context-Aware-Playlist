package com.comp90018.contexttunes.domain;

import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;
import com.comp90018.contexttunes.data.weather.WeatherService.WeatherState;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

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
        WeatherState weather = context.weather;

        // Weather-based rules (high priority)
        if (weather == WeatherState.RAINY) {
            return new Recommendation(CHILL_PLAYLIST, "Rainy weather calls for cozy vibes");
        }

        if (weather == WeatherState.SUNNY && timeOfDay.equals("morning")) {
            return new Recommendation(MORNING_PLAYLIST, "Sunny morning - perfect start to the day");
        }

        if (weather == WeatherState.SUNNY && context.activity.equals("still")) {
            return new Recommendation(PUMP_UP_PLAYLIST, "Sunny weather boosts energy");
        }

        if (weather == WeatherState.CLOUDY && (timeOfDay.equals("evening") || timeOfDay.equals("night"))) {
            return new Recommendation(CHILL_PLAYLIST, "Cloudy evening suggests relaxation");
        }

        // Original light-based rules (fallback)
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

    /**
     * Get multiple recommendations based on context.
     * Returns a list of 3-4 playlists that match different aspects of the context.
     */
    public static List<Recommendation> getMultipleRecommendations(Context context) {
        List<Recommendation> recommendations = new ArrayList<>();
        String timeOfDay = context.timeOfDay;
        LightBucket light = context.lightLevel;
        WeatherState weather = context.weather;
        String activity = context.activity;

        // Primary recommendation (highest priority)
        recommendations.add(getRecommendation(context));

        // Add weather-based alternative if applicable
        if (weather == WeatherState.RAINY && !recommendations.get(0).playlist.name.equals("Chill")) {
            recommendations.add(new Recommendation(CHILL_PLAYLIST, "Rainy day vibes"));
        } else if (weather == WeatherState.SUNNY && !recommendations.get(0).playlist.name.equals("Pump-up")) {
            recommendations.add(new Recommendation(PUMP_UP_PLAYLIST, "Sunny energy boost"));
        }

        // Add time-based alternative
        if (timeOfDay.equals("morning") && !recommendations.get(0).playlist.name.equals("Morning")) {
            recommendations.add(new Recommendation(MORNING_PLAYLIST, "Start your day right"));
        } else if ((timeOfDay.equals("evening") || timeOfDay.equals("night"))
                && !recommendations.get(0).playlist.name.equals("Chill")) {
            recommendations.add(new Recommendation(CHILL_PLAYLIST, "Evening wind-down"));
        }

        // Add activity-based alternative
        if (activity.equals("still") && light == LightBucket.BRIGHT
                && !recommendations.get(0).playlist.name.equals("Focus")) {
            recommendations.add(new Recommendation(FOCUS_PLAYLIST, "Deep work mode"));
        }

        // Ensure we always have at least 3 recommendations
        if (recommendations.size() < 3) {
            // Add pump-up if not already included
            boolean hasPumpUp = false;
            for (Recommendation r : recommendations) {
                if (r.playlist.name.equals("Pump-up")) {
                    hasPumpUp = true;
                    break;
                }
            }
            if (!hasPumpUp) {
                recommendations.add(new Recommendation(PUMP_UP_PLAYLIST, "Boost your energy"));
            }
        }

        if (recommendations.size() < 3) {
            // Add focus if not already included
            boolean hasFocus = false;
            for (Recommendation r : recommendations) {
                if (r.playlist.name.equals("Focus")) {
                    hasFocus = true;
                    break;
                }
            }
            if (!hasFocus) {
                recommendations.add(new Recommendation(FOCUS_PLAYLIST, "Concentration time"));
            }
        }

        // Limit to 4 recommendations max
        if (recommendations.size() > 4) {
            recommendations = recommendations.subList(0, 4);
        }

        return recommendations;
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