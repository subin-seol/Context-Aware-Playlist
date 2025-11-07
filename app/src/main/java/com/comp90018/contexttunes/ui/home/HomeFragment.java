package com.comp90018.contexttunes.ui.home;

import android.content.pm.PackageManager;
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
import android.graphics.Bitmap;

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
import com.comp90018.contexttunes.domain.AIPlaylistRecommender;
import com.comp90018.contexttunes.data.sensors.LightSensor;
import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;
import com.comp90018.contexttunes.data.sensors.LocationSensor;
import com.comp90018.contexttunes.data.weather.WeatherService;
import com.comp90018.contexttunes.data.weather.WeatherService.WeatherState;
import com.comp90018.contexttunes.databinding.FragmentHomeBinding;
import com.comp90018.contexttunes.domain.Context;
import com.comp90018.contexttunes.domain.SearchRecommendation;
import com.comp90018.contexttunes.data.viewModel.ImageViewModel;
import com.comp90018.contexttunes.data.api.SpotifyAPI;
import com.comp90018.contexttunes.domain.SpotifyPlaylist;
import com.comp90018.contexttunes.utils.PermissionManager;
import com.comp90018.contexttunes.utils.PlaylistOpener;
import com.comp90018.contexttunes.utils.SavedPlaylistsManager;
import com.comp90018.contexttunes.utils.SettingsManager;
import com.comp90018.contexttunes.domain.ImageLabels;
import com.comp90018.contexttunes.services.SpeedSensorService;
import com.comp90018.contexttunes.utils.AppEvents;
import com.comp90018.contexttunes.utils.LocationContextHelper;
import com.comp90018.contexttunes.data.viewModel.HomeStateViewModel;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalTime;
import java.util.Locale;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final int DEFAULT_WINDOW_SECONDS = 20;
    private static final int SPOTIFY_LIMIT = 5;
    private static final long WEATHER_MAX_AGE_MS = 45 * 60 * 1000L; // 45 min

    private boolean isCurrentlyLoading = false;

    private FragmentHomeBinding binding;
    private SettingsManager settingsManager;
    private LightSensor lightSensor;
    private LocationSensor locationSensor;
    private LocationContextHelper locationHelper;
    private AIPlaylistRecommender aiRecommender;
    private SpotifyAPI spotifyAPI;
    private WeatherService weatherService;

    private WeatherState currentWeather = WeatherState.UNKNOWN;
    private LightBucket currentLightBucket = LightBucket.UNKNOWN;

    // timer to guarantee window end even if service isn't running
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    // ---- window coordination (speed + labels) ----
    private volatile boolean speedReady  = false;
    private volatile boolean labelsReady = false;
    private static final int LABELS_TIMEOUT_MS = 8000; // hard stop for Rekognition
    @Nullable private Runnable windowWatchdog = null;


    // live values from SpeedSensorService (if running)
    @Nullable private Float  liveSpeedKmh   = null;
    @Nullable private Float  liveCadenceSpm = null;
    @Nullable private String liveActivity   = null;
    private String lastActivityLabel = "still";
    private LightBucket lastShownLight = LightBucket.UNKNOWN;

    // camera labels
    @Nullable private ImageLabels currentImageLabels = null;
    private ImageViewModel imageVM;

    // state
    private List<SpotifyPlaylist> spotifyPlaylists = new ArrayList<>();
    private boolean playlistsGenerated = false;
    // The exact context used for AI/Spotify (for chips)
    @Nullable private Context lastContext = null;

    private HomeStateViewModel homeStateVM;
    private String currentLocationText = "—";
    private String currentSpeedText = "—";


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

        // Shared VM with SnapFragment: observe image + labels
        imageVM = new ViewModelProvider(requireActivity()).get(ImageViewModel.class);

        // VM to hold Home state across navs
        homeStateVM = new ViewModelProvider(requireActivity()).get(HomeStateViewModel.class);
        homeStateVM.getWeatherState().observe(getViewLifecycleOwner(), ws -> {
            if (ws != null) {
                currentWeather = ws;
                updateWeatherStatus(ws);
            }
        });

        homeStateVM.getLastContext().observe(getViewLifecycleOwner(), ctx -> {
            if (ctx != null) {
                lastContext = ctx; // keep local mirror for quick access
                if (playlistsGenerated) {
                    populateContextChipsFor(ctx);
                }
            }
        });

        imageVM.getCapturedImage().observe(getViewLifecycleOwner(), bitmap -> {
            binding.btnSnap.setVisibility(bitmap != null ? View.GONE : View.VISIBLE);
            binding.btnPreviewImage.setVisibility(bitmap != null ? View.VISIBLE : View.GONE);
        });

        imageVM.getImageLabels().observe(getViewLifecycleOwner(), labels -> {
            currentImageLabels = labels; // keep latest labels for context & chips
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
                if (bucket == lastShownLight) return;          // gate duplicates
                lastShownLight = bucket;
                currentLightBucket = bucket;
                if (binding == null) return;
                requireActivity().runOnUiThread(() -> updateLightStatus(bucket));
            });
        }

        // SNAP nav
        binding.btnSnap.setOnClickListener(v -> ((MainActivity) requireActivity()).openSnap());
        binding.btnPreviewImage.setOnClickListener(v -> ((MainActivity) requireActivity()).openSnap());

        // GO / Regenerate → run the 20s window + context + AI + Spotify
        binding.btnGo.setOnClickListener(v -> beginWindow(DEFAULT_WINDOW_SECONDS));
        binding.btnRegenerate.setOnClickListener(v -> beginWindow(DEFAULT_WINDOW_SECONDS));

        // restore previously generated playlists/state when coming back from other tabs
        homeStateVM.getPlaylists().observe(getViewLifecycleOwner(), pl -> {
            if (pl == null) return;
            spotifyPlaylists = pl;
            if (!pl.isEmpty()) {
                // Render playlists if the generated state is active
                populateSpotifyPlaylistCards();
            }
        });
        homeStateVM.getRecommendationsGenerated().observe(getViewLifecycleOwner(), isGen -> {
            playlistsGenerated = Boolean.TRUE.equals(isGen);
            applyUIStateForGeneration(playlistsGenerated);
        });

        // If we already had state (process restore without LiveData tick), apply it once
        List<SpotifyPlaylist> existing = homeStateVM.getPlaylists().getValue();
        Boolean wasGen = homeStateVM.getRecommendationsGenerated().getValue();

        // pull lastContext from VM
        Context existingCtx = homeStateVM.getLastContext().getValue();
        if (existingCtx != null) {
            lastContext = existingCtx; // mirror into fragment
        }

        if (existing != null && !existing.isEmpty()) {
            spotifyPlaylists = existing;
            populateSpotifyPlaylistCards();
        }
        if (wasGen != null) {
            playlistsGenerated = wasGen;
            applyUIStateForGeneration(playlistsGenerated);
            if (playlistsGenerated) {
                // ensure chips render on first load after returning
                populateContextChipsFor(getEffectiveContext());
            }
        }
    }

    private void applyUIStateForGeneration(boolean generated) {
        if (binding == null) return;
        if (isCurrentlyLoading) return; // dont apply UI changes if currently loading
        if (generated) {
            binding.welcomeCard.setVisibility(View.GONE);
            binding.createVibeCard.setVisibility(View.GONE);
            binding.contextCardsContainer.setVisibility(View.GONE);
            binding.weatherStatus.setVisibility(View.GONE);
            binding.lightValue.setVisibility(View.GONE);
            binding.locationValue.setVisibility(View.GONE);
            binding.speedValue.setVisibility(View.GONE);


            binding.currentMoodCard.setVisibility(View.VISIBLE);
            binding.regenerateCard.setVisibility(View.VISIBLE);
            binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);

            populateContextChipsFor(getEffectiveContext());
            binding.playlistEmptyText.setVisibility(spotifyPlaylists.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            binding.currentMoodCard.setVisibility(View.GONE);
            binding.regenerateCard.setVisibility(View.GONE);
            binding.playlistSuggestionsSection.setVisibility(View.GONE);

            binding.welcomeCard.setVisibility(View.VISIBLE);
            binding.createVibeCard.setVisibility(View.VISIBLE);
            binding.contextCardsContainer.setVisibility(View.VISIBLE);
            binding.weatherStatus.setVisibility(View.VISIBLE);
            binding.lightValue.setVisibility(View.VISIBLE);
            binding.locationValue.setVisibility(View.VISIBLE);
            binding.speedValue.setVisibility(View.VISIBLE);
        }
    }

    private Context getEffectiveContext() {
        Context vmCtx = homeStateVM.getLastContext().getValue();
        if (lastContext != null) return lastContext;
        if (vmCtx != null) return vmCtx;
        return ctxFromLastKnown();
    }

    // ===================== PERMISSION-GATED ACTIONS =====================

    /** Weather: request permission if needed, then fetch. */
    private void ensureLocationAndFetchWeather() {
        ensureLocationAndFetchWeather(false);   // default: toast (used on GO)
    }

    private void ensureLocationAndFetchWeather(boolean showToast) {
        if (PermissionManager.hasAnyLocation(requireContext())) {
            fetchWeatherData(showToast);
        } else {
            PermissionManager.requestLocationFineAndCoarse(this);
        }
    }


    // ===================== WEATHER + LIGHT UI =====================

    private void fetchWeatherData(boolean showToast) {
        if (showToast) {
            Toast.makeText(requireContext(), "Fetching weather data...", Toast.LENGTH_SHORT).show();
        }
        weatherService.getCurrentWeather(ws -> {
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                currentWeather = ws;
                homeStateVM.setWeatherState(ws); // persist in VM
                homeStateVM.setWeatherFetchedAt(System.currentTimeMillis()); // track freshness
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
        // pre-UI → loading
        onGenerationStart();

        if (settingsManager.isLocationEnabled()) {
            ensureLocationAndFetchWeather();   // this will show the toast exactly on GO
        }

        // if user enabled “Accelerometer” in Settings, that’s our “speed pipeline” master toggle
        boolean speedEnabled = settingsManager.isAccelerometerEnabled();
        boolean hasAR = PermissionManager.hasActivityRecognition(requireContext());
        boolean hasAnyLoc = PermissionManager.hasAnyLocation(requireContext());

        if (speedEnabled && hasAR && hasAnyLoc) {
            startSpeedWindow(seconds); // service will emit FINAL on time
        } else if (speedEnabled && (!hasAR || !hasAnyLoc)) {
            // ask once at point-of-use; user can grant and tap GO again
            PermissionManager.requestSpeedSensing(this);
        }

        // reset join state every run
        speedReady = !settingsManager.isAccelerometerEnabled(); // if speed disabled, treat as ready
        labelsReady = true; // default true; we’ll set false only if we actually need to run analysis

        maybeStartImageLabelsWork();

        // Regardless, we arm the same-duration watchdog → single source of truth UX
        armWatchdog(seconds);
    }

    private void maybeStartImageLabelsWork() {
        Bitmap img = imageVM.getCapturedImage().getValue();
        ImageLabels labels = imageVM.getImageLabels().getValue();

        // If no image, nothing to do
        if (img == null) {
            labelsReady = true;
            return;
        }

        // If labels already exist for the CURRENT image (cache hit), done.
        if (labels != null && labels.matchesCurrentImage(img)) {
            labelsReady = true;
            return;
        }

        // We need to (re)generate labels.
        labelsReady = false;

        // Run analyser (AWS Rekognition) with a timeout guard.
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable timeout = () -> {
            if (!labelsReady) {
                Log.w(TAG, "Labels timeout — proceeding without image labels.");
                labelsReady = true;
                tryProceedIfAllReady();
            }
        };
        h.postDelayed(timeout, LABELS_TIMEOUT_MS);

        // Observe a single update, then remove observer
        imageVM.getImageLabels().observe(getViewLifecycleOwner(), newLabels -> {
            if (newLabels != null && newLabels.matchesCurrentImage(img)) {
                labelsReady = true;
                h.removeCallbacks(timeout);
                imageVM.getImageLabels().removeObservers(getViewLifecycleOwner());
                tryProceedIfAllReady();
            }
        });

        // Fire analysis (non-blocking). (Uses the same instance as Snap’s button)
        new com.comp90018.contexttunes.services.ImageAnalyser(requireContext())
                .analyzeImage(imageVM);   // This posts into imageVM on completion
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
            // service may not have sent FINAL yet; we use last known
            if (liveActivity != null && !liveActivity.isEmpty()) lastActivityLabel = liveActivity;
            speedReady = true; // stop waiting on speed
            tryProceedIfAllReady();
        };
        handler.postDelayed(windowWatchdog, (seconds + 2) * 1000L); // +2s slack
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
        isCurrentlyLoading = true;
        showLoading(true);
        if (getActivity() instanceof MainActivity) {
            MainActivity a = (MainActivity) getActivity();
            a.setBottomNavInteractionEnabled(false);
            a.setBottomNavVisibility(false);
        }

    }

    private void onGenerationEnd() {
        isCurrentlyLoading = false;
        if (getActivity() instanceof MainActivity) {
            MainActivity a = (MainActivity) getActivity();
            a.setBottomNavVisibility(true);
            a.setBottomNavInteractionEnabled(true);
        }
        showLoading(false);
    }

    // receive frames from SpeedSensorService
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

            // UPDATE: Also update the speed card in real-time
            if (binding != null) {
                requireActivity().runOnUiThread(() ->
                        updateSpeedCard(liveActivity, liveSpeedKmh)
                );
            }

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
                    updateLocationCard(null, null);
                    buildContextAndProceed(timeOfDay, activity, null, null);
                } else {
                    locationHelper.getLocationContext(loc, (placeTag, nearby) -> {
                        updateLocationCard(placeTag, nearby);
                        buildContextAndProceed(timeOfDay, activity, placeTag, nearby);
                    });
                }
            });
        } else {
            updateLocationCard(null, null);
            buildContextAndProceed(timeOfDay, activity, null, null);
        }
    }

    private void buildContextAndProceed(String timeOfDay, String activity,
                                        @Nullable String placeTag, @Nullable List<String> nearbyTypes) {
        // Extract top-N image labels (strings only) from VM
        List<String> img = topImageLabelStrings(3);

        Context ctx = new Context(
                currentLightBucket, timeOfDay, activity, currentWeather,
                placeTag, (nearbyTypes == null ? new ArrayList<>() : nearbyTypes),
                img
        );
        proceedWithContext(ctx);
    }

    private void proceedWithContext(@NonNull Context ctx) {
        lastContext = ctx; // store the exact context for chips rendering
        homeStateVM.setLastContext(ctx); // persist in VM

        // 1) Decide if we have ANY context at all
        boolean hasLight   = (ctx.lightLevel != null && ctx.lightLevel != LightBucket.UNKNOWN);
        boolean hasWeather = (ctx.weather != null && ctx.weather != WeatherState.UNKNOWN);
        boolean hasAct     = (ctx.activity != null && !ctx.activity.trim().isEmpty());
        boolean hasPlace   = (ctx.placeTag != null) || (ctx.nearbyPlaceTypes != null && !ctx.nearbyPlaceTypes.isEmpty());
        boolean hasImage   = (ctx.imageLabels != null && !ctx.imageLabels.isEmpty());

        if (!hasLight && !hasWeather && !hasAct && !hasPlace && !hasImage) {
            // Absolutely no context → inform and restore UI
            requireActivity().runOnUiThread(() -> {
                onGenerationEnd();
                binding.loadingContainer.setVisibility(View.GONE);
                binding.welcomeCard.setVisibility(View.VISIBLE);
                binding.createVibeCard.setVisibility(View.VISIBLE);
                binding.btnGo.setEnabled(true);
                binding.btnRegenerate.setEnabled(true);
                Toast.makeText(requireContext(),
                        "Not enough context. Enable sensors or add a photo.", Toast.LENGTH_LONG).show();
                homeStateVM.setRecommendationsGenerated(false);
            });
            return;
        }

        if (!settingsManager.isAIMode()) {
            runSpotify(fallbackQuery(ctx));   // skip AI, deterministic fallback
            return;
        }

        // 2) Proceed → AI stub (Context → search_query + reason) → Spotify
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
            binding.contextCardsContainer.setVisibility(View.GONE);
            binding.createVibeCard.setVisibility(View.GONE);
            binding.currentMoodCard.setVisibility(View.GONE);
            binding.regenerateCard.setVisibility(View.GONE);
            binding.playlistSuggestionsSection.setVisibility(View.GONE);
            binding.btnGo.setEnabled(false);
            binding.btnRegenerate.setEnabled(false);
            binding.loadingContainer.setVisibility(View.VISIBLE);
        } else {
            binding.contextCardsContainer.setVisibility(View.VISIBLE);
            binding.loadingContainer.setVisibility(View.GONE);
            binding.btnGo.setEnabled(true);
            binding.btnRegenerate.setEnabled(true);
        }
    }

    private void runSpotify(@NonNull String query) {
        spotifyAPI.searchPlaylists(query, SPOTIFY_LIMIT, new SpotifyAPI.PlaylistCallback() {
            @Override
            public void onSuccess(List<SpotifyPlaylist> playlists) {
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    spotifyPlaylists = playlists;
                    playlistsGenerated = true;

                    // End generation state FIRST before updating UI
                    onGenerationEnd();

                    // persist data + generated flag
                    homeStateVM.setPlaylists(playlists);
                    homeStateVM.setRecommendationsGenerated(true);

                    binding.loadingContainer.setVisibility(View.GONE);
                    binding.btnGo.setEnabled(true);
                    binding.btnRegenerate.setEnabled(true);

                    // Explicitly hide "before" state
                    binding.welcomeCard.setVisibility(View.GONE);
                    binding.createVibeCard.setVisibility(View.GONE);
                    binding.contextCardsContainer.setVisibility(View.GONE);
                    binding.weatherStatus.setVisibility(View.GONE);
                    binding.lightValue.setVisibility(View.GONE);
                    binding.locationValue.setVisibility(View.GONE);
                    binding.speedValue.setVisibility(View.GONE);

                    // Show “after” state
                    binding.currentMoodCard.setVisibility(View.VISIBLE);
                    binding.regenerateCard.setVisibility(View.VISIBLE);
                    binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);

                    populateContextChipsFor(lastContext != null ? lastContext : ctxFromLastKnown()); // render chips from last known
                    populateSpotifyPlaylistCards();
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

                    if (playlistsGenerated) {
                        binding.currentMoodCard.setVisibility(View.VISIBLE);
                        binding.regenerateCard.setVisibility(View.VISIBLE);
                        binding.playlistSuggestionsSection.setVisibility(View.VISIBLE);
                        binding.playlistCardsContainer.removeAllViews();
                        binding.playlistEmptyText.setText(getString(R.string.no_playlists_found));
                        binding.playlistEmptyText.setVisibility(View.VISIBLE);
                        binding.contextCardsContainer.setVisibility(View.GONE);
                        binding.weatherStatus.setVisibility(View.GONE);
                        binding.lightValue.setVisibility(View.GONE);
                        binding.locationValue.setVisibility(View.GONE);
                        binding.speedValue.setVisibility(View.GONE);
                        onGenerationEnd();
                    } else {
                        binding.welcomeCard.setVisibility(View.VISIBLE);
                        binding.createVibeCard.setVisibility(View.VISIBLE);
                        binding.contextCardsContainer.setVisibility(View.VISIBLE);
                        binding.weatherStatus.setVisibility(View.VISIBLE);
                        binding.lightValue.setVisibility(View.VISIBLE);
                        binding.locationValue.setVisibility(View.VISIBLE);
                        binding.speedValue.setVisibility(View.VISIBLE);
                        onGenerationEnd();
                        Toast.makeText(requireContext(), "Error fetching playlists. Try again.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // Fallback query if AI fails or returns empty
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

    // helper to keep chip rendering simple without changing binding points too much
    private Context ctxFromLastKnown() {
        String tod = computeTimeOfDay();
        String act = (liveActivity != null && !liveActivity.isEmpty()) ? liveActivity : lastActivityLabel;
        return new Context(currentLightBucket, tod, act, currentWeather, null, new ArrayList<>(), topImageLabelStrings(2));
    }

    // ===================== CONTEXT CHIPS =====================

    private void populateContextChipsFor(@NonNull Context ctx) {
        binding.contextTagsGroup.removeAllViews();

        // activity
        if (ctx.activity != null && !ctx.activity.isEmpty()) {
            addContextChip(cap(ctx.activity));
        }

        // light
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

        // weather
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

        // time
        if (ctx.timeOfDay != null && !ctx.timeOfDay.isEmpty()) {
            addContextChip(cap(ctx.timeOfDay));
        }

        // place tag or a couple of place types
        if (ctx.placeTag != null) {
            addContextChip(ctx.placeTag);
        } else if (ctx.nearbyPlaceTypes != null && !ctx.nearbyPlaceTypes.isEmpty()) {
            int n = Math.min(2, ctx.nearbyPlaceTypes.size());
            for (int i = 0; i < n; i++) {
                addContextChip(ctx.nearbyPlaceTypes.get(i));
            }
        }

        // camera labels (top-2)
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
            // Android < 26 without desugaring: fall back conservatively
            java.util.Calendar c = java.util.Calendar.getInstance();
            int h = c.get(java.util.Calendar.HOUR_OF_DAY);
            if (h >= 5 && h < 12)  return "morning";
            if (h >= 12 && h < 17) return "afternoon";
            if (h >= 17 && h < 22) return "evening";
            return "night";
        }
    }

    // ===================== SPOTIFY RENDER =====================

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

    private void updateSaveButtonIcon(com.google.android.material.button.MaterialButton btnSave, boolean isSaved) {
        if (isSaved) {
            btnSave.setIconResource(com.comp90018.contexttunes.R.drawable.ic_saved);
        } else {
            btnSave.setIconResource(com.comp90018.contexttunes.R.drawable.ic_unsaved);
        }
    }
// Update location card
    private void updateLocationCard(@Nullable String placeTag, @Nullable List<String> nearbyTypes) {
        if (binding == null) return;

        String locationText;
        if (placeTag != null && !placeTag.isEmpty()) {
            // Capitalize first letter
            locationText = placeTag.substring(0, 1).toUpperCase(Locale.getDefault())
                    + placeTag.substring(1);
        } else if (nearbyTypes != null && !nearbyTypes.isEmpty()) {
            // Show first nearby type, capitalize
            String first = nearbyTypes.get(0);
            locationText = first.substring(0, 1).toUpperCase(Locale.getDefault())
                    + first.substring(1);
        } else {
            locationText = "—";
        }

        currentLocationText = locationText;
        binding.locationValue.setText(locationText);
    }

    // Update speed card
    private void updateSpeedCard(@Nullable String activity, @Nullable Float speedKmh) {
        if (binding == null) return;

        String speedText;
        if (activity != null && !activity.isEmpty() && !activity.equals("still")) {
            // Show activity with capitalization
            speedText = activity.substring(0, 1).toUpperCase(Locale.getDefault())
                    + activity.substring(1);

            // Optionally add speed if available
            if (speedKmh != null && speedKmh > 0.5f) {
                speedText += String.format(Locale.getDefault(), " (%.1f km/h)", speedKmh);
            }
        } else if (speedKmh != null && speedKmh > 0.5f) {
            speedText = String.format(Locale.getDefault(), "%.1f km/h", speedKmh);
        } else {
            speedText = "Still";
        }

        currentSpeedText = speedText;
        binding.speedValue.setText(speedText);
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

    // permissions callback (weather only)
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                     @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionManager.REQ_LOCATION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) fetchWeatherData(false);   // CHANGE: silent
            else updateWeatherStatus(WeatherService.WeatherState.UNKNOWN);
        }
        // Speed perms (REQ_LOCATION_MULTI / REQ_ACTIVITY) are requested,
        // but we *don’t* immediately start sensing here; user taps GO again.
    }

    // ===================== LIFECYCLE =====================

    @Override public void onResume() {
        super.onResume();

        if (getActivity() instanceof MainActivity) {
            MainActivity a = (MainActivity) getActivity();
            a.setBottomNavVisibility(true);
            a.setBottomNavInteractionEnabled(true);
        }
        if (settingsManager.isLocationEnabled()) {
            Long last = homeStateVM.getWeatherFetchedAt().getValue();
            long now = System.currentTimeMillis();
            boolean stale = (currentWeather == WeatherState.UNKNOWN)
                    || last == null
                    || (now - last > WEATHER_MAX_AGE_MS);
            if (stale) ensureLocationAndFetchWeather(false);
        }
        // Refresh speed card if we have live data
        if (liveActivity != null || liveSpeedKmh != null) {
            updateSpeedCard(liveActivity != null ? liveActivity : lastActivityLabel, liveSpeedKmh);
        }

        if (playlistsGenerated && binding != null) {
            populateContextChipsFor(getEffectiveContext());
        }
    }

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
        try {
            if (getActivity() instanceof MainActivity) {
                MainActivity a = (MainActivity) getActivity();
                a.setBottomNavVisibility(true);
                a.setBottomNavInteractionEnabled(true);
            }
        } catch (Exception ignored) {}
        if (weatherService != null) weatherService.shutdown();
        if (spotifyAPI != null) {
            spotifyAPI.shutdown();
        }
        binding = null;
        super.onDestroyView();
    }
}
