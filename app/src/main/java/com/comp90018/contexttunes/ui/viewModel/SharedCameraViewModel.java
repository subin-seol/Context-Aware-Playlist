package com.comp90018.contexttunes.ui.viewModel;

import android.graphics.Bitmap;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SharedCameraViewModel extends ViewModel {
    private final MutableLiveData<Bitmap> capturedImage = new MutableLiveData<>(); // this  holds a Bitmap that can change over time

    /**
     * This method stores the captured image in a MutableLiveData object.
     * @param bitmap
     */
    public void setCapturedImage(Bitmap bitmap) {
        capturedImage.setValue(bitmap);
    }

    /**
     * This method returns the captured image from a LiveData object.
     * @return
     */
    public LiveData<Bitmap> getCapturedImage() {
        return capturedImage;
    }
}
