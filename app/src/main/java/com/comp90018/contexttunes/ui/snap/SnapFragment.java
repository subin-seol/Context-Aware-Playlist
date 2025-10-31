package com.comp90018.contexttunes.ui.snap;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import com.comp90018.contexttunes.data.viewModel.ImageViewModel;  
import com.comp90018.contexttunes.databinding.FragmentSnapBinding;
import com.comp90018.contexttunes.services.ImageAnalyser;          
import com.comp90018.contexttunes.utils.PermissionManager;          
import com.comp90018.contexttunes.utils.SettingsManager;

import java.io.IOException;

public class SnapFragment extends Fragment {
    private enum Pending { NONE, OPEN_CAMERA }
    private Pending pending = Pending.NONE;
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
                                ImageViewModel vm = new ViewModelProvider(requireActivity())
                                        .get(ImageViewModel.class);
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

        SettingsManager settingsManager = new SettingsManager(requireContext());
        if (!settingsManager.isCameraEnabled()) {
            view.post(() -> {
                Toast.makeText(requireContext(),
                        "Camera is disabled in settings", Toast.LENGTH_LONG).show();
                ((MainActivity) requireActivity()).goToHomeTab();
            });
            return;
        }

        if (!PermissionManager.hasCameraPermission(requireContext())) {
            pending = Pending.OPEN_CAMERA;
            PermissionManager.requestCamera(this);
            // we'll return; on grant we’ll continue setup in onRequestPermissionsResult
            return;
        }

        initCameraUi();
    }

    private void initCameraUi() {
        ImageViewModel viewModel = new ViewModelProvider(requireActivity())
                .get(ImageViewModel.class);

        cameraSensor = new CameraSensor(requireContext(), binding.cameraPreview, getViewLifecycleOwner());
        ((MainActivity) requireActivity()).setBottomNavVisibility(false);

        Bitmap existingBitmap = viewModel.getCapturedImage().getValue();
        if (existingBitmap != null) showCapturedUI(existingBitmap);
        else showCameraUI();

        viewModel.getCapturedImage().observe(getViewLifecycleOwner(), bitmap -> {
            if (bitmap != null) showCapturedUI(bitmap);
            else showCameraUI();
        });

        binding.btnCapture.setOnClickListener(v ->
                cameraSensor.takePhoto(capturedBitmap -> {
                    viewModel.setCapturedImage(capturedBitmap);
                    showCapturedUI(capturedBitmap);
                })
        );

        binding.btnRetake.setOnClickListener(v -> {
            viewModel.setCapturedImage(null);
            showCameraUI();
        });

        // Generate button
        binding.btnGenerate.setOnClickListener(v -> {
            // TODO: move this to rule engine pipeline when GO button is clicked on homepage
            ImageAnalyser analyser = new ImageAnalyser(requireContext());
            analyser.analyzeImage(viewModel);      // analyser spawns its own background thread
            ((MainActivity) requireActivity()).goToHomeTab();
        });

        binding.btnBack.setOnClickListener(v ->
                ((MainActivity) requireActivity()).goToHomeTab()
        );

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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionManager.REQ_CAMERA) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (!granted) {
                Toast.makeText(requireContext(),
                        "Camera permission needed for this feature", Toast.LENGTH_SHORT).show();
                ((MainActivity) requireActivity()).goToHomeTab();
                pending = Pending.NONE;
                return;
            }

            if (pending == Pending.OPEN_CAMERA) {
                pending = Pending.NONE;
                initCameraUi();
            }
        }
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