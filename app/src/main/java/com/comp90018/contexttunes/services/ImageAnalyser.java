package com.comp90018.contexttunes.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.comp90018.contexttunes.ui.viewModel.SharedCameraViewModel;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Label;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

public class ImageAnalyser {

    private SharedCameraViewModel viewModel;
    private AmazonRekognitionClient rekognitionClient;

    public ImageAnalyser(Context context) {
        // Initialize AWS Rekognition client
        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                context,
                "ap-southeast-2:b9bccc79-a206-491c-bf00-3f5cff56d5c7", // replace with your identity pool
                Regions.AP_SOUTHEAST_2           // adjust to your region
        );
        rekognitionClient = new AmazonRekognitionClient(credentialsProvider);
    }

    public void analyzeImage(AppCompatActivity activity) {
        viewModel = new ViewModelProvider(activity).get(SharedCameraViewModel.class);
        Bitmap bitmap = viewModel.getCapturedImage().getValue();
        if (bitmap != null) {
            new Thread(() -> detectLabels(bitmap, activity)).start();
        } else {
            Log.w("ImageAnalyser", "No image captured");
        }
    }

    private void detectLabels(Bitmap bitmap, AppCompatActivity activity) {
        try {
            // Convert Bitmap to ByteBuffer
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
            byte[] imageBytes = stream.toByteArray();
            Image awsImage = new Image().withBytes(ByteBuffer.wrap(imageBytes));

            // Build Rekognition request
            DetectLabelsRequest request = new DetectLabelsRequest()
                    .withImage(awsImage)
                    .withMaxLabels(10)
                    .withMinConfidence(70f);

            // Call Rekognition
            DetectLabelsResult result = rekognitionClient.detectLabels(request);
            List<Label> labels = result.getLabels();

            // Build human-readable description
            StringBuilder description = new StringBuilder("Detected objects: ");
            for (Label label : labels) {
                description.append(label.getName())
                        .append(" (")
                        .append(Math.round(label.getConfidence()))
                        .append("%), ");
            }
            if (labels.isEmpty()) description.append("None");

            String finalDescription = description.toString();
            // Update UI on main thread
            activity.runOnUiThread(() -> {
                Log.d("ImageAnalyser", finalDescription);
            });

        } catch (Exception e) {
            e.printStackTrace();
            activity.runOnUiThread(() -> Log.e("ImageAnalyser", "Error analyzing image"));
        }
    }
}