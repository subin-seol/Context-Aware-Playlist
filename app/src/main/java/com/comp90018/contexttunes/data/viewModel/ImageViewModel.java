package com.comp90018.contexttunes.data.viewModel;

import android.graphics.Bitmap;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.contexttunes.domain.ImageLabels;

public class ImageViewModel extends ViewModel {
    private final MutableLiveData<Bitmap> capturedImage = new MutableLiveData<>(); // this  holds a Bitmap that can change over time
    private final MutableLiveData<ImageLabels> imageLabels = new MutableLiveData<>(); // this holds the labels for the image

    /**
     * This method stores the captured image in a MutableLiveData object.
     * @param bitmap
     */
    public void setCapturedImage(Bitmap bitmap) {
        capturedImage.setValue(bitmap);
        imageLabels.setValue(null); // invalidate labels so Home knows to recompute
    }

    /**
     * This method returns the captured image from a LiveData object.
     * @return
     */
    public LiveData<Bitmap> getCapturedImage() {
        return capturedImage;
    }

    /**
     * This method stores the labels for the image in a MutableLiveData object. (main thread)
     * @param labels
     */
    public void setImageLabels(ImageLabels labels) {
        imageLabels.setValue(labels);
    }

    /**
     * Store labels from a background thread (used by ImageAnalyser).
     * @param labels
     */
    public void postImageLabels(ImageLabels labels) {
        imageLabels.postValue(labels);
    }

    /**
     * This method returns the labels for the image from a LiveData object.
     * @return
     */
    public LiveData<ImageLabels> getImageLabels() {
        return imageLabels;
    }

    /** Clear image & labels after a run so the next run has none. */
    public void clearImage() {
        // (Optional) recycle if created the bitmap manually
        // Bitmap b = capturedImage.getValue();
        // if (b != null && !b.isRecycled()) b.recycle();
        capturedImage.setValue(null);
        imageLabels.setValue(null);
    }
}
