package com.comp90018.contexttunes.ui.home;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.comp90018.contexttunes.BuildConfig;
import com.comp90018.contexttunes.MainActivity;
import com.comp90018.contexttunes.R;
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
import com.comp90018.contexttunes.services.SpotifyApiService;
import com.comp90018.contexttunes.ui.viewModel.SharedCameraViewModel;
import com.comp90018.contexttunes.utils.PermissionManager;
import com.comp90018.contexttunes.utils.SettingsManager;
import com.google.android.libraries.places.api.model.Place;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";

    private FragmentHomeBinding binding;
    private SettingsManager settingsManager;
    private LightSensor lightSensor;
    private LocationSensor locationSensor;
    private GooglePlacesAPI googlePlacesAPI;

    private MockWeatherService mockWeatherService;
    private WeatherState currentWeather = WeatherState.UNKNOWN;
    private LightBucket currentLightBucket = LightBucket.UNKNOWN;

    private List<SpotifyApiService.Playlist> spotifyPlaylists = new ArrayList<>();
    private boolean playlistsGenerated = false;

    private Recommendation currentRecommendation = null;
    private List<Recommendation> currentRecommendations = new ArrayList<>();
    private boolean recommendationsGenerated = false;

    // Why are we asking for location permission right now?
    private enum Pending { NONE, WEATHER, PLACES }
    private Pending pending = Pending.NONE;
    private SpotifyApiService spotifyApiService;


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

        settingsManager = new SettingsManager(requireContext());
        locationSensor  = new LocationSensor(requireContext());
        googlePlacesAPI = GooglePlacesAPI.getInstance(requireContext());
        mockWeatherService = new MockWeatherService(requireContext());
        spotifyApiService = new SpotifyApiService(BuildConfig.SPOTIFY_ACCESS_TOKEN);


        // Header
        binding.welcomeTitle.setText("Welcome back!");
        updateWeatherStatus(WeatherState.UNKNOWN);

        // ---- LIGHT SENSOR (respect Settings) ----
        if (settingsManager.isLightEnabled()) {
            lightSensor = new LightSensor(requireContext(), bucket -> {
                if (binding == null) return;
                requireActivity().runOnUiThread(() -> {
                    updateLightStatus(bucket);
                    updateRecommendation(bucket);
                });
            });
        }

        // ---- WEATHER (point-of-use permission) ----
        // Only fetch weather if location is allowed in Settings (even though mock doesn’t use it yet)
        if (settingsManager.isLocationEnabled()) {
            ensureLocationAndFetchWeather();
        } else {
            updateWeatherStatus(WeatherState.UNKNOWN);
        }

        // ---- SNAP navigation ----
        binding.btnSnap.setOnClickListener(v ->
                ((MainActivity) requireActivity()).goToSnapTab()
        );

        // ---- Generate / Regenerate ----
        binding.btnGo.setOnClickListener(v -> testSpotifyPlaylistSearch("party"));
