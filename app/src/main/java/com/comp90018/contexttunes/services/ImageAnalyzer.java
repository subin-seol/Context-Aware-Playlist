package com.comp90018.contexttunes.services;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.comp90018.contexttunes.ui.viewModel.SharedCameraViewModel;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ImageAnalyzer {
    SharedCameraViewModel viewModel;

    public void analyzeImage(AppCompatActivity activity) {
        viewModel = new ViewModelProvider(activity).get(SharedCameraViewModel.class);
        Bitmap bitmap = viewModel.getCapturedImage().getValue();

        if (bitmap != null) {
            // Resize bitmap to model input size (usually 224x224)
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);

            // Load model
            try (MobilenetV11Model model = MobilenetV11Model.newInstance(activity)) {

                // Convert bitmap to TensorImage
                TensorImage inputImage = TensorImage.fromBitmap(resizedBitmap);

                // Run inference
                MobilenetV11Model.Outputs outputs = model.process(inputImage);

                // Get probabilities
                float[] probabilities = outputs.getProbabilityAsTensorBuffer().getFloatArray();

                // Find top 1
                int maxIndex = 0;
                float maxProb = 0f;
                for (int i = 0; i < probabilities.length; i++) {
                    if (probabilities[i] > maxProb) {
                        maxProb = probabilities[i];
                        maxIndex = i;
                    }
                }

                Log.d("ImageAnalyzer", "Predicted label index: " + maxIndex + ", confidence: " + maxProb);

            } catch (IOException e) {
                Log.e("ImageAnalyzer", "Error loading model", e);
            }
        }
    }
}
