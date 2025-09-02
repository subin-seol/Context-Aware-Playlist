package com.comp90018.contexttunes.services;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.TextureView;

import androidx.core.app.ActivityCompat;

public class CameraSensor {
    private Context context;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private TextureView textureView;

    // Define camera state callback, which is the callback for camera device state changes
    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@androidx.annotation.NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@androidx.annotation.NonNull CameraDevice camera) {
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@androidx.annotation.NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    public CameraSensor(Context context, TextureView textureView) {
        this.context = context;
        this.textureView = textureView;
    }

    /**
     * This method starts the camera preview
     */
    public void startCameraPreview() {
        startBackgroundThread(); // start a thread
        // request for camera service which returns an object that lets the application interact with the camera hardware
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            // Retrive id of currently connected camera
            String cameraId = manager.getCameraIdList()[0];
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // if no camera permission, ask user for permission
                ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.CAMERA}, 200);
                return;
            }
            // open camera
            manager.openCamera(cameraId, cameraStateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException("Error opening camera", e);
        }
    }

    public void takePhoto(){
        // logic to take photo

    }

    public void stopCameraPreview() {
        // logic to stop camera preview
    }

    // This starts a new background thread to handle camera operations
    private void startBackgroundThread(){
        backgroundThread = new HandlerThread("Camera Background");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    // This stops the background thread
    private void stopBackgroundThread(){
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            throw new RuntimeException("Error stopping background thread", e);
        }
    }

    // This method creates a camera preview session
    private void createCameraPreviewSession() {

    }
}
