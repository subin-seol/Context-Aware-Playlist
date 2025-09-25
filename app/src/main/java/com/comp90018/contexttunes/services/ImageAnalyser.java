package com.comp90018.contexttunes.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Label;
import com.comp90018.contexttunes.BuildConfig;
import com.comp90018.contexttunes.data.viewModel.ImageViewModel;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

public class ImageAnalyser {

    private ImageViewModel viewModel;
    private AmazonRekognitionClient rekognitionClient;

    public ImageAnalyser(Context context) {
        // Initialize AWS Rekognition client
        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                context,
                BuildConfig.AWS_MODEL_KEY,
                Regions.AP_SOUTHEAST_2
        );
        rekognitionClient = new AmazonRekognitionClient(credentialsProvider);
    }

    public void analyzeImage(AppCompatActivity activity) {
        viewModel = new ViewModelProvider(activity).get(ImageViewModel.class);
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
            ImageLabels imageLabels = new ImageLabels();
            for (Label label : labels) {
                description.append(label.getName())
                        .append(" (")
                        .append(Math.round(label.getConfidence()))
                        .append("%), ");
                // Add labels and confidence to image view model
                ImageLabels.LabelConfidence labelConfidence = new ImageLabels.LabelConfidence(label.getName(), label.getConfidence());
                imageLabels.getItems().add(labelConfidence);
            }
            if (labels.isEmpty()) description.append("None");

            String finalDescription = description.toString();

            // Log the labels
            activity.runOnUiThread(() -> {
                Log.d("ImageAnalyser", finalDescription);
            });

        } catch (Exception e) {
            e.printStackTrace();
            activity.runOnUiThread(() -> Log.e("ImageAnalyser", "Error analyzing image"));
        }
    }
}