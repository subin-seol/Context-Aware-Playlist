package com.comp90018.contexttunes.ui.home;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.comp90018.contexttunes.BuildConfig;
import com.comp90018.contexttunes.MainActivity;

import com.comp90018.contexttunes.R;
import com.comp90018.contexttunes.data.api.GooglePlacesAPI;
import com.comp90018.contexttunes.data.sensors.LocationSensor;
import com.comp90018.contexttunes.data.sensors.LightSensor;
import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;
import com.comp90018.contexttunes.databinding.FragmentHomeBinding;
import com.comp90018.contexttunes.domain.Context;
import com.comp90018.contexttunes.domain.Recommendation;
import com.comp90018.contexttunes.domain.RuleEngine;
import com.google.android.libraries.places.api.model.Place;
import com.comp90018.contexttunes.ui.viewModel.SharedCameraViewModel;
import com.comp90018.contexttunes.utils.PlaylistOpener;

import java.util.List;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private FragmentHomeBinding binding;

    private LightSensor lightSensor;
    private LocationSensor locationSensor;

    private Recommendation currentRecommendation = null;
    private GooglePlacesAPI googlePlacesAPI;

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
        binding.welcomeTitle.setText("Welcome back Alex!");

        // --- LIGHT SENSOR WIRING ---
        lightSensor = new LightSensor(requireContext(), bucket -> {
            if (binding == null) return;
            requireActivity().runOnUiThread(() -> {
                String label;
                switch (bucket) {
                    case DIM:
                        label = "Light: Dim";
                        break;
                    case NORMAL:
                        label = "Light: Normal";
                        break;
                    case BRIGHT:
                        label = "Light: Bright";
                        break;
                    default:
                        label = "Light: N/A";
                }
                binding.lightValue.setText(label);

                // Update recommendation when light changes
                updateRecommendation(bucket);
            });
        });

        // --- LOCATION SENSOR SETUP ---
        locationSensor = new LocationSensor(requireActivity());
        googlePlacesAPI = new GooglePlacesAPI(requireContext(), BuildConfig.PLACES_API_KEY);

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


    }


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

    private void updateRecommendation(LightBucket lightBucket) {
        // Create current context by combining sensor data
        String timeOfDay = RuleEngine.getCurrentTimeOfDay();
        String activity = "still"; // Mock data for now
        Context context = new Context(lightBucket, timeOfDay, activity);

        // Get rec from rule engine
        Recommendation recommendation = RuleEngine.getRecommendation(context);

        // Update UI
        binding.welcomeSubtitle.setText(recommendation.reason);

        // Store for GO button
        currentRecommendation = recommendation;
    }

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

    // Forward permission results to LocationSensor
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        locationSensor.handlePermissionResult(requestCode, grantResults, location -> {
            if (location != null) {
                fetchNearbyPlaces(location);
            }
        });
    }

    // Small helper to print nicer bucket names
    private String pretty(LightBucket b) {
        if (b == null) return "N/A";
        switch (b) {
            case DIM: return "Dim";
            case NORMAL: return "Normal";
            case BRIGHT: return "Bright";
            default: return "N/A";
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
        super.onDestroyView();
        binding = null;
    }
}
