package com.comp90018.contexttunes.data.sensors;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class CameraSensor {
    private final Context context;
    private final PreviewView previewView;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;

    private final LifecycleOwner lifecycleOwner;

    public CameraSensor(Context context, PreviewView previewView, LifecycleOwner lifecycleOwner) {
        this.context = context;
        this.previewView = previewView;
        this.lifecycleOwner = lifecycleOwner;
    }

    public void startCameraPreview() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.CAMERA}, 200);
            return;
        }
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreviewAndCapture();
            } catch (Exception e) {
                Log.e("CameraX", "Failed to get camera provider", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindPreviewAndCapture() {
        Preview preview = new Preview.Builder().build();
        imageCapture = new ImageCapture.Builder().build();
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture);
    }

    public void takePhoto(@NonNull Consumer<Bitmap> callback) {
        if (imageCapture == null) return;

        imageCapture.takePicture(ContextCompat.getMainExecutor(context),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        new Thread(() -> {
                            Bitmap bitmap = CameraSensor.imageProxyToBitmap(imageProxy);

                            // Fix rotation based on ImageProxy rotationDegrees
                            int rotation = imageProxy.getImageInfo().getRotationDegrees();
                            if (rotation != 0 && bitmap != null) {
                                android.graphics.Matrix matrix = new android.graphics.Matrix();
                                matrix.postRotate(rotation);
                                bitmap = Bitmap.createBitmap(
                                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true
                                );
                            }

                            imageProxy.close();

                            Bitmap finalBitmap = bitmap;
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (finalBitmap != null) {
                                    callback.accept(finalBitmap);
                                }
                            });
                        }).start();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("CameraX", "Photo capture failed", exception);
                    }
                }
        );
    }

    public static Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    public void stopCameraPreview() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}