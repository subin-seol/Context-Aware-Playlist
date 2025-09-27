package com.comp90018.contexttunes.ui.home;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.comp90018.contexttunes.data.api.GooglePlacesAPI;
import com.comp90018.contexttunes.data.sensors.LightSensor;
import com.comp90018.contexttunes.data.sensors.LocationSensor;
import com.comp90018.contexttunes.data.weather.MockWeatherService;
import com.comp90018.contexttunes.data.weather.WeatherService.WeatherState;
import com.comp90018.contexttunes.databinding.FragmentHomeBinding;
import com.comp90018.contexttunes.domain.Recommendation;
import com.comp90018.contexttunes.domain.RuleEngine;
import com.comp90018.contexttunes.services.SpeedSensorService;
import com.comp90018.contexttunes.ui.viewModel.SharedCameraViewModel;
import com.comp90018.contexttunes.utils.AppEvents;
import com.comp90018.contexttunes.utils.PlaylistOpener;
import com.google.android.libraries.places.api.model.Place;

import java.util.List;
import java.util.Locale;

/**
 * HomeFragment
 *
 * - On first "GO", requests Activity Recognition + Location via AppEvents.Perms and runs a fixed
 *   5s measuring window in SpeedSensorService.
 * - While measuring: show "-" for activity/speed/cadence; at the end, show the last buffered values.
 * - Weather: fetched from MockWeatherService on view creation (single shot) and applied to UI
 *   and recommendation context.
 */
public class HomeFragment extends Fragment {

    private static final long MEASURE_WINDOW_MS = 5_000L;
    private static final long MEASURE_GRACE_MS  = 300L;

    private FragmentHomeBinding binding;

    // Team modules
    private LightSensor lightSensor;
    private LocationSensor locationSensor;
    @Nullable private GooglePlacesAPI googlePlacesAPI;
    private MockWeatherService mockWeatherService;

    private Recommendation currentRecommendation = null;
    private WeatherState currentWeather = WeatherState.UNKNOWN;
    private LightSensor.LightBucket currentLightBucket = LightSensor.LightBucket.UNKNOWN;

    // Measuring state for the GO flow
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean measuring = false;
    private long lastReadingAt = 0L;
    @Nullable private Runnable measuringTimeout;

    // Buffers (updated by broadcasts during the 5s window; applied once at the end)
    @Nullable private Float  pendingSpeedKmh   = null;
    @Nullable private Float  pendingCadenceSpm = null;
    @Nullable private String pendingActivity   = null;

