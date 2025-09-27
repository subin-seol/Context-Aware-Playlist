plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.comp90018.contexttunes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.comp90018.contexttunes"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Read Places API key from gradle.properties or environment variable (fallback to empty string)
    val placesKey: String =
        (providers.gradleProperty("PLACES_API_KEY").orNull
            ?: System.getenv("PLACES_API_KEY") ?: "")

    // Read OpenWeather API key from gradle.properties or environment variable (fallback to empty string)
    val openWeatherKey: String =
        (providers.gradleProperty("OPENWEATHER_API_KEY").orNull
            ?: System.getenv("OPENWEATHER_API_KEY") ?: "")

    buildTypes {
        getByName("debug") {
            // Expose keys via BuildConfig (avoid hardcoding in source)
            buildConfigField("String", "PLACES_API_KEY", "\"$placesKey\"")
            buildConfigField("String", "OPENWEATHER_API_KEY", "\"$openWeatherKey\"")
            isMinifyEnabled = false
        }
        getByName("release") {
            buildConfigField("String", "PLACES_API_KEY", "\"$placesKey\"")
            buildConfigField("String", "OPENWEATHER_API_KEY", "\"$openWeatherKey\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Keep consistent with the rest of the project
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true // Required to use buildConfigField
    }
}

dependencies {
    // ---- UI basics ----
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // ---- CameraX ----
    implementation(libs.cameraCore)
    implementation(libs.cameraLifecycle)
    implementation(libs.cameraView)
    implementation(libs.cameraCamera2)

    // ---- Google Location / Maps / Places ----
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.libraries.places:places:3.5.0")

    // ---- Local broadcast (Service <-> Fragment) ----
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // ---- Test ----
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
