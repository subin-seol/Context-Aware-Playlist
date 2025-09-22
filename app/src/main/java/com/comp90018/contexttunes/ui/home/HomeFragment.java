package com.comp90018.contexttunes.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.comp90018.contexttunes.MainActivity;
import com.comp90018.contexttunes.databinding.FragmentHomeBinding;
import com.comp90018.contexttunes.R;

import com.comp90018.contexttunes.data.sensors.LightSensor;
import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;
import com.comp90018.contexttunes.data.weather.WeatherService;
import com.comp90018.contexttunes.data.weather.WeatherService.WeatherState;
import com.comp90018.contexttunes.data.weather.MockWeatherService;

import com.comp90018.contexttunes.domain.Context;
import com.comp90018.contexttunes.domain.Recommendation;
import com.comp90018.contexttunes.domain.RuleEngine;
import com.comp90018.contexttunes.utils.PlaylistOpener;

public class HomeFragment extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    private FragmentHomeBinding binding;

    private LightSensor lightSensor;
    private MockWeatherService mockWeatherService; // Using mock service for testing

    private Recommendation currentRecommendation = null;
    private WeatherService.WeatherState currentWeather = WeatherService.WeatherState.UNKNOWN;

    private LightBucket currentLightBucket = LightBucket.UNKNOWN;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // username could come from prefs later
        binding.welcomeTitle.setText("Welcome back!");

        // Initialize weather status
        updateWeatherStatus(WeatherState.UNKNOWN);

        // --- LIGHT SENSOR WIRING ---
        lightSensor = new LightSensor(requireContext(), bucket -> {
            if (binding == null) return;
            requireActivity().runOnUiThread(() -> {
                // Update light sensor display
                updateLightStatus(bucket);
                // Update recommendation when light changes
                updateRecommendation(bucket);
            });
        });

        // --- WEATHER SERVICE WIRING ---
        mockWeatherService = new MockWeatherService(requireContext());

        // Check location permission and request if needed
        checkAndRequestLocationPermission();


        // Camera button -> switch to Snap tab
        binding.btnSnap.setOnClickListener(v -> {
            MainActivity act = (MainActivity) requireActivity();
            act.goToHomeTab(); // ensures listener exists
            // directly set selected tab:
            // R.id.nav_snap is your menu id in BottomNavigationView
            ((MainActivity) requireActivity()).selectTab(R.id.nav_snap);
            // simpler: call through MainActivity if you add a helper; for now:
            act.runOnUiThread(() ->
                    ((MainActivity) requireActivity()).selectTab(R.id.nav_snap)

            );
        });

        // GO button -> trigger recommendation
        // NEW: Update recommendation when light changes
        binding.btnGo.setOnClickListener(v -> {
            if (currentRecommendation != null) {
                PlaylistOpener.openPlaylist(requireContext(), currentRecommendation.playlist);
            } else {
                Toast.makeText(requireContext(), "Generating your vibe…", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkAndRequestLocationPermission() {
        if (hasLocationPermission()) {
            fetchWeatherData();
        } else {
            Toast.makeText(requireContext(), "Requesting location permission for weather...", Toast.LENGTH_SHORT).show();
            requestLocationPermission();
        }
    }

    private void fetchWeatherData() {
        Toast.makeText(requireContext(), "Fetching weather data...", Toast.LENGTH_SHORT).show();

        // Fetch initial weather data using mock service
        mockWeatherService.getCurrentWeather(mockWeather -> {
            // Convert MockWeatherService enum to WeatherService enum
            WeatherService.WeatherState convertedWeather;
            switch (mockWeather) {
                case SUNNY:
                    convertedWeather = WeatherService.WeatherState.SUNNY;
                    break;
                case CLOUDY:
                    convertedWeather = WeatherService.WeatherState.CLOUDY;
                    break;
                case RAINY:
                    convertedWeather = WeatherService.WeatherState.RAINY;
                    break;
                case UNKNOWN:
                default:
                    convertedWeather = WeatherService.WeatherState.UNKNOWN;
                    break;
            }

            currentWeather = convertedWeather;
            requireActivity().runOnUiThread(() -> {
                String debugMessage = "Weather received: " + convertedWeather.toString();
                Toast.makeText(requireContext(), debugMessage, Toast.LENGTH_SHORT).show();

                // Update recommendation with new weather data
                updateRecommendation(null); // null means use current light sensor value
                updateWeatherStatus(currentWeather);
            });
        });
    }

    private void requestLocationPermission() {
        // Request location permission
        ActivityCompat.requestPermissions(requireActivity(),
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    private boolean hasLocationPermission() {
        // Check if location permission is granted
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void updateRecommendation(@Nullable LightBucket lightBucket) {
        // Update current light bucket if provided
        if (lightBucket != null) {
            currentLightBucket = lightBucket;
        }

        // Create current context by combining all available data
        String timeOfDay = RuleEngine.getCurrentTimeOfDay();
        String activity = "still"; // Mock data for now
        Context context = new Context(currentLightBucket, timeOfDay, activity, currentWeather);

        // Get rec from rule engine
        Recommendation recommendation = RuleEngine.getRecommendation(context);

        // Update UI
        binding.welcomeSubtitle.setText(recommendation.reason);

        // Store for GO button
        currentRecommendation = recommendation;
    }

    private void updateWeatherStatus(WeatherState weather) {
        if (binding == null) return;

        String weatherText;
        switch (weather) {
            case SUNNY:
                weatherText = "Weather: ☀️ Sunny";
                break;
            case CLOUDY:
                weatherText = "Weather: ☁️ Cloudy";
                break;
            case RAINY:
                weatherText = "Weather: 🌧️ Rainy";
                break;
            case UNKNOWN:
            default:
                weatherText = "Weather: —";
                break;
        }

        binding.weatherStatus.setText(weatherText);
    }

    private void updateLightStatus(LightBucket bucket) {
        if (binding == null) return;

        String lightText;
        switch (bucket) {
            case DIM:
                lightText = "Light: 🌒 Dim";
                break;
            case NORMAL:
                lightText = "Light: 🌗 Normal";
                break;
            case BRIGHT:
                lightText = "Light: 🌕 Bright";
                break;
            case UNKNOWN:
            default:
                lightText = "Light: —";
                break;
        }

        binding.lightValue.setText(lightText);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, fetch weather data
                fetchWeatherData();
            } else {
                // Permission denied, show fallback message
                updateWeatherStatus(WeatherState.UNKNOWN);
                Toast.makeText(requireContext(), "Location permission needed for weather updates", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (lightSensor != null) lightSensor.start(); // begin receiving updates
    }

    @Override
    public void onStop() {
        if (lightSensor != null) lightSensor.stop(); // stop to save battery
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (mockWeatherService != null) {
            mockWeatherService.shutdown();
        }
        super.onDestroyView();
        binding = null;
    }
}
