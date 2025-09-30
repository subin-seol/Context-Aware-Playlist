package com.comp90018.contexttunes.ui.snap;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.comp90018.contexttunes.MainActivity;
import com.comp90018.contexttunes.data.sensors.CameraSensor;
import com.comp90018.contexttunes.databinding.FragmentSnapBinding;
import com.comp90018.contexttunes.ui.viewModel.SharedCameraViewModel;
import com.comp90018.contexttunes.utils.SettingsManager;

import java.io.IOException;

public class SnapFragment extends Fragment {

    private FragmentSnapBinding binding;
    private CameraSensor cameraSensor;
    // Activity result launcher for image picking
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize the image picker launcher
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == requireActivity().RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            try {
                                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                                        requireActivity().getContentResolver(), selectedImageUri);
                                // Get viewModel instance since it might not be initialized yet
                                SharedCameraViewModel vm = new ViewModelProvider(requireActivity())
                                        .get(SharedCameraViewModel.class);
                                vm.setCapturedImage(bitmap);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
        );
    }


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

        // Check camera permission in settings first
        SettingsManager settingsManager = new SettingsManager(requireContext());
        if (!settingsManager.isCameraEnabled()) {
            // Post the navigation to avoid fragment transaction issues
            view.post(() -> {
                Toast.makeText(requireContext(),
                        "Camera is disabled in settings",
                        Toast.LENGTH_LONG).show();
                ((MainActivity) requireActivity()).goToHomeTab();
            });
            return;
        }

        SharedCameraViewModel viewModel = new ViewModelProvider(requireActivity())
                .get(SharedCameraViewModel.class);

        cameraSensor = new CameraSensor(requireContext(), binding.cameraPreview, getViewLifecycleOwner());

        // Always hide nav bar when in snap fragment
        ((MainActivity) requireActivity()).setBottomNavVisibility(false);

        // Determine initial UI state based on existing captured image
        Bitmap existingBitmap = viewModel.getCapturedImage().getValue();
        if (existingBitmap != null) {
            // Post-capture UI
            showCapturedUI(existingBitmap);
        } else {
            // Camera preview UI
            showCameraUI();
        }

        // Observe captured image changes
        viewModel.getCapturedImage().observe(getViewLifecycleOwner(), bitmap -> {
            if (bitmap != null) {
                // Post-capture UI
                showCapturedUI(bitmap);
            } else {
                // Camera preview UI
                showCameraUI();
            }
        });

        // Capture button
        binding.btnCapture.setOnClickListener(v -> cameraSensor.takePhoto(capturedBitmap -> {
            viewModel.setCapturedImage(capturedBitmap);
            showCapturedUI(capturedBitmap);
        }));

        // Retake button
        binding.btnRetake.setOnClickListener(v -> {
            viewModel.setCapturedImage(null);
            showCameraUI();
        });

        // Generate button
        binding.btnGenerate.setOnClickListener(v -> {
            ((MainActivity) requireActivity()).goToHomeTab();
        });

        // Back button
        binding.btnBack.setOnClickListener(v ->
                ((MainActivity) requireActivity()).goToHomeTab()
        );

        // Add upload button logic (uncomment if you have btnUpload)
        binding.btnUpload.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });
    }

    private void showCameraUI() {
        binding.cameraPreview.setVisibility(View.VISIBLE);
        binding.btnUpload.setVisibility(View.VISIBLE);
        binding.imagePreview.setVisibility(View.GONE);
        binding.btnCapture.setVisibility(View.VISIBLE);
        binding.btnRetake.setVisibility(View.GONE);
        binding.btnGenerate.setVisibility(View.GONE);
        cameraSensor.startCameraPreview();
    }

    private void showCapturedUI(Bitmap bitmap) {
        binding.imagePreview.setImageBitmap(bitmap);
        binding.cameraPreview.setVisibility(View.GONE);
        binding.imagePreview.setVisibility(View.VISIBLE);
        binding.btnCapture.setVisibility(View.GONE);
        binding.btnRetake.setVisibility(View.VISIBLE);
        binding.btnGenerate.setVisibility(View.VISIBLE);
        binding.btnUpload.setVisibility(View.GONE);
        cameraSensor.stopCameraPreview();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraSensor != null) {
            cameraSensor.stopCameraPreview();
        }
        ((MainActivity) requireActivity()).setBottomNavVisibility(true);
        binding = null;
    }
}