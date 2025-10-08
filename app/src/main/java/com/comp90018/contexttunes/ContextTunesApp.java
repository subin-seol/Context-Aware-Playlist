package com.comp90018.contexttunes;

import android.app.Application;
import com.google.android.libraries.places.api.Places;

public class ContextTunesApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        // Initialize the Places SDK ONCE for the whole app
        Places.initializeWithNewPlacesApiEnabled(getApplicationContext(), BuildConfig.PLACES_API_KEY);
    }
}