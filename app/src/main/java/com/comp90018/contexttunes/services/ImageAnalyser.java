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
import com.comp90018.contexttunes.domain.ImageLabels;

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

    /**
     * Call this from UI code. We:
     *  1) read the current bitmap from the VM,
     *  2) spawn a small background thread to do network + encode,
     *  3) post labels back into the VM when done.
     */

    // Changed from requiring activity to just viewModel to decouple from UI
    public void analyzeImage(ImageViewModel viewModel) {
        Bitmap bitmap = viewModel.getCapturedImage().getValue();
        if (bitmap != null) {
            new Thread(() -> detectLabels(bitmap)).start();
        } else {
            Log.w("ImageAnalyser", "No image captured");
            // Optionally notify observers with an empty labels object:
            viewModel.postImageLabels(new ImageLabels());
        }
    }

    private void detectLabels(Bitmap bitmap) {
        try {
            // Convert Bitmap to ByteBuffer
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            // Can add downscaling here if needed to reduce size, to avoid OOM or network issues
            boolean ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
            if (!ok) {
                Log.e("ImageAnalyser", "Bitmap.compress returned false");
                return;
            }
            byte[] imageBytes = stream.toByteArray();
            Image awsImage = new Image().withBytes(ByteBuffer.wrap(imageBytes));

            // Build Rekognition request
            DetectLabelsRequest request = new DetectLabelsRequest()
                    .withImage(awsImage)
                    .withMaxLabels(10)
                    .withMinConfidence(70f);

            // Call Rekognition (network I/O, must be off main thread)
            DetectLabelsResult result = rekognitionClient.detectLabels(request);
            List<Label> labels = result.getLabels();

            // 4) Log + convert to our domain model
            if (labels == null || labels.isEmpty()) {
                Log.d("ImageAnalyser", "No labels detected.");
                // Still post an empty ImageLabels so observers know analysis finished
                viewModel.postImageLabels(new ImageLabels());
                return;
            }

            // Build human-readable description
            StringBuilder description = new StringBuilder("Detected objects: ");
            ImageLabels imageLabels = new ImageLabels();
            for (Label label : labels) {
                // Use the helper
                imageLabels.addLabel(label.getName(), label.getConfidence());
                description.append(label.getName())
                        .append(" (")
                        .append(Math.round(label.getConfidence()))
                        .append("%), ");
            }

            // Log and post to VM (bg-safe)
            String finalDescription = description.toString();
            Log.d("ImageAnalyser", finalDescription);

            viewModel.postImageLabels(imageLabels);

        } catch (Exception e) {
            Log.e("ImageAnalyser", "Error analyzing image", e);
        }
    }
}