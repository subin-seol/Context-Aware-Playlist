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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // --- AndroidX UI
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // --- CameraX
    implementation(libs.cameraCore)
    implementation(libs.cameraLifecycle)
    implementation(libs.cameraView)
    implementation(libs.cameraCamera2)

    // --- Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // --- Google Play services: Fused Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // --- Core (for ContextCompat.registerReceiver, etc.)
    implementation("androidx.core:core-ktx:1.13.1")
}