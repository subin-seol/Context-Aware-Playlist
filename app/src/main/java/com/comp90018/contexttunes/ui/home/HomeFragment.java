package com.comp90018.contexttunes.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.comp90018.contexttunes.R;
import com.comp90018.contexttunes.data.sensors.LocationSensor;
import com.comp90018.contexttunes.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private LocationSensor locationSensor;
    private FragmentHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Use ViewBinding to inflate layout
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Initialize LocationSensor with activity context
        locationSensor = new LocationSensor(requireActivity());

        binding.buttonGetLocation.setOnClickListener(v -> {
            // Request location and update UI
            locationSensor.getCurrentLocation(location -> {
                if (location != null) {
                    binding.textLatitude.setText(getString(
                        R.string.latitude_format, location.getLatitude()));
                    binding.textLongitude.setText(getString(
                        R.string.longitude_format, location.getLongitude()));
                } else {
                    binding.textLatitude.setText(getString(R.string.latitude_na));
                    binding.textLongitude.setText(getString(R.string.longitude_na));
                }
            });
        });
    }

    // Handle permission result for location
    @SuppressWarnings("deprecation")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        locationSensor.handlePermissionResult(requestCode, grantResults, location -> {
            if (location != null) {
                binding.textLatitude.setText(getString(
                    R.string.latitude_format, location.getLatitude()));
                binding.textLongitude.setText(getString(
                    R.string.longitude_format, location.getLongitude()));
            } else {
                binding.textLatitude.setText(getString(R.string.latitude_na));
                binding.textLongitude.setText(getString(R.string.longitude_na));
            }
        });
    }
}
