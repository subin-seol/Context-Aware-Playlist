package com.comp90018.contexttunes.ui.snap;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.comp90018.contexttunes.R;
import com.comp90018.contexttunes.services.CameraSensor;

public class SnapFragment extends Fragment {
    private TextureView textureView;
    private CameraSensor cameraSensor;

    /*
    This is called when the fragment is added to the activity.
    It inflates the layout for the fragment.
    It also initializes the camera sensor.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate layout
        return inflater.inflate(R.layout.fragment_snap, container, false);
    }

    /*
    This is called when the view hierarchy is created
    It gets the texture view and initializes the camera sensor.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Get the texture view
        textureView = view.findViewById(R.id.texture_view);
        // Initialize the camera sensor
        cameraSensor = new CameraSensor(requireContext() ,textureView);

        // Start camera when TextureView is ready
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                cameraSensor.startCameraPreview();
            }
            @Override
            public void onSurfaceTextureSizeChanged(@NonNull android.graphics.SurfaceTexture surface, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull android.graphics.SurfaceTexture surface) {
                cameraSensor.stopCameraPreview();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull android.graphics.SurfaceTexture surface) {}
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cameraSensor.stopCameraPreview();
    }
}
