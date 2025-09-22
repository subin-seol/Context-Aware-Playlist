package com.comp90018.contexttunes.data.weather;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Random;

/**
 * Mock weather service for testing purposes when API key is not available.
 * Returns random weather states to demonstrate the feature.
 */
public class MockWeatherService {

    public enum WeatherState {
        SUNNY, CLOUDY, RAINY, UNKNOWN
    }

    public interface WeatherCallback {
        void onWeatherReceived(@NonNull WeatherState weather);
    }

    private static final String TAG = "MockWeatherService";
    private final Random random = new Random();

    public MockWeatherService(@NonNull Context context) {
        // Mock service doesn't need context
    }

    public void getCurrentWeather(@NonNull WeatherCallback callback) {
        // Simulate network delay
        new Thread(() -> {
            try {
                Thread.sleep(1000); // 1 second delay to simulate API call

                // Return a random weather state for testing
                WeatherState[] states = {WeatherState.SUNNY, WeatherState.CLOUDY, WeatherState.RAINY};
                WeatherState randomWeather = states[random.nextInt(states.length)];

                Log.d(TAG, "Mock weather generated: " + randomWeather);
                callback.onWeatherReceived(randomWeather);

            } catch (InterruptedException e) {
                Log.e(TAG, "Mock weather service interrupted", e);
                callback.onWeatherReceived(WeatherState.UNKNOWN);
            }
        }).start();
    }

    public void shutdown() {
        // Nothing to shutdown in mock service
    }
}