//        binding.btnRegenerate.setOnClickListener(v -> testSpotifyPlaylistSearch("party"));

        // ---- Preview captured image ----
        binding.btnPreviewImage.setOnClickListener(v ->
                ((MainActivity) requireActivity()).goToSnapTab()
        );

        // Observe camera state to toggle visibility of buttons
        SharedCameraViewModel viewModel = new ViewModelProvider(requireActivity())
                .get(SharedCameraViewModel.class);
        viewModel.getCapturedImage().observe(getViewLifecycleOwner(), bitmap -> {
            if (bitmap != null) {
                binding.btnSnap.setVisibility(View.GONE);
                binding.btnPreviewImage.setVisibility(View.VISIBLE);
            } else {
                binding.btnSnap.setVisibility(View.VISIBLE);
                binding.btnPreviewImage.setVisibility(View.GONE);
            }
        });

        // ---- Nearby Places (point-of-use permission) ----
        binding.btnFetchPlaces.setOnClickListener(v -> {
            if (!settingsManager.isLocationEnabled()) {
                Toast.makeText(requireContext(), "Location services disabled in settings", Toast.LENGTH_SHORT).show();
                return;
            }
            ensureLocationThenFetchPlaces();
        });
    }

    // ===================== PERMISSION-GATED ACTIONS =====================

    /** Weather: request permission if needed, then fetch. */
    private void ensureLocationAndFetchWeather() {
        if (PermissionManager.hasLocationPermission(requireContext())) {
            fetchWeatherData();
            pending = Pending.NONE;
        } else {
            pending = Pending.WEATHER;
            PermissionManager.requestLocation(this);
        }
    }

    /** Places: request permission if needed, then fetch nearby with current location. */
    private void ensureLocationThenFetchPlaces() {
        if (PermissionManager.hasLocationPermission(requireContext())) {
            locationSensor.getCurrentLocation(location -> {
                if (location == null) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "Couldn't get location", Toast.LENGTH_SHORT).show());
                    return;
                }
                fetchNearbyPlaces(location);
            });
            pending = Pending.NONE;
        } else {
            pending = Pending.PLACES;
            PermissionManager.requestLocation(this);
        }
    }


    // ===================== WEATHER + LIGHT UI =====================

    private void fetchWeatherData() {
        Toast.makeText(requireContext(), "Fetching weather data...", Toast.LENGTH_SHORT).show();

        // Mock: convert mock enum to WeatherService enum (kept as-is)
        mockWeatherService.getCurrentWeather(mockWeather -> {
            WeatherService.WeatherState converted;
            switch (mockWeather) {
                case SUNNY:  converted = WeatherService.WeatherState.SUNNY;  break;
                case CLOUDY: converted = WeatherService.WeatherState.CLOUDY; break;
                case RAINY:  converted = WeatherService.WeatherState.RAINY;  break;
                case UNKNOWN:
                default:     converted = WeatherService.WeatherState.UNKNOWN; break;
            }

            currentWeather = converted;
            requireActivity().runOnUiThread(() -> {
                updateRecommendation(null); // use current light
                updateWeatherStatus(currentWeather);
            });
        });
    }

    private void updateWeatherStatus(WeatherState weather) {
        if (binding == null) return;
        String weatherText;
        switch (weather) {
            case SUNNY:   weatherText = "Weather: ☀️ Sunny";  break;
            case CLOUDY:  weatherText = "Weather: ☁️ Cloudy"; break;
            case RAINY:   weatherText = "Weather: 🌧️ Rainy";  break;
            case UNKNOWN:
            default:      weatherText = "Weather: —";          break;
        }
        binding.weatherStatus.setText(weatherText);
    }

    private void updateLightStatus(LightBucket bucket) {
        if (binding == null) return;
        String lightText;
        switch (bucket) {
            case DIM:     lightText = "Light: 🌒 Dim";     break;
            case NORMAL:  lightText = "Light: 🌗 Normal";  break;
            case BRIGHT:  lightText = "Light: 🌕 Bright";  break;
            case UNKNOWN:
            default:      lightText = "Light: —";          break;
        }
        binding.lightValue.setText(lightText);
    }

    // ===================== RECOMMENDATION =====================

    private void updateRecommendation(@Nullable LightBucket lightBucket) {
        if (lightBucket != null) currentLightBucket = lightBucket;

        if (!recommendationsGenerated) { // only auto-update before first generation
            String timeOfDay = RuleEngine.getCurrentTimeOfDay();
            String activity  = "still"; // mock
            Context ctx = new Context(currentLightBucket, timeOfDay, activity, currentWeather);

            Recommendation rec = RuleEngine.getRecommendation(ctx);
            binding.welcomeSubtitle.setText(rec.reason);
            currentRecommendation = rec;
        }
    }

    private void generateRecommendations() {
        String timeOfDay = RuleEngine.getCurrentTimeOfDay();
        String activity  = "still";
        Context ctx = new Context(currentLightBucket, timeOfDay, activity, currentWeather);

        // Get multiple recommendations
        currentRecommendations = RuleEngine.getMultipleRecommendations(ctx);
        recommendationsGenerated = true;

        // Update UI to show generated state
        showGeneratedState();
    }

    private void showGeneratedState() {
        if (binding == null) return;

        // Hide "Create My Vibe" card
        binding.createVibeCard.setVisibility(View.GONE);

        // Show "Current Mood" card
        binding.currentMoodCard.setVisibility(View.VISIBLE);
        populateContextTags();

        // Show playlist suggestions section
        binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);
        populateSpotifyPlaylistCards();
        
    }

    private void showBeforeGenerationState() {
        if (binding == null) return;

        // Show "Create My Vibe" card
        binding.createVibeCard.setVisibility(View.VISIBLE);
        binding.welcomeSubtitle.setVisibility(View.VISIBLE);

        // Hide "after generation" elements
        binding.currentMoodCard.setVisibility(View.GONE);
        binding.playlistSuggestionsSection.setVisibility(View.GONE);

        recommendationsGenerated = false;
    }

    private void populateContextTags() {
        binding.contextTagsGroup.removeAllViews();

        // Activity tag (mocked as "still" for now)
        addContextChip("Still", android.graphics.Color.parseColor("#2D3748"));

        String lightText;
        switch (currentLightBucket) {
            case DIM:    lightText = "Dim";    break;
            case NORMAL: lightText = "Normal"; break;
            case BRIGHT: lightText = "Bright"; break;
            default:     lightText = "Unknown";
        }
        if (currentLightBucket != LightBucket.UNKNOWN) {
            addContextChip(lightText, android.graphics.Color.parseColor("#2D3748"));
        }

        String weatherText;
        switch (currentWeather) {
            case SUNNY:  weatherText = "Sunny";  break;
            case CLOUDY: weatherText = "Cloudy"; break;
            case RAINY:  weatherText = "Rainy";  break;
            default:     weatherText = "";
        }
        if (!weatherText.isEmpty()) {
            addContextChip(weatherText, android.graphics.Color.parseColor("#2D3748"));
        }

        // Time of day tag (capitalize first letter)
        String timeOfDay = RuleEngine.getCurrentTimeOfDay();
        String capitalized = timeOfDay.substring(0,1).toUpperCase() + timeOfDay.substring(1);
        addContextChip(capitalized, android.graphics.Color.parseColor("#2D3748"));
    }

    // Add a single context chip to the chip group
    private void addContextChip(String text, int backgroundColor) {
        com.google.android.material.chip.Chip chip =
                new com.google.android.material.chip.Chip(requireContext());
        chip.setText(text);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(backgroundColor));
        chip.setTextColor(ContextCompat.getColor(requireContext(),
                com.comp90018.contexttunes.R.color.text_primary));
        chip.setClickable(false);
        chip.setChipCornerRadius(24f);
        binding.contextTagsGroup.addView(chip);
    }

    // ===================== PLACES =====================

    private void fetchNearbyPlaces(Location location) {
        googlePlacesAPI.getNearbyPlaces(location, 300, new GooglePlacesAPI.NearbyPlacesCallback() {
            @Override
            public void onPlacesFound(List<Place> places) {
                if (places.isEmpty()) {
                    Toast.makeText(requireContext(), "No nearby places found", Toast.LENGTH_SHORT).show();
                } else {
                    for (Place p : places) {
                        Log.d(TAG, "Found place: " + p.getDisplayName() + " (" + p.getPrimaryType() + ")");
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

    // ===================== PERMISSION RESULT =====================

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionManager.REQ_LOCATION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (!granted) {
                if (pending == Pending.WEATHER) {
                    updateWeatherStatus(WeatherState.UNKNOWN);
                    Toast.makeText(requireContext(), "Location permission needed for weather", Toast.LENGTH_SHORT).show();
                } else if (pending == Pending.PLACES) {
                    Toast.makeText(requireContext(), "Location permission needed to fetch nearby places", Toast.LENGTH_SHORT).show();
                }
                pending = Pending.NONE;
                return;
            }

            // Granted → resume original intent
            if (pending == Pending.WEATHER) {
                fetchWeatherData();
            } else if (pending == Pending.PLACES) {
                locationSensor.getCurrentLocation(loc -> {
                    if (loc == null) {
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "Couldn't get location", Toast.LENGTH_SHORT).show());
                    } else {
                        fetchNearbyPlaces(loc);
                    }
                });
            }
            pending = Pending.NONE;
        }
    }

    // ===================== SPOTIFY PLAYLIST GENERATION =====================
    private void testSpotifyPlaylistSearch(String query) {
        Log.d(TAG, "Starting playlist search for search key: " + query);

        // Disable GO button
        binding.btnGo.setEnabled(false);

        // Hide all content below welcome card
        binding.contentContainer.setVisibility(View.GONE);

        // Show centered loading UI
        binding.loadingContainer.setVisibility(View.VISIBLE);

        spotifyApiService.searchPlaylists(query, 5, new SpotifyApiService.PlaylistCallback() {
            @Override
            public void onSuccess(List<SpotifyApiService.Playlist> playlists) {
                if (getActivity() == null) return;

                requireActivity().runOnUiThread(() -> {
                    Log.d(TAG, "Search successful! Found " + playlists.size() + " playlists");

                    for (SpotifyApiService.Playlist playlist : playlists) {
                        Log.d(TAG, "====================");
                        Log.d(TAG, "Name: " + playlist.name);
                        Log.d(TAG, "Owner: " + playlist.ownerName);
                        Log.d(TAG, "Tracks: " + playlist.totalTracks);
                        Log.d(TAG, "Description: " + playlist.description);
                        Log.d(TAG, "Image URL: " + playlist.imageUrl);
                        Log.d(TAG, "Spotify URL: " + playlist.externalUrl);
                        Log.d(TAG, "====================");
                    }

                    // Store and display
                    spotifyPlaylists = playlists;
                    playlistsGenerated = true;

                    // Hide loading, show content
                    binding.loadingContainer.setVisibility(View.GONE);
                    binding.contentContainer.setVisibility(View.VISIBLE);
                    binding.btnGo.setEnabled(true);
                    // Change button text to "Regenerate"
                    binding.btnGo.setText("Regenerate");

                    // Populate UI
                    binding.currentMoodCard.setVisibility(View.VISIBLE);
                    populateContextTags();

                    binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);
                    populateSpotifyPlaylistCards();

                    if (playlists.isEmpty()) {
                        Toast.makeText(requireContext(), "No playlists found. Try again!", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            public void onError(String error) {
                if (getActivity() == null) return;

                requireActivity().runOnUiThread(() -> {
                    Log.e(TAG, "Error occurred: " + error);

                    // Hide loading, show content, enable button
                    binding.loadingContainer.setVisibility(View.GONE);
                    binding.contentContainer.setVisibility(View.VISIBLE);
                    binding.btnGo.setEnabled(true);

                    // Show previous results if they existed
                    if (playlistsGenerated) {
                        binding.currentMoodCard.setVisibility(View.VISIBLE);
                        binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);
                    }

                    Toast.makeText(requireContext(), "Error fetching playlists: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // Populate playlist cards based on current recommendations
    private void populateSpotifyPlaylistCards() {
        Log.d(TAG, "Populating " + spotifyPlaylists.size() + " playlist cards");
        binding.playlistCardsContainer.removeAllViews();

        for (SpotifyApiService.Playlist playlist : spotifyPlaylists) {
            View card = getLayoutInflater().inflate(
                    R.layout.item_playlist_card,
                    binding.playlistCardsContainer,
                    false
            );

            ImageView playlistImage = card.findViewById(R.id.playlistImage);
            TextView playlistName = card.findViewById(R.id.playlistName);
            TextView playlistReason = card.findViewById(R.id.playlistReason);
            com.google.android.material.button.MaterialButton btnPlay = card.findViewById(R.id.btnPlay);

            if (playlist.imageUrl != null && !playlist.imageUrl.isEmpty()) {
                Glide.with(requireContext())
                        .load(playlist.imageUrl)
                        .into(playlistImage);
            }

            playlistName.setText(playlist.name);
            playlistReason.setText(playlist.ownerName + " • " + playlist.totalTracks + " tracks");

            btnPlay.setOnClickListener(v -> {
                Log.d(TAG, "Opening playlist: " + playlist.externalUrl);
                openSpotifyPlaylist(playlist.externalUrl);
            });

            binding.playlistCardsContainer.addView(card);
        }
    }

    private void openSpotifyPlaylist(String url) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(requireContext(), "Playlist URL not available", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Cannot open link", Toast.LENGTH_SHORT).show();
        }
    }

    // ===================== LIFECYCLE =====================

    @Override
    public void onStart() {
        super.onStart();
        if (lightSensor != null && settingsManager.isLightEnabled()) {
            lightSensor.start();
        }
    }

    @Override
    public void onStop() {
        if (lightSensor != null) lightSensor.stop();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (mockWeatherService != null) {
            mockWeatherService.shutdown();
        }
        binding = null;
        super.onDestroyView();
    }
}
