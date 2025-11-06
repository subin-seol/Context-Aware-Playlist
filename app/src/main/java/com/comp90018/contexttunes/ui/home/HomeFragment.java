package com.comp90018.contexttunes.ui.home;

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
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

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
import com.comp90018.contexttunes.data.viewModel.ImageViewModel;
import com.comp90018.contexttunes.data.api.SpotifyAPI;
import com.comp90018.contexttunes.domain.SpotifyPlaylist;
import com.comp90018.contexttunes.utils.PermissionManager;
import com.comp90018.contexttunes.utils.PlaylistOpener;
import com.comp90018.contexttunes.utils.SavedPlaylistsManager;
import com.comp90018.contexttunes.utils.SettingsManager;
import com.google.android.libraries.places.api.model.Place;
import com.comp90018.contexttunes.services.SpeedSensorService;
import com.comp90018.contexttunes.utils.AppEvents;

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

    private List<SpotifyPlaylist> spotifyPlaylists = new ArrayList<>();
    private boolean playlistsGenerated = false;
    private boolean recommendationsGenerated = false;

    // Why are we asking for location permission right now?
    private enum Pending { NONE, WEATHER, PLACES }
    private Pending pending = Pending.NONE;
    private SpotifyAPI spotifyAPI;

    // --- Speed sensing (20s test) state ---
    private boolean measuring = false;
    private long   measureStartedAt = 0L;

    // live/last values during the 30s window
    @Nullable private Float  liveSpeedKmh   = null;
    @Nullable private Float  liveCadenceSpm = null;
    @Nullable private String liveActivity   = null;

    // keep the last activity label to feed into RuleEngine after the window
    private String lastActivityLabel = "still";


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
        ImageViewModel viewModel = new ViewModelProvider(requireActivity()).get(ImageViewModel.class);

        // init services/settings
        settingsManager = new SettingsManager(requireContext());
        locationSensor  = new LocationSensor(requireContext());
        googlePlacesAPI = GooglePlacesAPI.getInstance(requireContext());
        mockWeatherService = new MockWeatherService(requireContext());
        spotifyAPI = new SpotifyAPI(BuildConfig.SPOTIFY_ACCESS_TOKEN);


        // Header
        binding.welcomeTitle.setText("Welcome back!");
        updateWeatherStatus(WeatherState.UNKNOWN);

        // Ask once so foreground notification can show on Android 13+
        ensureNotificationPermissionIfNeeded();

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
        binding.btnGo.setOnClickListener(v -> {
            // Start a 10s sensing window together with playlist generation
            if (!measuring) {
                ensurePermsThenSense();
                updateSensingUi();  // show spinner in the Recent Activity panel
            }
            testSpotifyPlaylistSearch("party");
        });

        binding.btnRegenerate.setOnClickListener(v -> testSpotifyPlaylistSearch("party"));
        binding.btnSnapRegen.setOnClickListener(v -> ((MainActivity) requireActivity()).goToSnapTab());

        // --- Sense (10s) test button ---
        binding.btnSenseTest.setOnClickListener(v -> {
            if (measuring) return;
            ensurePermsThenSense();
            updateSensingUi();  // Update button + spinner state
        });

        // ---- Preview captured image ----
        binding.btnPreviewImage.setOnClickListener(v ->
                ((MainActivity) requireActivity()).goToSnapTab()
        );

        // Observe camera state to toggle visibility of buttons
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

        updateRecentActivityTag();
        updateSensingUi();
        updatePlacesUi(null);
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
        }
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
        binding.btnRegenerate.setVisibility(View.GONE);

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

    private void updateSaveButtonIcon(com.google.android.material.button.MaterialButton btnSave, boolean isSaved) {
        if (isSaved) {
            btnSave.setIconResource(com.comp90018.contexttunes.R.drawable.ic_saved);
        } else {
            btnSave.setIconResource(com.comp90018.contexttunes.R.drawable.ic_unsaved);
        }
    }

    // ===================== PLACES =====================
    private void updatePlacesUi(@Nullable List<Place> places) {
        if (binding == null) return;

        String[] labels = new String[] { "–", "–", "–" };

        if (places != null) {
            int count = Math.min(3, places.size());
            for (int i = 0; i < count; i++) {
                Place p = places.get(i);
                String name = p.getDisplayName();
                if (name == null || name.isEmpty()) {
                    name = "–";
                } else if (name.length() > 14) {

                    name = name.substring(0, 13) + "…";
                }
                labels[i] = name;
            }
        }

        binding.placeTag1.setText(labels[0]);
        binding.placeTag2.setText(labels[1]);
        binding.placeTag3.setText(labels[2]);
    }

    private void fetchNearbyPlaces(Location location) {
        googlePlacesAPI.getNearbyPlaces(location, 300, new GooglePlacesAPI.NearbyPlacesCallback() {
            @Override
            public void onPlacesFound(List<Place> places) {

                if (!places.isEmpty()) {
                    for (Place p : places) {
                        Log.d(TAG, "Found place: " + p.getDisplayName() + " (" + p.getPrimaryType() + ")");
                    }
                }


                requireActivity().runOnUiThread(() -> {
                    updatePlacesUi(places);
                    if (places.isEmpty()) {
                        Toast.makeText(requireContext(), "No nearby places found", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(),
                                "Found " + places.size() + " places", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Places API error", e);
                requireActivity().runOnUiThread(() -> {

                    updatePlacesUi(null);
                    Toast.makeText(requireContext(), "Places API error", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }


    // ===================== SPEED SENSING =====================
    // Receive frames from SpeedSensorService (~1 Hz)
    private final BroadcastReceiver speedRx = new BroadcastReceiver() {
        @Override public void onReceive(android.content.Context ctx, Intent i) {
            if (!AppEvents.ACTION_SPEED_UPDATE.equals(i.getAction())) return;

            float kmh = i.getFloatExtra(AppEvents.EXTRA_SPEED_KMH, 0f);
            float spm = i.getFloatExtra(AppEvents.EXTRA_CADENCE_SPM, Float.NaN);
            String act = i.getStringExtra(AppEvents.EXTRA_ACTIVITY);
            boolean isFinal = i.getBooleanExtra(AppEvents.EXTRA_IS_FINAL, false);

            liveSpeedKmh   = kmh;
            liveCadenceSpm = Float.isNaN(spm) ? null : spm;
            liveActivity   = (act == null || act.isEmpty()) ? "still" : act;

            // Optional: show live numbers somewhere (Toast or a small TextView)
            // For a simple smoke test:
            // Toast.makeText(requireContext(), String.format(Locale.getDefault(),
            //         "%s · %.1f km/h · %s", liveActivity, kmh,
            //         (liveCadenceSpm==null?"–":String.format("%.0f spm", liveCadenceSpm))), Toast.LENGTH_SHORT).show();

            if (isFinal) {
                measuring = false;
                lastActivityLabel = (liveActivity == null ? "still" : liveActivity);

                // Update UI: stop spinner, refresh chip text
                updateSensingUi();
                updateRecentActivityTag();

                onSensingFinished();  // optional Toast summary
            }
        }
    };


    /** Update the Recent Activity chip with the last activity label. */
    private void updateRecentActivityTag() {
        if (binding == null) return;

        String label = (lastActivityLabel == null || lastActivityLabel.isEmpty())
                ? "still"
                : lastActivityLabel;

        // Capitalize first character for display: "walking" -> "Walking"
        String display = label.substring(0, 1).toUpperCase() + label.substring(1).toLowerCase();
        binding.recentTag.setText(display);
    }

    /** Update SENSE button + spinner based on current measuring flag. */
    private void updateSensingUi() {
        if (binding == null) return;

        if (measuring) {
            // Disable button and show spinner
            binding.btnSenseTest.setEnabled(false);
            binding.btnSenseTest.setText("");
            binding.progressSense.setVisibility(View.VISIBLE);
        } else {
            // Enable button and restore text
            binding.btnSenseTest.setEnabled(true);
            binding.progressSense.setVisibility(View.GONE);
            binding.btnSenseTest.setText("SENSE");
        }
    }


    /** Kick a 20s sensing window (foreground service via Intent action). */
    private void startSpeedWindow() {
        Intent i = new Intent(requireContext(), SpeedSensorService.class)
                .setAction(AppEvents.ACTION_SPEED_SAMPLE_NOW);
        androidx.core.content.ContextCompat.startForegroundService(requireContext(), i);
    }

    /** Gate by permissions, then start the window. */
    private void ensurePermsThenSense() {
        if (PermissionManager.hasActivityRecognition(requireContext())
                && PermissionManager.hasAnyLocation(requireContext())) {
            measuring = true;
            measureStartedAt = System.currentTimeMillis();
            liveSpeedKmh = null; liveCadenceSpm = null; liveActivity = null;

            updateSensingUi();  // Refresh SENSE button -> spinner

            Toast.makeText(requireContext(), "Measuring activity for 10s…", Toast.LENGTH_SHORT).show();
            startSpeedWindow();
        } else {
            PermissionManager.requestSpeedSensing(this);
            // After user grants in onRequestPermissionsResult, call ensurePermsThenSense() again
        }
    }

    /** Show the final sensed values without touching recommendations. */
    // Called when measuring window ends in final frame
    private void onSensingFinished() {
        String summary = String.format(
                java.util.Locale.getDefault(),
                "Detected: %s • %s • %s",
                (liveActivity == null ? "still" :
                        liveActivity.substring(0,1).toUpperCase()+liveActivity.substring(1)),
                (liveSpeedKmh==null ? "-" : String.format(java.util.Locale.getDefault(), "%.1f km/h", liveSpeedKmh)),
                (liveCadenceSpm==null ? "-" : String.format(java.util.Locale.getDefault(), "%.0f spm", liveCadenceSpm))
        );
        Toast.makeText(requireContext(), summary, Toast.LENGTH_SHORT).show();
    }

    private static final int REQ_NOTIFICATIONS = 2001;

    private void ensureNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{ android.Manifest.permission.POST_NOTIFICATIONS },
                        REQ_NOTIFICATIONS
                );
            }
        }
    }


    // ===================== PERMISSION RESULT =====================

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // --- SENSING PERMISSIONS (Activity + Multi-Location) ---
        if (requestCode == PermissionManager.REQ_LOCATION_MULTI
                || requestCode == PermissionManager.REQ_ACTIVITY) {
            boolean anyGranted = false;
            for (int r : grantResults) if (r == PackageManager.PERMISSION_GRANTED) { anyGranted = true; break; }
            if (anyGranted) {
                ensurePermsThenSense();
            } else {
                Toast.makeText(requireContext(), "Permissions needed for activity sensing", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // --- WEATHER / PLACES LOCATION (single fine location) ---
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

        // Disable buttons
        binding.btnGo.setEnabled(false);
        if (playlistsGenerated) {
            binding.btnRegenerate.setEnabled(false);
        }

        // HIDE EVERYTHING
        binding.welcomeCard.setVisibility(View.GONE);
        binding.createVibeCard.setVisibility(View.GONE);
        binding.currentMoodCard.setVisibility(View.GONE);
        binding.regenerateCard.setVisibility(View.GONE);
        binding.playlistSuggestionsSection.setVisibility(View.GONE);
        binding.statsRow.setVisibility(View.GONE);
        binding.recentTitle.setVisibility(View.GONE);
        binding.recentItem.setVisibility(View.GONE);
        // binding.btnFetchPlaces.setVisibility(View.GONE);

        // Show loading
        binding.loadingContainer.setVisibility(View.VISIBLE);

        spotifyAPI.searchPlaylists(query, 5, new SpotifyAPI.PlaylistCallback() {
            @Override
            public void onSuccess(List<SpotifyPlaylist> playlists) {
                if (getActivity() == null) return;

                requireActivity().runOnUiThread(() -> {
                    spotifyPlaylists = playlists;
                    playlistsGenerated = true;

                    binding.loadingContainer.setVisibility(View.GONE);
                    binding.btnGo.setEnabled(true);
                    binding.btnRegenerate.setEnabled(true);

                    // HIDE: welcome, create vibe, stats, recent activity
                    binding.welcomeCard.setVisibility(View.GONE);
                    binding.createVibeCard.setVisibility(View.GONE);
                    binding.statsRow.setVisibility(View.GONE);
                    binding.recentTitle.setVisibility(View.GONE);
                    binding.recentItem.setVisibility(View.GONE);
                    // binding.btnFetchPlaces.setVisibility(View.GONE);

                    // SHOW: current mood (centered), regenerate card, playlists
                    binding.currentMoodCard.setVisibility(View.VISIBLE);
                    binding.regenerateCard.setVisibility(View.VISIBLE);
                    binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);

                    populateContextTags();
                    populateSpotifyPlaylistCards();

                    // Inline empty state (no toast-only)
                    binding.playlistEmptyText.setVisibility(
                            playlists.isEmpty() ? View.VISIBLE : View.GONE
                    );
                });
            }

            public void onError(String error) {
                if (getActivity() == null) return;

                requireActivity().runOnUiThread(() -> {
                    Log.e(TAG, "Error occurred: " + error);

                    // Hide loading
                    binding.loadingContainer.setVisibility(View.GONE);
                    binding.btnGo.setEnabled(true);
                    binding.btnRegenerate.setEnabled(true);

                    if (playlistsGenerated) {
                        // Already had playlists - show AFTER generation state
                        binding.currentMoodCard.setVisibility(View.VISIBLE);
                        binding.regenerateCard.setVisibility(View.VISIBLE);
                        binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);
                        // Show inline error as empty state
                        binding.playlistCardsContainer.removeAllViews();
                        binding.playlistEmptyText.setText(getString(R.string.no_playlists_found));
                        binding.playlistEmptyText.setVisibility(View.VISIBLE);
                    } else {
                        // First time error - show BEFORE generation state
                        binding.welcomeCard.setVisibility(View.VISIBLE);
                        binding.createVibeCard.setVisibility(View.VISIBLE);
                        binding.statsRow.setVisibility(View.VISIBLE);
                        binding.recentTitle.setVisibility(View.VISIBLE);
                        binding.recentItem.setVisibility(View.VISIBLE);
                        Toast.makeText(requireContext(), "Error fetching playlists. Try again.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // Populate playlist cards based on current recommendations
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
            com.google.android.material.button.MaterialButton btnSave =
                    card.findViewById(R.id.btnSave); // bookmark toggle

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

            // Initial saved state & icon
            boolean isSaved = saved.isSpotifyPlaylistSaved(playlist);
            updateSaveButtonIcon(btnSave, isSaved);
            btnSave.setOnClickListener(v -> {
                boolean currentlySaved = saved.isSpotifyPlaylistSaved(playlist);
                if (currentlySaved) {
                    saved.unsaveSpotifyPlaylist(playlist);
                    Toast.makeText(requireContext(), "Playlist removed from saved", Toast.LENGTH_SHORT).show();
                } else {
                    saved.saveSpotifyPlaylist(playlist);
                    Toast.makeText(requireContext(), "Playlist saved", Toast.LENGTH_SHORT).show();
                }
                updateSaveButtonIcon(btnSave, !currentlySaved);
            });

            binding.playlistCardsContainer.addView(card);
        }
    }

    // ===================== LIFECYCLE =====================

    @Override
    public void onStart() {
        super.onStart();
        if (lightSensor != null && settingsManager.isLightEnabled()) {
            lightSensor.start();
        }

        // listen only while fragment is visible
        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(speedRx, new IntentFilter(AppEvents.ACTION_SPEED_UPDATE));
    }

    @Override
    public void onStop() {
        if (lightSensor != null) lightSensor.stop();
        try {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(speedRx);
        } catch (Exception ignore) {}
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (mockWeatherService != null) {
            mockWeatherService.shutdown();
        }
        if (spotifyAPI != null) {
            spotifyAPI.shutdown();
        }
        binding = null;
        super.onDestroyView();
    }
}
