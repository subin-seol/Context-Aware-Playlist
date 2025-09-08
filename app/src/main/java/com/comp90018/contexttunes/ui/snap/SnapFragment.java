package com.comp90018.contexttunes.ui.snap;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.Fragment;

import com.comp90018.contexttunes.MainActivity;
import com.comp90018.contexttunes.data.sensors.CameraSensor;
import com.comp90018.contexttunes.databinding.FragmentSnapBinding;

public class SnapFragment extends Fragment {
    private FragmentSnapBinding binding;
    private CameraSensor cameraSensor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSnapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PreviewView previewView = binding.cameraPreview;
        cameraSensor = new CameraSensor(requireContext(), previewView, getViewLifecycleOwner());

        // Hide bottom nav bar when camera opens
        ((MainActivity) requireActivity()).setBottomNavVisibility(false);
        // Call camera preview
        cameraSensor.startCameraPreview();

        // Add event listener for shutter button
        binding.btnCapture.setOnClickListener(v -> {
            cameraSensor.takePhoto(bitmap -> {
                // Freeze preview and show captured image
                binding.imagePreview.setImageBitmap(bitmap);
                binding.cameraPreview.setVisibility(View.GONE);
                binding.imagePreview.setVisibility(View.VISIBLE);
                binding.btnCapture.setVisibility(View.GONE);
                binding.btnRetake.setVisibility(View.VISIBLE);
                binding.btnGenerate.setVisibility(View.VISIBLE);
            });
        });

        // Add event listener for cancel button
        binding.btnBack.setOnClickListener(v -> {
            // Redirect to home tab
            ((MainActivity) requireActivity()).goToHomeTab();
        });

        // Add event listener for retake button
        binding.btnRetake.setOnClickListener(v -> {
            // Show capture, hide retake & generate buttons
            binding.btnCapture.setVisibility(View.VISIBLE);
            binding.btnRetake.setVisibility(View.GONE);
            binding.btnGenerate.setVisibility(View.GONE);

            // Hide captured image
            binding.imagePreview.setVisibility(View.GONE);

            // Show live preview
            binding.cameraPreview.setVisibility(View.VISIBLE);

            // Restart camera preview
            cameraSensor.startCameraPreview();
        });

        // Add event listener for generate playlist button
        binding.btnGenerate.setOnClickListener(v2 -> {
            // TODO: add generate playlist logic here
            // redirect to homepage
            ((MainActivity) requireActivity()).goToHomeTab();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ((MainActivity) requireActivity()).setBottomNavVisibility(true);
        cameraSensor.stopCameraPreview();
        binding = null;
    }
}