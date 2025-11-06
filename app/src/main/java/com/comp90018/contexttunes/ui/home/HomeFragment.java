package com.comp90018.contexttunes.ui.home;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.bumptech.glide.Glide;
import com.comp90018.contexttunes.BuildConfig;
import com.comp90018.contexttunes.MainActivity;
import com.comp90018.contexttunes.R;
import com.comp90018.contexttunes.data.api.SpotifyAPI;
import com.comp90018.contexttunes.data.sensors.LightSensor;
import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;
import com.comp90018.contexttunes.data.viewModel.HomeStateViewModel;
import com.comp90018.contexttunes.data.viewModel.ImageViewModel;
import com.comp90018.contexttunes.data.weather.WeatherService;
import com.comp90018.contexttunes.data.weather.WeatherService.WeatherState;
import com.comp90018.contexttunes.databinding.FragmentHomeBinding;
import com.comp90018.contexttunes.domain.Context;
import com.comp90018.contexttunes.domain.Recommendation;
import com.comp90018.contexttunes.domain.RuleEngine;
import com.comp90018.contexttunes.domain.SpotifyPlaylist;
import com.comp90018.contexttunes.services.SpeedSensorService;
import com.comp90018.contexttunes.utils.AppEvents;
import com.comp90018.contexttunes.utils.PermissionManager;
import com.comp90018.contexttunes.utils.PlaylistOpener;
import com.comp90018.contexttunes.utils.SavedPlaylistsManager;
import com.comp90018.contexttunes.utils.SettingsManager;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";

    private FragmentHomeBinding binding;
    private SettingsManager settingsManager;
    private LightSensor lightSensor;
    private WeatherService weatherService;
    private SpotifyAPI spotifyAPI;

    private WeatherState currentWeather = WeatherState.UNKNOWN;
    private LightBucket currentLightBucket = LightBucket.UNKNOWN;

    private List<SpotifyPlaylist> spotifyPlaylists = new ArrayList<>();
    private boolean playlistsGenerated = false;
    private boolean recommendationsGenerated = false;

    // Speed sensing state
    private boolean measuring = false;
    @Nullable private Float liveSpeedKmh = null;
    @Nullable private Float liveCadenceSpm = null;
    @Nullable private String liveActivity = null;
    private String lastActivityLabel = "still";

    private HomeStateViewModel homeStateViewModel;

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

        // Initialize services
        settingsManager = new SettingsManager(requireContext());
        weatherService = new WeatherService(requireContext());
        spotifyAPI = new SpotifyAPI(BuildConfig.SPOTIFY_ACCESS_TOKEN);

        // Get ViewModels
        ImageViewModel imageViewModel = new ViewModelProvider(requireActivity()).get(ImageViewModel.class);
        homeStateViewModel = new ViewModelProvider(requireActivity()).get(HomeStateViewModel.class);

        // Ask for notification permission (Android 13+)
        ensureNotificationPermissionIfNeeded();

        // Observe playlists from ViewModel
        homeStateViewModel.getPlaylists().observe(getViewLifecycleOwner(), playlists -> {
            if (playlists != null) {
                this.spotifyPlaylists = playlists;
                updateUIState();
            }
        });

        // Observe recommendations generated state
        homeStateViewModel.getRecommendationsGenerated().observe(getViewLifecycleOwner(), isGenerated -> {
            if (isGenerated != null) {
                this.playlistsGenerated = isGenerated;
                this.recommendationsGenerated = isGenerated;
                updateUIState();
            }
        });

        // Observe weather state
        homeStateViewModel.getWeatherState().observe(getViewLifecycleOwner(), weather -> {
            if (weather != null) {
                currentWeather = weather;
                updateWeatherStatus(weather);
            }
        });

        // Initialize light sensor if enabled
        if (settingsManager.isLightEnabled()) {
            lightSensor = new LightSensor(requireContext(), bucket -> {
                if (binding == null) return;
                requireActivity().runOnUiThread(() -> {
                    updateLightStatus(bucket);
                    updateRecommendation(bucket);
                });
            });
        }

        // Fetch weather if location enabled
        if (settingsManager.isLocationEnabled()) {
            ensureLocationAndFetchWeather();
        } else {
            updateWeatherStatus(WeatherState.UNKNOWN);
        }

        // Set up button listeners
        setupButtonListeners(imageViewModel);

        // Set initial recommendation
        updateRecommendation(null);
    }

    private void setupButtonListeners(ImageViewModel imageViewModel) {
        // Snap button - go to camera tab
        binding.btnSnap.setOnClickListener(v ->
                ((MainActivity) requireActivity()).goToSnapTab()
        );

        // Preview captured image button
        binding.btnPreviewImage.setOnClickListener(v ->
                ((MainActivity) requireActivity()).goToSnapTab()
        );

        // Generate playlists button
        binding.btnGo.setOnClickListener(v -> generatePlaylists());

        // Regenerate playlists button
        binding.btnRegenerate.setOnClickListener(v -> generatePlaylists());

        // Regenerate with new snap
        binding.btnSnapRegen.setOnClickListener(v ->
                ((MainActivity) requireActivity()).goToSnapTab()
        );

        // Observe camera state to toggle buttons
        imageViewModel.getCapturedImage().observe(getViewLifecycleOwner(), bitmap -> {
            if (bitmap != null) {
                binding.btnSnap.setVisibility(View.GONE);
                binding.btnPreviewImage.setVisibility(View.VISIBLE);
            } else {
                binding.btnSnap.setVisibility(View.VISIBLE);
                binding.btnPreviewImage.setVisibility(View.GONE);
            }
        });
    }

    // ===================== PERMISSION HANDLING =====================

    private void ensureLocationAndFetchWeather() {
        if (PermissionManager.hasLocationPermission(requireContext())) {
            fetchWeatherData();
        } else {
            PermissionManager.requestLocation(this);
        }
    }

    private void ensureNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        2001
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Speed sensing permissions
        if (requestCode == PermissionManager.REQ_LOCATION_MULTI
                || requestCode == PermissionManager.REQ_ACTIVITY) {
            boolean anyGranted = false;
            for (int r : grantResults)
                if (r == PackageManager.PERMISSION_GRANTED) {
                    anyGranted = true;
                    break;
                }
            if (anyGranted) {
                ensurePermsThenSense();
            } else {
                Toast.makeText(requireContext(),
                        "Permissions needed for activity sensing",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Weather location permission
        if (requestCode == PermissionManager.REQ_LOCATION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                fetchWeatherData();
            } else {
                updateWeatherStatus(WeatherState.UNKNOWN);
                Toast.makeText(requireContext(),
                        "Location permission needed for weather",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ===================== WEATHER & LIGHT =====================

    private void fetchWeatherData() {
        Toast.makeText(requireContext(), "Fetching weather data...", Toast.LENGTH_SHORT).show();

        weatherService.getCurrentWeather(weather -> {
            requireActivity().runOnUiThread(() -> {
                currentWeather = weather;
                homeStateViewModel.setWeatherState(weather);
                updateRecommendation(null);
                updateWeatherStatus(currentWeather);
                Log.d(TAG, "Weather: " + weather.name());
            });
        });
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

        if (!recommendationsGenerated) {
            String timeOfDay = RuleEngine.getCurrentTimeOfDay();
            String activity = "still";
            Context ctx = new Context(currentLightBucket, timeOfDay, activity, currentWeather);

            Recommendation rec = RuleEngine.getRecommendation(ctx);
            binding.welcomeSubtitle.setText(rec.reason);
        }
    }

    // ===================== UI STATE MANAGEMENT =====================

    private void updateUIState() {
        if (binding == null) return;

        if (playlistsGenerated && !spotifyPlaylists.isEmpty()) {
            showGeneratedState();
        } else {
            showBeforeGenerationState();
        }
    }

    private void showBeforeGenerationState() {
        if (binding == null) return;

        // Show before generation elements
        binding.welcomeCard.setVisibility(View.VISIBLE);
        binding.createVibeCard.setVisibility(View.VISIBLE);
        binding.welcomeSubtitle.setVisibility(View.VISIBLE);

        // Hide after generation elements
        binding.currentMoodCard.setVisibility(View.GONE);
        binding.regenerateCard.setVisibility(View.GONE);
        binding.playlistSuggestionsSection.setVisibility(View.GONE);

        recommendationsGenerated = false;
    }

    private void showGeneratedState() {
        if (binding == null) return;

        // Hide before generation elements
        binding.welcomeCard.setVisibility(View.GONE);
        binding.createVibeCard.setVisibility(View.GONE);
        binding.welcomeSubtitle.setVisibility(View.GONE);

        // Show after generation elements
        binding.currentMoodCard.setVisibility(View.VISIBLE);
        binding.regenerateCard.setVisibility(View.VISIBLE);
        binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);

        populateContextTags();
        populateSpotifyPlaylistCards();
    }

    private void populateContextTags() {
        binding.contextTagsGroup.removeAllViews();

        // Activity tag (using lastActivityLabel from speed sensing)
        String activityText = lastActivityLabel.substring(0, 1).toUpperCase()
                + lastActivityLabel.substring(1);
        addContextChip(activityText, android.graphics.Color.parseColor("#2D3748"));

        // Light tag
        String lightText;
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
            default:
                lightText = null;
        }
        if (lightText != null) {
            addContextChip(lightText, android.graphics.Color.parseColor("#2D3748"));
        }

        // Weather tag
        String weatherText;
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
            default:
                weatherText = null;
        }
        if (weatherText != null) {
            addContextChip(weatherText, android.graphics.Color.parseColor("#2D3748"));
        }

        // Time of day tag
        String timeOfDay = RuleEngine.getCurrentTimeOfDay();
        String capitalized = timeOfDay.substring(0, 1).toUpperCase() + timeOfDay.substring(1);
        addContextChip(capitalized, android.graphics.Color.parseColor("#2D3748"));
    }

    private void addContextChip(String text, int backgroundColor) {
        com.google.android.material.chip.Chip chip =
                new com.google.android.material.chip.Chip(requireContext());
        chip.setText(text);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(backgroundColor));
        chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        chip.setClickable(false);
        chip.setChipCornerRadius(24f);
        binding.contextTagsGroup.addView(chip);
    }

    // ===================== SPOTIFY PLAYLIST GENERATION =====================

    private void generatePlaylists() {
        Log.d(TAG, "Generating playlists");

        // Disable buttons
        binding.btnGo.setEnabled(false);
        if (playlistsGenerated) {
            binding.btnRegenerate.setEnabled(false);
        }

        // Hide everything
        binding.welcomeCard.setVisibility(View.GONE);
        binding.createVibeCard.setVisibility(View.GONE);
        binding.currentMoodCard.setVisibility(View.GONE);
        binding.regenerateCard.setVisibility(View.GONE);
        binding.playlistSuggestionsSection.setVisibility(View.GONE);

        // Show loading
        binding.loadingContainer.setVisibility(View.VISIBLE);

        // TODO: Replace with actual context-based query
        String query = "party";

        spotifyAPI.searchPlaylists(query, 5, new SpotifyAPI.PlaylistCallback() {
            @Override
            public void onSuccess(List<SpotifyPlaylist> playlists) {
                if (getActivity() == null) return;

                requireActivity().runOnUiThread(() -> {
                    spotifyPlaylists = playlists;
                    homeStateViewModel.setPlaylists(playlists);
                    homeStateViewModel.setRecommendationsGenerated(true);

                    playlistsGenerated = true;

                    binding.loadingContainer.setVisibility(View.GONE);
                    binding.btnGo.setEnabled(true);
                    binding.btnRegenerate.setEnabled(true);

                    // Show after generation state
                    binding.currentMoodCard.setVisibility(View.VISIBLE);
                    binding.regenerateCard.setVisibility(View.VISIBLE);
                    binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);

                    populateContextTags();
                    populateSpotifyPlaylistCards();

                    // Handle empty state
                    binding.playlistEmptyText.setVisibility(
                            playlists.isEmpty() ? View.VISIBLE : View.GONE
                    );
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;

                requireActivity().runOnUiThread(() -> {
                    Log.e(TAG, "Error occurred: " + error);

                    binding.loadingContainer.setVisibility(View.GONE);
                    binding.btnGo.setEnabled(true);
                    binding.btnRegenerate.setEnabled(true);

                    if (playlistsGenerated) {
                        // Show after generation state with error
                        binding.currentMoodCard.setVisibility(View.VISIBLE);
                        binding.regenerateCard.setVisibility(View.VISIBLE);
                        binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);
                        binding.playlistCardsContainer.removeAllViews();
                        binding.playlistEmptyText.setText(getString(R.string.no_playlists_found));
                        binding.playlistEmptyText.setVisibility(View.VISIBLE);
                    } else {
                        // Show before generation state
                        binding.welcomeCard.setVisibility(View.VISIBLE);
                        binding.createVibeCard.setVisibility(View.VISIBLE);
                        Toast.makeText(requireContext(),
                                "Error fetching playlists. Try again.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void populateSpotifyPlaylistCards() {
        Log.d(TAG, "Populating " + spotifyPlaylists.size() + " playlist cards");
        binding.playlistCardsContainer.removeAllViews();

        SavedPlaylistsManager saved = new SavedPlaylistsManager(requireContext());

        for (SpotifyPlaylist playlist : spotifyPlaylists) {
            View card = getLayoutInflater().inflate(
                    R.layout.item_playlist_card,
                    binding.playlistCardsContainer,
                    false
            );

            ImageView playlistImage = card.findViewById(R.id.playlistImage);
            TextView playlistName = card.findViewById(R.id.playlistName);
            TextView playlistMeta = card.findViewById(R.id.playlistMeta);
            com.google.android.material.button.MaterialButton btnPlay = card.findViewById(R.id.btnPlay);
            com.google.android.material.button.MaterialButton btnSave = card.findViewById(R.id.btnSave);

            if (playlist.imageUrl != null && !playlist.imageUrl.isEmpty()) {
                Glide.with(requireContext())
                        .load(playlist.imageUrl)
                        .into(playlistImage);
            }

            playlistName.setText(playlist.name);
            playlistMeta.setText(playlist.ownerName + " • " + playlist.totalTracks + " tracks");

            btnPlay.setOnClickListener(v -> {
                Log.d(TAG, "Opening playlist: " + playlist.externalUrl);
                PlaylistOpener.openPlaylist(requireContext(), playlist);
            });

            // Save/unsave button
            boolean isSaved = saved.isSpotifyPlaylistSaved(playlist);
            updateSaveButtonIcon(btnSave, isSaved);
            btnSave.setOnClickListener(v -> {
                boolean currentlySaved = saved.isSpotifyPlaylistSaved(playlist);
                if (currentlySaved) {
                    saved.unsaveSpotifyPlaylist(playlist);
                    Toast.makeText(requireContext(),
                            "Playlist removed from saved",
                            Toast.LENGTH_SHORT).show();
                } else {
                    saved.saveSpotifyPlaylist(playlist);
                    Toast.makeText(requireContext(),
                            "Playlist saved",
                            Toast.LENGTH_SHORT).show();
                }
                updateSaveButtonIcon(btnSave, !currentlySaved);
            });

            binding.playlistCardsContainer.addView(card);
        }
    }

    private void updateSaveButtonIcon(com.google.android.material.button.MaterialButton btnSave,
                                      boolean isSaved) {
        if (isSaved) {
            btnSave.setIconResource(R.drawable.ic_saved);
        } else {
            btnSave.setIconResource(R.drawable.ic_unsaved);
        }
    }

    // ===================== SPEED SENSING =====================

    private final BroadcastReceiver speedRx = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context ctx, Intent i) {
            if (!AppEvents.ACTION_SPEED_UPDATE.equals(i.getAction())) return;

            float kmh = i.getFloatExtra(AppEvents.EXTRA_SPEED_KMH, 0f);
            float spm = i.getFloatExtra(AppEvents.EXTRA_CADENCE_SPM, Float.NaN);
            String act = i.getStringExtra(AppEvents.EXTRA_ACTIVITY);
            boolean isFinal = i.getBooleanExtra(AppEvents.EXTRA_IS_FINAL, false);

            liveSpeedKmh = kmh;
            liveCadenceSpm = Float.isNaN(spm) ? null : spm;
            liveActivity = (act == null || act.isEmpty()) ? "still" : act;

            if (isFinal) {
                measuring = false;
                lastActivityLabel = liveActivity == null ? "still" : liveActivity;
                onSensingFinished();
            }
        }
    };

    private void ensurePermsThenSense() {
        if (PermissionManager.hasActivityRecognition(requireContext())
                && PermissionManager.hasAnyLocation(requireContext())) {
            measuring = true;
            liveSpeedKmh = null;
            liveCadenceSpm = null;
            liveActivity = null;

            Toast.makeText(requireContext(), "Measuring activity for 20s…", Toast.LENGTH_SHORT).show();
            startSpeedWindow();
        } else {
            PermissionManager.requestSpeedSensing(this);
        }
    }

    private void startSpeedWindow() {
        Intent i = new Intent(requireContext(), SpeedSensorService.class)
                .setAction(AppEvents.ACTION_SPEED_SAMPLE_NOW);
        androidx.core.content.ContextCompat.startForegroundService(requireContext(), i);
    }

    private void onSensingFinished() {
        String summary = String.format(
                java.util.Locale.getDefault(),
                "Detected: %s • %s • %s",
                (liveActivity == null ? "still" :
                        liveActivity.substring(0, 1).toUpperCase() + liveActivity.substring(1)),
                (liveSpeedKmh == null ? "-" :
                        String.format(java.util.Locale.getDefault(), "%.1f km/h", liveSpeedKmh)),
                (liveCadenceSpm == null ? "-" :
                        String.format(java.util.Locale.getDefault(), "%.0f spm", liveCadenceSpm))
        );
        Toast.makeText(requireContext(), summary, Toast.LENGTH_SHORT).show();
    }

    // ===================== LIFECYCLE =====================

    @Override
    public void onStart() {
        super.onStart();
        if (lightSensor != null && settingsManager.isLightEnabled()) {
            lightSensor.start();
        }

        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(speedRx, new IntentFilter(AppEvents.ACTION_SPEED_UPDATE));
    }

    @Override
    public void onStop() {
        if (lightSensor != null) lightSensor.stop();
        try {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(speedRx);
        } catch (Exception ignore) {
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (weatherService != null) {
            weatherService.shutdown();
        }
        if (spotifyAPI != null) {
            spotifyAPI.shutdown();
        }
        binding = null;
        super.onDestroyView();
    }
}