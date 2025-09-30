package com.comp90018.contexttunes.ui.home;


import android.location.Location;
import android.Manifest;
import android.content.pm.PackageManager;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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
import com.comp90018.contexttunes.data.sensors.LocationSensor;
import com.comp90018.contexttunes.data.weather.WeatherService;
import com.comp90018.contexttunes.data.weather.WeatherService.WeatherState;
import com.comp90018.contexttunes.data.weather.MockWeatherService;

import com.comp90018.contexttunes.data.sensors.LightSensor;
import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;
import com.comp90018.contexttunes.databinding.FragmentHomeBinding;
import com.comp90018.contexttunes.domain.Context;
import com.comp90018.contexttunes.domain.Recommendation;
import com.comp90018.contexttunes.domain.RuleEngine;
import com.comp90018.contexttunes.utils.SettingsManager;
import com.google.android.libraries.places.api.model.Place;
import com.comp90018.contexttunes.ui.viewModel.SharedCameraViewModel;

import com.comp90018.contexttunes.utils.PlaylistOpener;
import java.util.ArrayList;



import java.util.List;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    private FragmentHomeBinding binding;
    private SettingsManager settingsManager;
    private LightSensor lightSensor;
    private LocationSensor locationSensor;
    private GooglePlacesAPI googlePlacesAPI;

    private MockWeatherService mockWeatherService; // Using mock service for testing
    private Recommendation currentRecommendation = null;
    private WeatherState currentWeather = WeatherState.UNKNOWN;
    private LightBucket currentLightBucket = LightBucket.UNKNOWN;

    private List<Recommendation> currentRecommendations = new ArrayList<>();
    private boolean recommendationsGenerated = false;

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

        // Init settings manager
        settingsManager = new SettingsManager(requireContext());

        // Get the shared ViewModel
        SharedCameraViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedCameraViewModel.class);

        // username could come from prefs later
        binding.welcomeTitle.setText("Welcome back!");

        // Initialize weather status
        updateWeatherStatus(WeatherState.UNKNOWN);

        // --- LIGHT SENSOR WIRING ---
        // Only initialize if enabled in settings
        SettingsManager settingsManager = new SettingsManager(requireContext());
        if (settingsManager.isLightEnabled()) {
            lightSensor = new LightSensor(requireContext(), bucket -> {
                if (binding == null) return;
                requireActivity().runOnUiThread(() -> {
                    updateLightStatus(bucket);
                    updateRecommendation(bucket);
                });
            });
        }


        // --- LOCATION SENSOR + PLACES API ---
        locationSensor = new LocationSensor(requireActivity());
        googlePlacesAPI = new GooglePlacesAPI(requireContext(), BuildConfig.PLACES_API_KEY);

        // --- WEATHER SERVICE ---
        mockWeatherService = new MockWeatherService(requireContext());
        // Only fetch weather if location is enabled in settings
        if (settingsManager.isLocationEnabled()) {
            checkAndRequestLocationPermission(); // Handles permission + weather fetch
        } else {
            updateWeatherStatus(WeatherState.UNKNOWN);
        }

        // Camera button -> switch to Snap tab
        binding.btnSnap.setOnClickListener(v -> {
            // Use MainActivity's method to navigate to snap tab properly
            ((MainActivity) requireActivity()).goToSnapTab();
        });

        // GO button -> trigger recommendation
        binding.btnGo.setOnClickListener(v -> {
            generateRecommendations();
        });

        // Regenerate button
        binding.btnRegenerate.setOnClickListener(v -> {
            generateRecommendations();
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
            if (!settingsManager.isLocationEnabled()) {
                Toast.makeText(requireContext(), "Location services disabled in settings", Toast.LENGTH_SHORT).show();
                return;
            }

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

          // Only auto-update if recommendations haven't been generated yet
          if (!recommendationsGenerated) {
              String timeOfDay = RuleEngine.getCurrentTimeOfDay();
              String activity = "still";
              Context context = new Context(currentLightBucket, timeOfDay, activity, currentWeather);

              Recommendation recommendation = RuleEngine.getRecommendation(context);
              binding.welcomeSubtitle.setText(recommendation.reason);
              currentRecommendation = recommendation;
          }
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

    // --- PLACES ---
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

    /**
     * Generate multiple recommendations and update UI to show them
     */
    private void generateRecommendations() {
        String timeOfDay = RuleEngine.getCurrentTimeOfDay();
        String activity = "still"; // mock
        Context context = new Context(currentLightBucket, timeOfDay, activity, currentWeather);

        // Get multiple recommendations
        currentRecommendations = RuleEngine.getMultipleRecommendations(context);
        recommendationsGenerated = true;

        // Update UI to show generated state
        showGeneratedState();
    }

    /**
     * Show the "after generation" UI state with context tags and playlist cards
     */
    private void showGeneratedState() {
        if (binding == null) return;

        // hide the "Create My Vibe" card
        binding.createVibeCard.setVisibility(View.GONE);

        // show the "Current Mood" card
        binding.currentMoodCard.setVisibility(View.VISIBLE);
        populateContextTags();

        // show playlist suggestions section
        binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);
        populatePlaylistCards();

        // show regenerate button
        binding.btnRegenerate.setVisibility(View.VISIBLE);
    }

    //Show the "before generation" UI state
    private void showBeforeGenerationState() {
        if (binding == null) return;

        // SHOW the "Create My Vibe" card
        binding.createVibeCard.setVisibility(View.VISIBLE);
        binding.welcomeSubtitle.setVisibility(View.VISIBLE);

        // HIDE the "after generation" elements
        binding.currentMoodCard.setVisibility(View.GONE);
        binding.playlistSuggestionsSection.setVisibility(View.GONE);
        binding.btnRegenerate.setVisibility(View.GONE);

        recommendationsGenerated = false;
    }


    //Populate context tags based on current context
    private void populateContextTags() {
        binding.contextTagsGroup.removeAllViews();

        // Add activity tag
        addContextChip("Still", android.graphics.Color.parseColor("#2D3748"));

        // Add light level tag
        String lightText = "";
        switch (currentLightBucket) {
            case DIM:
                lightText = "Dim";
                break;
            case NORMAL:
                lightText = "Normal";
                break;
            case BRIGHT:
                lightText = "Bright";
                break;
            case UNKNOWN:
                lightText = "Unknown";
                break;
        }
        if (!lightText.isEmpty() && currentLightBucket != LightBucket.UNKNOWN) {
            addContextChip(lightText, android.graphics.Color.parseColor("#2D3748"));
        }

        // Add weather tag
        String weatherText = "";
        switch (currentWeather) {
            case SUNNY:
                weatherText = "Sunny";
                break;
            case CLOUDY:
                weatherText = "Cloudy";
                break;
            case RAINY:
                weatherText = "Rainy";
                break;
        }
        if (!weatherText.isEmpty() && currentWeather != WeatherState.UNKNOWN) {
            addContextChip(weatherText, android.graphics.Color.parseColor("#2D3748"));
        }

        // Add time of day tag (capitalize first letter)
        String timeOfDay = RuleEngine.getCurrentTimeOfDay();
        String capitalizedTime = timeOfDay.substring(0, 1).toUpperCase() + timeOfDay.substring(1);
        addContextChip(capitalizedTime, android.graphics.Color.parseColor("#2D3748"));
    }

    //Add a single context chip to the chip group
    private void addContextChip(String text, int backgroundColor) {
        com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
        chip.setText(text);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(backgroundColor));
        chip.setTextColor(ContextCompat.getColor(requireContext(), com.comp90018.contexttunes.R.color.text_primary));
        chip.setClickable(false);
        chip.setChipCornerRadius(24f);
        binding.contextTagsGroup.addView(chip);
    }

    // Populate playlist cards based on current recommendations

    private void populatePlaylistCards() {
        binding.playlistCardsContainer.removeAllViews();

        for (Recommendation recommendation : currentRecommendations) {
            View cardView = getLayoutInflater().inflate(
                    com.comp90018.contexttunes.R.layout.item_playlist_card,
                    binding.playlistCardsContainer,
                    false
            );

            TextView playlistName = cardView.findViewById(com.comp90018.contexttunes.R.id.playlistName);
            TextView playlistReason = cardView.findViewById(com.comp90018.contexttunes.R.id.playlistReason);
            com.google.android.material.button.MaterialButton btnPlay = cardView.findViewById(com.comp90018.contexttunes.R.id.btnPlay);

            playlistName.setText(recommendation.playlist.name);
            playlistReason.setText(recommendation.reason);

            btnPlay.setOnClickListener(v -> {
                PlaylistOpener.openPlaylist(requireContext(), recommendation.playlist);
            });

            binding.playlistCardsContainer.addView(cardView);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        SettingsManager settingsManager = new SettingsManager(requireContext());
        if (lightSensor != null && settingsManager.isLightEnabled()) {
            lightSensor.start();
        }
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
