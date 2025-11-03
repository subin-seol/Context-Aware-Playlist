package com.comp90018.contexttunes.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

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
import com.comp90018.contexttunes.utils.ImageLabelsHasher;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

public class ImageAnalyser {
    private static final String TAG = "ImageAnalyser";
    private static final int JPEG_QUALITY = 80;
    private static final int MAX_LABELS = 10;
    private static final float MIN_CONFIDENCE = 70f;

    private final Context appContext;
    private volatile AmazonRekognitionClient rekognitionClient; // lazy init on worker

    public ImageAnalyser(Context context) {
        this.appContext = context.getApplicationContext();

    }

    /**
     * Call this from UI code. We:
     *  1) read the current bitmap from the VM,
     *  2) spawn a small background thread to do network + encode,
     *  3) post labels back into the VM when done.
     */

    /** Cache-aware entry point. */
    public void analyzeImage(ImageViewModel viewModel) {
        Bitmap bitmap = viewModel.getCapturedImage().getValue();
        if (bitmap == null) {
            Log.w(TAG, "No image captured");
            viewModel.postImageLabels(new ImageLabels()); // signal "done" (empty)
            return;
        }

        // --- cache check: if labels already exist for this exact image, reuse and exit
        ImageLabels existing = viewModel.getImageLabels().getValue();
        String currentHash = ImageLabelsHasher.hash(bitmap);
        if (existing != null && currentHash.equals(existing.getSourceHash())) {
            Log.d(TAG, "Reusing cached labels for current image (hash hit).");
            // No post needed; observers already have it. If you want to re-emit:
            // viewModel.postImageLabels(existing);
            return;
        }

        // else compute on a worker
        new Thread(() -> detectLabels(bitmap, currentHash, viewModel)).start();
    }

    // Ensure client is initialized (thread-safe lazy init)
    private AmazonRekognitionClient getClient() {
        if (rekognitionClient == null) {
            synchronized (this) {
                if (rekognitionClient == null) {
                    CognitoCachingCredentialsProvider credentialsProvider =
                            new CognitoCachingCredentialsProvider(
                                    appContext,
                                    BuildConfig.AWS_MODEL_KEY,
                                    Regions.AP_SOUTHEAST_2
                            );
                    rekognitionClient = new AmazonRekognitionClient(credentialsProvider);
                }
            }
        }
        return rekognitionClient;
    }

    private void detectLabels(Bitmap bitmap, String sourceHash, ImageViewModel viewModel) {
        try {
            // Convert Bitmap to ByteBuffer
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            // Can add downscaling here if needed to reduce size, to avoid OOM or network issues
            boolean ok = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream);
            if (!ok) {
                Log.e(TAG, "Bitmap.compress returned false");
                viewModel.postImageLabels(new ImageLabels()); // still signal "done"
                return;
            }
            byte[] imageBytes = stream.toByteArray();
            Image awsImage = new Image().withBytes(ByteBuffer.wrap(imageBytes));

            // Build Rekognition request
            DetectLabelsRequest request = new DetectLabelsRequest()
                    .withImage(awsImage)
                    .withMaxLabels(MAX_LABELS)
                    .withMinConfidence(MIN_CONFIDENCE);

            // Call Rekognition (network I/O, must be off main thread)
            DetectLabelsResult result = getClient().detectLabels(request);
            List<Label> labels = result.getLabels();

            ImageLabels imageLabels = new ImageLabels();
            if (labels != null) {
                for (Label l : labels) {
                    imageLabels.addLabel(l.getName(), l.getConfidence());
                }
            }
            imageLabels.setSourceHash(sourceHash);

            Log.d(TAG, "Detected " + (labels == null ? 0 : labels.size()) + " labels; hash=" + sourceHash);
            viewModel.postImageLabels(imageLabels);

        } catch (Exception e) {
            Log.e(TAG, "Error analyzing image", e);
            // Post an empty object so the pipeline can proceed
            viewModel.postImageLabels(new ImageLabels());
        }
    }
}