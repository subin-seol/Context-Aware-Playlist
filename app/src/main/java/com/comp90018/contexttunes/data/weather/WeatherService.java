package com.comp90018.contexttunes.data.weather;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides current weather conditions as simple states: SUNNY, CLOUDY, RAINY.
 * Uses OpenWeatherMap API for weather data.
 */
public class WeatherService {

    public enum WeatherState {
        SUNNY, CLOUDY, RAINY, UNKNOWN
    }

    public interface WeatherCallback {
        void onWeatherReceived(@NonNull WeatherState weather);
    }

    private static final String TAG = "WeatherService";
    private static final String API_KEY = "your_openweather_api_key_here"; // Replace with actual API key
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/weather";

    private final Context context;
    private final ExecutorService executor;

    public WeatherService(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void getCurrentWeather(@NonNull WeatherCallback callback) {
        executor.execute(() -> {
            try {
                Location location = getLastKnownLocation();
                if (location == null) {
                    Log.w(TAG, "No location available, returning UNKNOWN weather");
                    callback.onWeatherReceived(WeatherState.UNKNOWN);
                    return;
                }

                WeatherState weather = fetchWeatherFromAPI(location.getLatitude(), location.getLongitude());
                callback.onWeatherReceived(weather);

            } catch (Exception e) {
                Log.e(TAG, "Error getting weather", e);
                callback.onWeatherReceived(WeatherState.UNKNOWN);
            }
        });
    }

    @Nullable
    private Location getLastKnownLocation() {
        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) return null;

            // Try GPS first, then network
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            return location;
        } catch (SecurityException e) {
            Log.w(TAG, "No location permission", e);
            return null;
        }
    }

    @NonNull
    private WeatherState fetchWeatherFromAPI(double lat, double lon) {
        try {
            String urlString = String.format("%s?lat=%f&lon=%f&appid=%s", BASE_URL, lat, lon, API_KEY);
            URL url = new URL(urlString);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Log.w(TAG, "Weather API returned code: " + responseCode);
                return WeatherState.UNKNOWN;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            return parseWeatherResponse(response.toString());

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error fetching weather from API", e);
            return WeatherState.UNKNOWN;
        }
    }

    @NonNull
    private WeatherState parseWeatherResponse(String jsonResponse) throws JSONException {
        JSONObject json = new JSONObject(jsonResponse);

        if (!json.has("weather")) {
            return WeatherState.UNKNOWN;
        }

        JSONObject weather = json.getJSONArray("weather").getJSONObject(0);
        String main = weather.getString("main").toLowerCase();
        String description = weather.optString("description", "").toLowerCase();

        // Map weather conditions to simple states
        switch (main) {
            case "clear":
                return WeatherState.SUNNY;
            case "clouds":
                return WeatherState.CLOUDY;
            case "rain":
            case "drizzle":
            case "thunderstorm":
                return WeatherState.RAINY;
            case "snow":
                return WeatherState.CLOUDY; // Treat snow as cloudy
            default:
                // Check description for more details
                if (description.contains("rain") || description.contains("storm")) {
                    return WeatherState.RAINY;
                } else if (description.contains("cloud")) {
                    return WeatherState.CLOUDY;
                } else if (description.contains("clear") || description.contains("sun")) {
                    return WeatherState.SUNNY;
                }
                return WeatherState.UNKNOWN;
        }
    }

    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