    /** Receives frames from SpeedSensorService; while measuring, we only buffer. */
    private final BroadcastReceiver speedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || binding == null) return;
            if (!AppEvents.ACTION_SPEED_UPDATE.equals(intent.getAction())) return;
            if (!measuring) return;

            float kmh     = intent.getFloatExtra(AppEvents.EXTRA_SPEED_KMH, Float.NaN);
            float cadence = intent.getFloatExtra(AppEvents.EXTRA_CADENCE_SPM, Float.NaN);
            String act    = intent.getStringExtra(AppEvents.EXTRA_ACTIVITY);

            lastReadingAt = System.currentTimeMillis();
            pendingSpeedKmh   = Float.isNaN(kmh) ? null : kmh;
            pendingCadenceSpm = Float.isNaN(cadence) ? null : cadence;
            pendingActivity   = (act == null || act.isEmpty()) ? null : act;
        }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedCameraViewModel vm = new ViewModelProvider(requireActivity()).get(SharedCameraViewModel.class);

        // Initial placeholders
        binding.welcomeTitle.setText("Welcome back!");
        updateWeatherStatus(WeatherState.UNKNOWN);
        binding.activityValue.setText("-");
        binding.speedValue.setText("-");
        binding.cadenceValue.setText("-");

        // Light sensor → updates light UI + recommendation context
        lightSensor = new LightSensor(requireContext(), bucket -> {
            if (!isAdded() || binding == null) return;
            requireActivity().runOnUiThread(() -> {
                updateLightStatus(bucket);
                updateRecommendation(bucket);
            });
        });

        // Places key discovery (unchanged)
        locationSensor = new LocationSensor(requireActivity());
        String placesKey = null;
        try {
            int id = getResources().getIdentifier("places_api_key", "string", requireContext().getPackageName());
            if (id != 0) {
                String v = getString(id);
                if (v != null && !v.trim().isEmpty()) placesKey = v.trim();
            }
        } catch (Throwable ignore) {}
        if (placesKey == null || placesKey.isEmpty()) {
            try {
                String v = com.comp90018.contexttunes.BuildConfig.PLACES_API_KEY;
                if (v != null && !v.trim().isEmpty()) placesKey = v.trim();
            } catch (Throwable ignore) {}
        }
        if (placesKey != null && !placesKey.isEmpty()) {
            googlePlacesAPI = new GooglePlacesAPI(requireContext(), placesKey);
        } else {
            googlePlacesAPI = null;
            if (binding.btnFetchPlaces != null) binding.btnFetchPlaces.setVisibility(View.GONE);
        }

        // Weather: create mock service and fetch immediately
        mockWeatherService = new MockWeatherService(requireContext());
        fetchWeatherData(); // <<— this was missing; brings Weather back

        requestNotificationPermissionIfNeeded();

        // Camera buttons (unchanged)
        View.OnClickListener toSnap = v1 -> openSnapTabSafely();
        binding.btnSnap.setOnClickListener(toSnap);
        binding.btnPreviewImage.setOnClickListener(toSnap);
        vm.getCapturedImage().observe(getViewLifecycleOwner(), bmp -> {
            if (binding == null) return;
            binding.btnSnap.setVisibility(bmp != null ? View.GONE : View.VISIBLE);
            binding.btnPreviewImage.setVisibility(bmp != null ? View.VISIBLE : View.GONE);
        });

        binding.btnFetchPlaces.setOnClickListener(v12 -> {
            if (googlePlacesAPI == null) {
                Toast.makeText(requireContext(), "Places disabled (no API key).", Toast.LENGTH_SHORT).show();
                return;
            }
            locationSensor.getCurrentLocation(location -> {
                if (location != null) fetchNearbyPlaces(location);
                else Toast.makeText(requireContext(), "No location found", Toast.LENGTH_SHORT).show();
            });
        });

        // GO: ask perms via AppEvents.Perms, then start 5s measuring window
        binding.btnGo.setText("GO");
        binding.btnGo.setOnClickListener(v -> {
            AppEvents.Perms.ensureForSpeed(requireActivity(), this::startMeasuringBurst);
        });

        // Optional: keep service warm
        startSpeedService(false);
    }

    /** Single-shot weather fetch from MockWeatherService and apply to UI + recommendation. */
    private void fetchWeatherData() {
        if (mockWeatherService == null) return;
        mockWeatherService.getCurrentWeather(mockWeather -> {
            // Map mock enum → WeatherState (defensive, in case enums differ)
            WeatherState converted;
            switch (mockWeather) {
                case SUNNY:  converted = WeatherState.SUNNY;  break;
                case CLOUDY: converted = WeatherState.CLOUDY; break;
                case RAINY:  converted = WeatherState.RAINY;  break;
                case UNKNOWN:
                default:     converted = WeatherState.UNKNOWN; break;
            }
            currentWeather = converted;

            if (!isAdded() || binding == null) return;
            requireActivity().runOnUiThread(() -> {
                updateWeatherStatus(currentWeather);
                // Weather influences recommendation context
                updateRecommendation(null);
            });
        });
    }

    /** Forward permission result so the first GO can continue immediately after grant. */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (!AppEvents.Perms.handleResult(requireActivity(), requestCode, permissions, grantResults, this::startMeasuringBurst)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /** Start a fixed 5s measuring window; UI shows "-" until we finish. */
    private void startMeasuringBurst() {
        if (!isAdded() || binding == null) return;

        measuring = true;
        lastReadingAt = 0L;

        pendingSpeedKmh   = null;
        pendingCadenceSpm = null;
        pendingActivity   = null;

        binding.btnGo.setEnabled(false);
        binding.btnGo.setText("Measuring…");

        binding.activityValue.setText("-");
        binding.speedValue.setText("-");
        binding.cadenceValue.setText("-");

        startSpeedService(true);

        if (measuringTimeout != null) ui.removeCallbacks(measuringTimeout);
        measuringTimeout = () -> finishMeasurement(lastReadingAt != 0L);
        ui.postDelayed(measuringTimeout, MEASURE_WINDOW_MS + MEASURE_GRACE_MS);
    }

    /** Finish and apply the last buffered frame (or safe defaults if no frame). */
    private void finishMeasurement(boolean hadReading) {
        if (!isAdded() || binding == null) return;

        binding.btnGo.setEnabled(true);
        binding.btnGo.setText("GO");

        if (hadReading) {
            String act = (pendingActivity == null || pendingActivity.isEmpty()) ? "-" : pendingActivity;
            String v   = (pendingSpeedKmh == null)   ? "-" : String.format(Locale.getDefault(), "%.1f km/h", pendingSpeedKmh);
            String spm = (pendingCadenceSpm == null) ? "-" : String.format(Locale.getDefault(), "%.0f spm",  pendingCadenceSpm);
            binding.activityValue.setText(act);
            binding.speedValue.setText(v);
            binding.cadenceValue.setText(spm);
        } else {
            binding.activityValue.setText("still");
            binding.speedValue.setText("0.0 km/h");
            binding.cadenceValue.setText("0 spm");
            Toast.makeText(requireContext(), "No signal — using defaults", Toast.LENGTH_SHORT).show();
        }

        measuring = false;
        pendingSpeedKmh = null;
        pendingCadenceSpm = null;
        pendingActivity = null;

        if (measuringTimeout != null) {
            ui.removeCallbacks(measuringTimeout);
            measuringTimeout = null;
        }
    }

    /** Start/ping SpeedSensorService (service handles the 5s window on ACTION_SPEED_SAMPLE_NOW). */
    private void startSpeedService(boolean immediate) {
        try {
            Intent i = new Intent(requireContext(), SpeedSensorService.class);
            if (immediate) i.setAction(AppEvents.ACTION_SPEED_SAMPLE_NOW);
            requireContext().startService(i);
        } catch (Throwable t) {
            Toast.makeText(requireContext(), "Speed service unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- teammates' helpers (unchanged) ----------

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    private void updateRecommendation(@Nullable LightSensor.LightBucket lightBucket) {
        if (lightBucket != null) currentLightBucket = lightBucket;
        String timeOfDay = RuleEngine.getCurrentTimeOfDay();
        String activity = "still";
        com.comp90018.contexttunes.domain.Context ctx =
                new com.comp90018.contexttunes.domain.Context(currentLightBucket, timeOfDay, activity, currentWeather);
        Recommendation rec = RuleEngine.getRecommendation(ctx);
        binding.welcomeSubtitle.setText(rec.reason);
        currentRecommendation = rec;
    }

    private void updateWeatherStatus(WeatherState weather) {
        String weatherText;
        switch (weather) {
            case SUNNY:  weatherText = "Weather: ☀️ Sunny";  break;
            case CLOUDY: weatherText = "Weather: ☁️ Cloudy"; break;
            case RAINY:  weatherText = "Weather: 🌧️ Rainy";  break;
            case UNKNOWN:
            default:     weatherText = "Weather: —";         break;
        }
        binding.weatherStatus.setText(weatherText);
    }

    private void updateLightStatus(LightSensor.LightBucket bucket) {
        String lightText;
        switch (bucket) {
            case DIM:    lightText = "Light: 🌒 Dim";    break;
            case NORMAL: lightText = "Light: 🌗 Normal"; break;
            case BRIGHT: lightText = "Light: 🌕 Bright"; break;
            case UNKNOWN:
            default:     lightText = "Light: —";         break;
        }
        binding.lightValue.setText(lightText);
    }

    private void fetchNearbyPlaces(Location location) {
        if (googlePlacesAPI == null) return;
        googlePlacesAPI.getNearbyPlaces(location, 300, new GooglePlacesAPI.NearbyPlacesCallback() {
            @Override public void onPlacesFound(List<Place> places) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        places.isEmpty() ? "No nearby places found" : ("Found " + places.size() + " places"),
                        Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(Exception e) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Places API error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openSnapTabSafely() {
        try {
            java.lang.reflect.Method m = requireActivity().getClass().getMethod("goToSnapTab");
            m.invoke(requireActivity());
            return;
        } catch (Throwable ignore) {}
        try {
            int navViewId = getResources().getIdentifier("nav_view", "id", requireContext().getPackageName());
            int snapId    = getResources().getIdentifier("navigation_snap", "id", requireContext().getPackageName());
            if (navViewId != 0 && snapId != 0) {
                com.google.android.material.bottomnavigation.BottomNavigationView nav =
                        requireActivity().findViewById(navViewId);
                if (nav != null) nav.setSelectedItemId(snapId);
            }
        } catch (Throwable ignore) {}
    }

    // Receiver lifecycle
    @Override public void onStart() {
        super.onStart();
        if (lightSensor != null) lightSensor.start();

        IntentFilter f = new IntentFilter(AppEvents.ACTION_SPEED_UPDATE);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(speedReceiver, f);
        ContextCompat.registerReceiver(requireContext(), speedReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override public void onStop() {
        if (lightSensor != null) lightSensor.stop();
        try { LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(speedReceiver); } catch (Exception ignore) {}
        try { requireContext().unregisterReceiver(speedReceiver); } catch (Exception ignore) {}
        super.onStop();
    }

    @Override public void onDestroyView() {
        if (mockWeatherService != null) mockWeatherService.shutdown();
        binding = null;
        super.onDestroyView();
    }
}
