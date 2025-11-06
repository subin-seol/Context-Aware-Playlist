package com.comp90018.contexttunes.ui.home;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import com.comp90018.contexttunes.data.sensors.LocationSensor;
import com.comp90018.contexttunes.data.viewModel.HomeStateViewModel;
import com.comp90018.contexttunes.data.viewModel.ImageViewModel;
import com.comp90018.contexttunes.data.weather.WeatherService;
import com.comp90018.contexttunes.data.weather.WeatherService.WeatherState;
import com.comp90018.contexttunes.databinding.FragmentHomeBinding;
import com.comp90018.contexttunes.domain.AIPlaylistRecommender;
import com.comp90018.contexttunes.domain.Context;
import com.comp90018.contexttunes.domain.ImageLabels;
import com.comp90018.contexttunes.domain.SearchRecommendation;
import com.comp90018.contexttunes.domain.SpotifyPlaylist;
import com.comp90018.contexttunes.services.SpeedSensorService;
import com.comp90018.contexttunes.utils.AppEvents;
import com.comp90018.contexttunes.utils.LocationContextHelper;
import com.comp90018.contexttunes.utils.PermissionManager;
import com.comp90018.contexttunes.utils.PlaylistOpener;
import com.comp90018.contexttunes.utils.SavedPlaylistsManager;
import com.comp90018.contexttunes.utils.SettingsManager;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final int DEFAULT_WINDOW_SECONDS = 20;

    private FragmentHomeBinding binding;
    private SettingsManager settingsManager;
    private LightSensor lightSensor;
    private LocationSensor locationSensor;
    private LocationContextHelper locationHelper;
    private AIPlaylistRecommender aiRecommender;
    private SpotifyAPI spotifyAPI;
    private WeatherService weatherService;

    // ViewModels
    private HomeStateViewModel homeStateVM;
    private ImageViewModel imageVM;

    private WeatherState currentWeather = WeatherState.UNKNOWN;
    private LightBucket currentLightBucket = LightBucket.UNKNOWN;

    // timer to guarantee window end even if service isn't running
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    // ---- window coordination (speed + labels) ----
    private volatile boolean speedReady  = false;
    private volatile boolean labelsReady = false;
    private static final int LABELS_TIMEOUT_MS = 8000;
    @Nullable private Runnable windowWatchdog = null;

    // live values from SpeedSensorService
    @Nullable private Float  liveSpeedKmh   = null;
    @Nullable private Float  liveCadenceSpm = null;
    @Nullable private String liveActivity   = null;
    private String lastActivityLabel = "still";

    // camera labels
    @Nullable private ImageLabels currentImageLabels = null;

    // The exact context used for AI/Spotify (for chips)
    @Nullable private Context lastContext = null;

    // ===================== LIFECYCLE =====================

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

        // Initialize ViewModels (scoped to Activity so they survive fragment recreation)
        homeStateVM = new ViewModelProvider(requireActivity()).get(HomeStateViewModel.class);
        imageVM = new ViewModelProvider(requireActivity()).get(ImageViewModel.class);

        // Observe image state from ImageViewModel
        imageVM.getCapturedImage().observe(getViewLifecycleOwner(), bitmap -> {
            binding.btnSnap.setVisibility(bitmap != null ? View.GONE : View.VISIBLE);
            binding.btnPreviewImage.setVisibility(bitmap != null ? View.VISIBLE : View.GONE);
        });

        imageVM.getImageLabels().observe(getViewLifecycleOwner(), labels -> {
            currentImageLabels = labels;
        });

        // Observe playlist state from HomeStateViewModel
        homeStateVM.getPlaylists().observe(getViewLifecycleOwner(), playlists -> {
            if (playlists != null && !playlists.isEmpty()) {
                populateSpotifyPlaylistCards(playlists);
                binding.playlistEmptyText.setVisibility(View.GONE);
            }
        });

        // Observe recommendations generated state
        homeStateVM.getRecommendationsGenerated().observe(getViewLifecycleOwner(), generated -> {
            if (generated != null && generated) {
                // Show "after generation" UI state
                binding.welcomeCard.setVisibility(View.GONE);
                binding.createVibeCard.setVisibility(View.GONE);
                binding.currentMoodCard.setVisibility(View.VISIBLE);
                binding.regenerateCard.setVisibility(View.VISIBLE);
                binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);

                // Restore context chips if we have lastContext stored
                if (lastContext != null) {
                    populateContextChipsFor(lastContext);
                }
            } else {
                // Show "before generation" UI state
                binding.welcomeCard.setVisibility(View.VISIBLE);
                binding.createVibeCard.setVisibility(View.VISIBLE);
                binding.currentMoodCard.setVisibility(View.GONE);
                binding.regenerateCard.setVisibility(View.GONE);
                binding.playlistSuggestionsSection.setVisibility(View.GONE);
            }
        });

        // init services/settings
        settingsManager   = new SettingsManager(requireContext());
        locationSensor    = new LocationSensor(requireContext());
        locationHelper    = new LocationContextHelper(requireContext());
        weatherService    = new WeatherService(requireContext());
        aiRecommender     = new AIPlaylistRecommender();
        spotifyAPI        = new SpotifyAPI(BuildConfig.SPOTIFY_ACCESS_TOKEN);

        // Header
        binding.welcomeTitle.setText("Welcome back!");
        updateWeatherStatus(WeatherState.UNKNOWN);

        // Ask once so foreground notification can show on Android 13+
        ensureNotificationPermissionIfNeeded();

        // LIGHT
        if (settingsManager.isLightEnabled()) {
            lightSensor = new LightSensor(requireContext(), bucket -> {
                if (binding == null) return;
                requireActivity().runOnUiThread(() -> {
                    currentLightBucket = bucket;
                    updateLightStatus(bucket);
                });
            });
        }

        // WEATHER
        if (settingsManager.isLocationEnabled()) {
            ensureLocationAndFetchWeather();
        } else {
            updateWeatherStatus(WeatherState.UNKNOWN);
        }

        // SNAP nav
        binding.btnSnap.setOnClickListener(v -> ((MainActivity) requireActivity()).openSnap());
        binding.btnPreviewImage.setOnClickListener(v -> ((MainActivity) requireActivity()).openSnap());

        // GO / Regenerate
        binding.btnGo.setOnClickListener(v -> beginWindow(DEFAULT_WINDOW_SECONDS));
        binding.btnRegenerate.setOnClickListener(v -> beginWindow(DEFAULT_WINDOW_SECONDS));
    }

    // ===================== PERMISSION-GATED ACTIONS =====================

    private void ensureLocationAndFetchWeather() {
        if (PermissionManager.hasLocationPermission(requireContext())) {
            fetchWeatherData();
        } else {
            PermissionManager.requestLocation(this);
        }
    }

    // ===================== WEATHER + LIGHT UI =====================

    private void fetchWeatherData() {
        Toast.makeText(requireContext(), "Fetching weather data...", Toast.LENGTH_SHORT).show();

        weatherService.getCurrentWeather(mockWeather -> {
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

    // ===================== FIXED 20s WINDOW PIPELINE =====================

    private void beginWindow(int seconds) {
        onGenerationStart();

        boolean speedEnabled = settingsManager.isAccelerometerEnabled();
        boolean hasAR = PermissionManager.hasActivityRecognition(requireContext());
        boolean hasAnyLoc = PermissionManager.hasAnyLocation(requireContext());

        if (speedEnabled && hasAR && hasAnyLoc) {
            startSpeedWindow(seconds);
        } else if (speedEnabled && (!hasAR || !hasAnyLoc)) {
            PermissionManager.requestSpeedSensing(this);
        }

        speedReady = !settingsManager.isAccelerometerEnabled();
        labelsReady = true;

        maybeStartImageLabelsWork();
        armWatchdog(seconds);
    }

    private void maybeStartImageLabelsWork() {
        Bitmap img = imageVM.getCapturedImage().getValue();
        ImageLabels labels = imageVM.getImageLabels().getValue();

        if (img == null) {
            labelsReady = true;
            return;
        }

        if (labels != null && labels.matchesCurrentImage(img)) {
            labelsReady = true;
            return;
        }

        labelsReady = false;

        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable timeout = () -> {
            if (!labelsReady) {
                Log.w(TAG, "Labels timeout — proceeding without image labels.");
                labelsReady = true;
                tryProceedIfAllReady();
            }
        };
        h.postDelayed(timeout, LABELS_TIMEOUT_MS);

        imageVM.getImageLabels().observe(getViewLifecycleOwner(), newLabels -> {
            if (newLabels != null && newLabels.matchesCurrentImage(img)) {
                labelsReady = true;
                h.removeCallbacks(timeout);
                imageVM.getImageLabels().removeObservers(getViewLifecycleOwner());
                tryProceedIfAllReady();
            }
        });

        new com.comp90018.contexttunes.services.ImageAnalyser(requireContext())
                .analyzeImage(imageVM);
    }

    private void startSpeedWindow(int seconds) {
        Intent i = new Intent(requireContext(), SpeedSensorService.class)
                .setAction(AppEvents.ACTION_SPEED_SAMPLE_NOW);
        i.putExtra(AppEvents.EXTRA_WINDOW_SECONDS, seconds);
        ContextCompat.startForegroundService(requireContext(), i);
    }

    private void armWatchdog(int seconds) {
        cancelWatchdog();
        windowWatchdog = () -> {
            if (liveActivity != null && !liveActivity.isEmpty()) lastActivityLabel = liveActivity;
            speedReady = true;
            tryProceedIfAllReady();
        };
        handler.postDelayed(windowWatchdog, (seconds + 2) * 1000L);
    }

    private void tryProceedIfAllReady() {
        if (!speedReady || !labelsReady) return;

        final String timeOfDay = computeTimeOfDay();
        final String act = (lastActivityLabel == null || lastActivityLabel.isEmpty()) ? "still" : lastActivityLabel;
        resolveLocationThenProceed(timeOfDay, act);
    }

    private void cancelWatchdog() {
        if (windowWatchdog != null) {
            handler.removeCallbacks(windowWatchdog);
            windowWatchdog = null;
        }
    }

    private void onGenerationStart() {
        showLoading(true);
        if (getActivity() instanceof MainActivity) {
            MainActivity a = (MainActivity) getActivity();
            a.setBottomNavInteractionEnabled(false);
            a.setBottomNavVisibility(false);
        }
    }

    private void onGenerationEnd() {
        if (getActivity() instanceof MainActivity) {
            MainActivity a = (MainActivity) getActivity();
            a.setBottomNavVisibility(true);
            a.setBottomNavInteractionEnabled(true);
        }
        showLoading(false);
    }

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

            if (isFinal) {
                cancelWatchdog();
                lastActivityLabel = (liveActivity == null ? "still" : liveActivity);
                speedReady = true;
                tryProceedIfAllReady();
            }
        }
    };

    private void resolveLocationThenProceed(String timeOfDay, String activity) {
        if (settingsManager.isLocationEnabled() && PermissionManager.hasAnyLocation(requireContext())) {
            locationSensor.getCurrentLocation(loc -> {
                if (loc == null) {
                    buildContextAndProceed(timeOfDay, activity, null, null);
                } else {
                    locationHelper.getLocationContext(loc, (placeTag, nearby) ->
                            buildContextAndProceed(timeOfDay, activity, placeTag, nearby));
                }
            });
        } else {
            buildContextAndProceed(timeOfDay, activity, null, null);
        }
    }

    private void buildContextAndProceed(String timeOfDay, String activity,
                                        @Nullable String placeTag, @Nullable List<String> nearbyTypes) {
        List<String> img = topImageLabelStrings(3);

        Context ctx = new Context(
                currentLightBucket, timeOfDay, activity, currentWeather,
                placeTag, (nearbyTypes == null ? new ArrayList<>() : nearbyTypes),
                img
        );
        proceedWithContext(ctx);
    }

    private void proceedWithContext(@NonNull Context ctx) {
        lastContext = ctx;

        boolean hasLight   = (ctx.lightLevel != null && ctx.lightLevel != LightBucket.UNKNOWN);
        boolean hasWeather = (ctx.weather != null && ctx.weather != WeatherState.UNKNOWN);
        boolean hasAct     = (ctx.activity != null && !ctx.activity.trim().isEmpty());
        boolean hasPlace   = (ctx.placeTag != null) || (ctx.nearbyPlaceTypes != null && !ctx.nearbyPlaceTypes.isEmpty());
        boolean hasImage   = (ctx.imageLabels != null && !ctx.imageLabels.isEmpty());

        if (!hasLight && !hasWeather && !hasAct && !hasPlace && !hasImage) {
            requireActivity().runOnUiThread(() -> {
                onGenerationEnd();
                binding.loadingContainer.setVisibility(View.GONE);
                binding.welcomeCard.setVisibility(View.VISIBLE);
                binding.createVibeCard.setVisibility(View.VISIBLE);
                binding.btnGo.setEnabled(true);
                binding.btnRegenerate.setEnabled(true);
                Toast.makeText(requireContext(),
                        "Not enough context. Enable sensors or add a photo.", Toast.LENGTH_LONG).show();
            });
            return;
        }

        aiRecommender.getSearchRecommendation(ctx, new AIPlaylistRecommender.AICallback() {
            @Override public void onSuccess(@NonNull SearchRecommendation rec) {
                String query = (rec.searchQuery == null || rec.searchQuery.isEmpty())
                        ? fallbackQuery(ctx)
                        : rec.searchQuery;
                runSpotify(query);
            }
            @Override public void onError(@NonNull Exception e) {
                Log.e(TAG, "AI failed, fallback", e);
                runSpotify(fallbackQuery(ctx));
            }
        });
    }

    private List<String> topImageLabelStrings(int n) {
        List<String> out = new ArrayList<>();
        if (currentImageLabels != null && currentImageLabels.getItems() != null) {
            for (ImageLabels.LabelConfidence lc : currentImageLabels.getItems()) {
                if (lc.getLabel() != null && !lc.getLabel().isEmpty()) {
                    out.add(lc.getLabel());
                    if (out.size() >= n) break;
                }
            }
        }
        return out;
    }

    private void showLoading(boolean on) {
        if (on) {
            binding.welcomeCard.setVisibility(View.GONE);
            binding.createVibeCard.setVisibility(View.GONE);
            binding.currentMoodCard.setVisibility(View.GONE);
            binding.regenerateCard.setVisibility(View.GONE);
            binding.playlistSuggestionsSection.setVisibility(View.GONE);
            binding.btnGo.setEnabled(false);
            binding.btnRegenerate.setEnabled(false);
            binding.loadingContainer.setVisibility(View.VISIBLE);
        } else {
            binding.loadingContainer.setVisibility(View.GONE);
            binding.btnGo.setEnabled(true);
            binding.btnRegenerate.setEnabled(true);
        }
    }

    private void runSpotify(@NonNull String query) {
        spotifyAPI.searchPlaylists(query, 5, new SpotifyAPI.PlaylistCallback() {
            @Override
            public void onSuccess(List<SpotifyPlaylist> playlists) {
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    // Update ViewModel instead of local state
                    homeStateVM.setPlaylists(playlists);
                    homeStateVM.setRecommendationsGenerated(true);

                    binding.loadingContainer.setVisibility(View.GONE);
                    binding.btnGo.setEnabled(true);
                    binding.btnRegenerate.setEnabled(true);

                    // UI updates now handled by observers
                    populateContextChipsFor(lastContext != null ? lastContext : ctxFromLastKnown());
                    binding.playlistEmptyText.setVisibility(playlists.isEmpty() ? View.VISIBLE : View.GONE);
                    onGenerationEnd();
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    Log.e(TAG, "Spotify error: " + error);
                    binding.loadingContainer.setVisibility(View.GONE);
                    binding.btnGo.setEnabled(true);
                    binding.btnRegenerate.setEnabled(true);

                    Boolean generated = homeStateVM.getRecommendationsGenerated().getValue();
                    if (generated != null && generated) {
                        binding.currentMoodCard.setVisibility(View.VISIBLE);
                        binding.regenerateCard.setVisibility(View.VISIBLE);
                        binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);
                        binding.playlistCardsContainer.removeAllViews();
                        binding.playlistEmptyText.setText(getString(R.string.no_playlists_found));
                        binding.playlistEmptyText.setVisibility(View.VISIBLE);
                        onGenerationEnd();
                    } else {
                        binding.welcomeCard.setVisibility(View.VISIBLE);
                        binding.createVibeCard.setVisibility(View.VISIBLE);
                        onGenerationEnd();
                        Toast.makeText(requireContext(), "Error fetching playlists. Try again.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private String fallbackQuery(@NonNull Context ctx) {
        String tod = ctx.timeOfDay == null ? "" : ctx.timeOfDay.toLowerCase();
        String wx  = ctx.weather == null ? "" : ctx.weather.name().toLowerCase();
        String place = ctx.placeTag == null ? "" : ctx.placeTag.toLowerCase();
        String act = ctx.activity == null ? "still" : ctx.activity.toLowerCase();

        if ("gym".equals(place) || "running".equals(act)) return "gym 140 bpm";
        if ("library".equals(place) || "office".equals(place)) return "instrumental focus";
        if ("home".equals(place) && "evening".equals(tod)) return "evening chill acoustic";
        if ("morning".equals(tod)) return "upbeat indie morning";
        if ("rainy".equals(wx)) return "rainy day lo-fi";
        return "chill pop";
    }

    private Context ctxFromLastKnown() {
        String tod = computeTimeOfDay();
        String act = (liveActivity != null && !liveActivity.isEmpty()) ? liveActivity : lastActivityLabel;
        return new Context(currentLightBucket, tod, act, currentWeather, null, new ArrayList<>(), topImageLabelStrings(2));
    }

    // ===================== CONTEXT CHIPS =====================

    private void populateContextChipsFor(@NonNull Context ctx) {
        binding.contextTagsGroup.removeAllViews();

        if (ctx.activity != null && !ctx.activity.isEmpty()) {
            addContextChip(cap(ctx.activity));
        }

        if (ctx.lightLevel != null && ctx.lightLevel != LightBucket.UNKNOWN) {
            String lightText;
            switch (ctx.lightLevel) {
                case DIM:    lightText = "Dim";    break;
                case NORMAL: lightText = "Normal"; break;
                case BRIGHT: lightText = "Bright"; break;
                default:     lightText = "Unknown";
            }
            addContextChip(lightText);
        }

        if (ctx.weather != null && ctx.weather != WeatherState.UNKNOWN) {
            String w;
            switch (ctx.weather) {
                case SUNNY:  w = "Sunny"; break;
                case CLOUDY: w = "Cloudy"; break;
                case RAINY:  w = "Rainy";  break;
                default:     w = null;
            }
            if (w != null) addContextChip(w);
        }

        if (ctx.timeOfDay != null && !ctx.timeOfDay.isEmpty()) {
            addContextChip(cap(ctx.timeOfDay));
        }

        if (ctx.placeTag != null) {
            addContextChip(ctx.placeTag);
        } else if (ctx.nearbyPlaceTypes != null && !ctx.nearbyPlaceTypes.isEmpty()) {
            int n = Math.min(2, ctx.nearbyPlaceTypes.size());
            for (int i = 0; i < n; i++) {
                addContextChip(ctx.nearbyPlaceTypes.get(i));
            }
        }

        if (ctx.imageLabels != null && !ctx.imageLabels.isEmpty()) {
            int n = Math.min(2, ctx.imageLabels.size());
            for (int i = 0; i < n; i++) addContextChip(ctx.imageLabels.get(i));
        }
    }

    private void addContextChip(String text) {
        com.google.android.material.chip.Chip chip =
                new com.google.android.material.chip.Chip(requireContext());
        chip.setText(text);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2D3748")));
        chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        chip.setClickable(false);
        chip.setChipCornerRadius(24f);
        binding.contextTagsGroup.addView(chip);
    }

    private String cap(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0,1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    private String computeTimeOfDay() {
        try {
            LocalTime now = LocalTime.now();
            int h = now.getHour();
            if (h >= 5 && h < 12)  return "morning";
            if (h >= 12 && h < 17) return "afternoon";
            if (h >= 17 && h < 22) return "evening";
            return "night";
        } catch (Throwable t) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            int h = c.get(java.util.Calendar.HOUR_OF_DAY);
            if (h >= 5 && h < 12)  return "morning";
            if (h >= 12 && h < 17) return "afternoon";
            if (h >= 17 && h < 22) return "evening";
            return "night";
        }
    }

    // ===================== SPOTIFY RENDER =====================

    private void populateSpotifyPlaylistCards(List<SpotifyPlaylist> playlists) {
        Log.d(TAG, "Populating " + playlists.size() + " playlist cards");
        binding.playlistCardsContainer.removeAllViews();

        SavedPlaylistsManager saved = new SavedPlaylistsManager(requireContext());

        for (SpotifyPlaylist playlist : playlists) {
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

    private void updateSaveButtonIcon(com.google.android.material.button.MaterialButton btnSave, boolean isSaved) {
        if (isSaved) {
            btnSave.setIconResource(R.drawable.ic_saved);
        } else {
            btnSave.setIconResource(R.drawable.ic_unsaved);
        }
    }

    // ===================== PERMISSIONS =====================
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

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                     @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionManager.REQ_LOCATION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) fetchWeatherData();
            else updateWeatherStatus(WeatherService.WeatherState.UNKNOWN);
        }
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
        } catch (Exception ignore) {}
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        try {
            if (getActivity() instanceof MainActivity) {
                MainActivity a = (MainActivity) getActivity();
                a.setBottomNavVisibility(true);
                a.setBottomNavInteractionEnabled(true);
            }
        } catch (Exception ignored) {}
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
