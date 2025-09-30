package com.comp90018.contexttunes.ui.settings;

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

import com.comp90018.contexttunes.data.sensors.LocationSensor;
import com.comp90018.contexttunes.databinding.FragmentSettingsBinding;
import com.comp90018.contexttunes.utils.SettingsManager;

/**
 * Settings Fragment - allows users to configure app behavior.
 *
 * Features:
 * - Detection mode selection (Passive/Active)
 * - Sensor permission toggles
 * - Location tagging for common places
 * - Notification preferences
 * - Account management
 */
public class SettingsFragment extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST = 200;
    private static final int CAMERA_PERMISSION_REQUEST = 201;

    private FragmentSettingsBinding binding;
    private SettingsManager settingsManager;
    private LocationSensor locationSensor;

    // Track which location button was clicked for permission callback
    private String pendingLocationTag = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize managers
        settingsManager = new SettingsManager(requireContext());
        locationSensor = new LocationSensor(requireContext());

        // Load saved settings
        loadSettings();

        // Setup listeners
        setupDetectionModeListeners();
        setupSensorPermissionListeners();
        setupLocationTaggingListeners();
        setupNotificationListeners();
        setupAccountListeners();
    }

    /**
     * Load all saved settings from SharedPreferences and update UI
     */
    private void loadSettings() {
        // Detection Mode
        boolean isPassive = settingsManager.isPassiveMode();
        binding.switchPassive.setChecked(isPassive);
        binding.switchActive.setChecked(!isPassive);

        // Sensor Permissions
        binding.switchLocation.setChecked(settingsManager.isLocationEnabled());
        binding.switchCamera.setChecked(settingsManager.isCameraEnabled());
        binding.switchLight.setChecked(settingsManager.isLightEnabled());
        binding.switchAccelerometer.setChecked(settingsManager.isAccelerometerEnabled());

        // Notifications
        binding.switchPlaylistSuggestions.setChecked(settingsManager.isPlaylistSuggestionsEnabled());
        binding.switchContextChanges.setChecked(settingsManager.isContextChangesEnabled());

        // Update location tag button states
        updateLocationTagButtons();
    }

    /**
     * Setup Detection Mode toggle listeners.
     * Only one mode can be active at a time (Passive or Active).
     */
    private void setupDetectionModeListeners() {
        binding.switchPassive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                binding.switchActive.setChecked(false);
                settingsManager.setDetectionMode(true); // true = passive
                Toast.makeText(requireContext(), "Passive mode enabled", Toast.LENGTH_SHORT).show();
            } else if (!binding.switchActive.isChecked()) {
                // If unchecking passive and active is also off, turn active back on
                binding.switchActive.setChecked(true);
            }
        });

        binding.switchActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                binding.switchPassive.setChecked(false);
                settingsManager.setDetectionMode(false); // false = active
                Toast.makeText(requireContext(), "Active mode enabled", Toast.LENGTH_SHORT).show();
            } else if (!binding.switchPassive.isChecked()) {
                // If unchecking active and passive is also off, turn passive back on
                binding.switchPassive.setChecked(true);
            }
        });
    }

    /**
     * Setup Sensor Permission toggle listeners.
     * These control which sensors the app is allowed to use.
     */
    private void setupSensorPermissionListeners() {
        binding.switchLocation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setLocationEnabled(isChecked);
            if (isChecked && !hasLocationPermission()) {
                requestLocationPermission();
            }
            Toast.makeText(requireContext(),
                    "Location " + (isChecked ? "enabled" : "disabled"),
                    Toast.LENGTH_SHORT).show();
        });

        binding.switchCamera.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setCameraEnabled(isChecked);
            if (isChecked && !hasCameraPermission()) {
                requestCameraPermission();
            }
            Toast.makeText(requireContext(),
                    "Camera " + (isChecked ? "enabled" : "disabled"),
                    Toast.LENGTH_SHORT).show();
        });

        binding.switchLight.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setLightEnabled(isChecked);
            Toast.makeText(requireContext(),
                    "Light sensor " + (isChecked ? "enabled" : "disabled"),
                    Toast.LENGTH_SHORT).show();
        });

        binding.switchAccelerometer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setAccelerometerEnabled(isChecked);
            Toast.makeText(requireContext(),
                    "Accelerometer " + (isChecked ? "enabled" : "disabled"),
                    Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Setup Location Tagging button listeners.
     * These buttons save the user's current GPS location with a label (Home, Gym, etc.)
     */
    private void setupLocationTaggingListeners() {
        binding.btnTagHome.setOnClickListener(v -> tagCurrentLocation("Home"));
        binding.btnTagGym.setOnClickListener(v -> tagCurrentLocation("Gym"));
        binding.btnTagOffice.setOnClickListener(v -> tagCurrentLocation("Office"));
        binding.btnTagLibrary.setOnClickListener(v -> tagCurrentLocation("Library"));
        binding.btnTagPark.setOnClickListener(v -> tagCurrentLocation("Park"));
        binding.btnTagCafe.setOnClickListener(v -> tagCurrentLocation("Cafe"));

        // Long press to clear saved location
        binding.btnTagHome.setOnLongClickListener(v -> clearLocationTag("Home"));
        binding.btnTagGym.setOnLongClickListener(v -> clearLocationTag("Gym"));
        binding.btnTagOffice.setOnLongClickListener(v -> clearLocationTag("Office"));
        binding.btnTagLibrary.setOnLongClickListener(v -> clearLocationTag("Library"));
        binding.btnTagPark.setOnLongClickListener(v -> clearLocationTag("Park"));
        binding.btnTagCafe.setOnLongClickListener(v -> clearLocationTag("Cafe"));
    }

    /**
     * Tag the user's current location with a label.
     * Requires location permission.
     */
    private void tagCurrentLocation(String tag) {
        if (!hasLocationPermission()) {
            pendingLocationTag = tag;
            requestLocationPermission();
            return;
        }

        Toast.makeText(requireContext(), "Getting your location...", Toast.LENGTH_SHORT).show();

        locationSensor.getCurrentLocation(location -> {
            if (location != null) {
                settingsManager.saveLocation(tag, location.getLatitude(), location.getLongitude());
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(),
                            tag + " location saved!",
                            Toast.LENGTH_SHORT).show();
                    updateLocationTagButtons();
                });
            } else {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Couldn't get location",
                                Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    /**
     * Clear a saved location tag.
     * Returns true to indicate long press was handled.
     */
    private boolean clearLocationTag(String tag) {
        if (settingsManager.hasLocation(tag)) {
            settingsManager.clearLocation(tag);
            Toast.makeText(requireContext(), tag + " location cleared", Toast.LENGTH_SHORT).show();
            updateLocationTagButtons();
        } else {
            Toast.makeText(requireContext(), "No " + tag + " location saved", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /**
     * Update location tag buttons to show which locations are saved.
     * Saved locations appear with a teal accent color.
     */
    private void updateLocationTagButtons() {
        updateButtonState(binding.btnTagHome, "Home");
        updateButtonState(binding.btnTagGym, "Gym");
        updateButtonState(binding.btnTagOffice, "Office");
        updateButtonState(binding.btnTagLibrary, "Library");
        updateButtonState(binding.btnTagPark, "Park");
        updateButtonState(binding.btnTagCafe, "Cafe");
    }

    /**
     * Update a single button's appearance based on whether location is saved
     */
    private void updateButtonState(com.google.android.material.button.MaterialButton button, String tag) {
        if (settingsManager.hasLocation(tag)) {
            button.setStrokeColorResource(com.comp90018.contexttunes.R.color.teal_accent);
            button.setTextColor(ContextCompat.getColor(requireContext(),
                    com.comp90018.contexttunes.R.color.teal_accent));
        } else {
            button.setStrokeColorResource(com.comp90018.contexttunes.R.color.text_secondary);
            button.setTextColor(ContextCompat.getColor(requireContext(),
                    com.comp90018.contexttunes.R.color.text_primary));
        }
    }

    /**
     * Setup Notification preference listeners
     */
    private void setupNotificationListeners() {
        binding.switchPlaylistSuggestions.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setPlaylistSuggestionsEnabled(isChecked);
            Toast.makeText(requireContext(),
                    "Playlist suggestions " + (isChecked ? "enabled" : "disabled"),
                    Toast.LENGTH_SHORT).show();
        });

        binding.switchContextChanges.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setContextChangesEnabled(isChecked);
            Toast.makeText(requireContext(),
                    "Context change notifications " + (isChecked ? "enabled" : "disabled"),
                    Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Setup Account section listeners
     */
    private void setupAccountListeners() {
        binding.btnSpotifyConnection.setOnClickListener(v -> {
            Toast.makeText(requireContext(),
                    "Spotify connection management coming soon!",
                    Toast.LENGTH_SHORT).show();
        });

        binding.btnSignOut.setOnClickListener(v -> {
            Toast.makeText(requireContext(),
                    "Sign out functionality coming soon!",
                    Toast.LENGTH_SHORT).show();
        });
    }

    // ===== Permission Helpers =====

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(requireActivity(),
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST);
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(requireActivity(),
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Location permission granted", Toast.LENGTH_SHORT).show();

                // If there's a pending location tag, complete it now
                if (pendingLocationTag != null) {
                    tagCurrentLocation(pendingLocationTag);
                    pendingLocationTag = null;
                }
            } else {
                Toast.makeText(requireContext(),
                        "Location permission needed for this feature",
                        Toast.LENGTH_SHORT).show();
                binding.switchLocation.setChecked(false);
            }
        } else if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Camera permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(),
                        "Camera permission needed for this feature",
                        Toast.LENGTH_SHORT).show();
                binding.switchCamera.setChecked(false);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}