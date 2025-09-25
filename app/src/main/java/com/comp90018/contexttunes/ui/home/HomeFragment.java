package com.comp90018.contexttunes.ui.home;


import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.comp90018.contexttunes.BuildConfig;
import com.comp90018.contexttunes.MainActivity;
import com.comp90018.contexttunes.data.api.GooglePlacesAPI;
import com.comp90018.contexttunes.data.sensors.LightSensor;
import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;
import com.comp90018.contexttunes.data.sensors.LocationSensor;
import com.comp90018.contexttunes.data.weather.MockWeatherService;
import com.comp90018.contexttunes.data.weather.WeatherService;
import com.comp90018.contexttunes.data.weather.WeatherService.WeatherState;
import com.comp90018.contexttunes.databinding.FragmentHomeBinding;
import com.comp90018.contexttunes.domain.Context;
import com.comp90018.contexttunes.domain.Recommendation;
import com.comp90018.contexttunes.domain.RuleEngine;
import com.comp90018.contexttunes.ui.viewModel.SharedCameraViewModel;
import com.comp90018.contexttunes.utils.PlaylistOpener;
import com.google.android.libraries.places.api.model.Place;

import java.util.List;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    private FragmentHomeBinding binding;

    private LightSensor lightSensor;
    private LocationSensor locationSensor;
    private GooglePlacesAPI googlePlacesAPI;

    private MockWeatherService mockWeatherService; // Using mock service for testing
    private Recommendation currentRecommendation = null;
    private WeatherState currentWeather = WeatherState.UNKNOWN;
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

        // Get the shared ViewModel
        SharedCameraViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedCameraViewModel.class);

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

        // --- LIGHT SENSOR ---
        lightSensor = new LightSensor(requireContext(), bucket -> {
            if (binding == null) return;
            requireActivity().runOnUiThread(() -> {
                updateLightStatus(bucket);
                updateRecommendation(bucket);
            });
        });

        // --- LOCATION SENSOR + PLACES API ---
        locationSensor = new LocationSensor(requireActivity());
        googlePlacesAPI = new GooglePlacesAPI(requireContext(), BuildConfig.PLACES_API_KEY);

        // --- WEATHER SERVICE ---
        mockWeatherService = new MockWeatherService(requireContext());
        checkAndRequestLocationPermission(); // Handles permission + weather fetch

        // Camera button -> switch to Snap tab
        binding.btnSnap.setOnClickListener(v -> {
            // Use MainActivity's method to navigate to snap tab properly
            ((MainActivity) requireActivity()).goToSnapTab();
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

        // Add listener for image preview button
        binding.btnPreviewImage.setOnClickListener(v -> {
            // Navigate to snap tab which will show the captured image with retake/generate buttons
            ((MainActivity) requireActivity()).goToSnapTab();
        });

        // Observe if there is a captured image
        viewModel.getCapturedImage().observe(getViewLifecycleOwner(),bitmap ->{
            if (bitmap != null){
                // Update Snap button with an image icon
                binding.btnSnap.setVisibility(View.GONE);
                binding.btnPreviewImage.setVisibility(View.VISIBLE);
            } else {
                // Show camera button when no image
                binding.btnSnap.setVisibility(View.VISIBLE);
                binding.btnPreviewImage.setVisibility(View.GONE);
            }
        });

        // Button to fetch places
        binding.btnFetchPlaces.setOnClickListener(v -> {
            locationSensor.getCurrentLocation(location -> {
                if (location != null) {
                    Log.d(TAG, "Got location: " + location.getLatitude() + ", " + location.getLongitude());
                    fetchNearbyPlaces(location);
                } else {
                    Toast.makeText(requireContext(), "No location found", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }


      // --- WEATHER / PERMISSIONS ---
      private void checkAndRequestLocationPermission() {
        if (hasLocationPermission()) {
            fetchWeatherData();
        } else {
            Toast.makeText(requireContext(), "Requesting location permission for weather...", Toast.LENGTH_SHORT).show();
            requestLocationPermission();
        }
    }
  
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(requireActivity(),
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
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
  
  
      // --- RECOMMENDATION ---
      private void updateRecommendation(@Nullable LightBucket lightBucket) {
        if (lightBucket != null) currentLightBucket = lightBucket;

        String timeOfDay = RuleEngine.getCurrentTimeOfDay();
        String activity = "still"; // mock
        Context context = new Context(currentLightBucket, timeOfDay, activity, currentWeather);

        Recommendation recommendation = RuleEngine.getRecommendation(context);
        binding.welcomeSubtitle.setText(recommendation.reason);
        currentRecommendation = recommendation;
    }
  
  // --- LIGHT & WEATHER UI
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

//     --- PLACES ---
    private void fetchNearbyPlaces(Location location) {
        googlePlacesAPI.getNearbyPlaces(location, 300, new GooglePlacesAPI.NearbyPlacesCallback() {
            @Override
            public void onPlacesFound(List<Place> places) {
                if (places.isEmpty()) {
                    Toast.makeText(requireContext(), "No nearby places found", Toast.LENGTH_SHORT).show();
                } else {
                    for (Place place : places) {
                        Log.d(TAG, "Found place: " + place.getDisplayName()
                                + " (" + place.getPrimaryType() + ")");
                    }
                    Toast.makeText(requireContext(),
                            "Found " + places.size() + " places", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Places API error", e);
                Toast.makeText(requireContext(), "Places API error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- PERMISSION CALLBACK ---
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchWeatherData();
            } else {
                updateWeatherStatus(WeatherState.UNKNOWN);
                Toast.makeText(requireContext(), "Location permission needed for weather updates", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Forward to LocationSensor for places API permissions
            locationSensor.handlePermissionResult(requestCode, grantResults, this::fetchNearbyPlaces);
        }
    }
  
  // Small helper to print nicer bucket names
    private String pretty(LightBucket b) {
        if (b == null) return "N/A";
        switch (b) {
            case DIM:
                return "Dim";
            case NORMAL:
                return "Normal";
            case BRIGHT:
                return "Bright";
            default:
                return "N/A";
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
