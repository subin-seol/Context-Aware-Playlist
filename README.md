# ContextTunes 🎵

## Project Overview
ContextTunes is a sensor-driven, AI-augmented music recommendation app developed for COMP90018 (Mobile Computing Systems Programming). The app dynamically generates Spotify playlist recommendations based on the user's real-time environment, using on-device sensors (light, accelerometer, GPS, camera) and OpenAI's GPT-4o-mini model for contextual reasoning. It also integrates Google Places API and OpenWeather API to enhance context awareness. Key features include:

- **Sensor Integration**: Uses light sensor (ambient brightness), accelerometer (motion detection), GPS (location tracking), and camera (ML Kit for scene labeling) to infer user context (e.g., "running outdoors," "studying in low light").
- **External Services**: Combines sensor data with data from Google Places (for identifying nearby locations) and Open Weather (for retrieving real-time weather conditions) API for richer environmental context.
- **AI Reasoning**: Combines sensor data into a context vector and uses OpenAI's API to generate personalized Spotify search queries.
- **Spotify Integration**: Fetches and opens playlists via Spotify deep links.
- **User Interface**: Provides a simple, context-aware UI with real-time sensor feedback and playlist suggestions.

The project has been tested on a **Pixel 8 emulator** and a **physical Pixel 8 device** (API level 34, Android 14), ensuring compatibility with API level 26 or higher and Google Play Services.

## Setup and Execution Instructions

### Prerequisites
- **Android Studio**: Version 2023.3.1 or later.
- **JDK**: JDK 17 or later, configured in Android Studio.
- **Android Device/Emulator**: Pixel 8 emulator (API level 34) or a physical Android device (API level 26 or higher) with Google Play Services.
- **API Keys**: The provided `secrets.properties` contains valid keys for Google Places, OpenWeather, OpenAI, and AWS Rekognition. The Spotify access token must be regenerated (see step 3).
- **curl**: Required to generate a Spotify access token (available on most systems or installable via `sudo apt install curl` on Linux, or equivalent).

### Step-by-Step Instructions

#### 1. Unzip the Project
- Extract `ContextTunes_SourceCode.zip` or clone the repository to a directory (e.g., `~/ContextTunes`).
- Verify the directory contains:
  - `app/src/main/` (source code and resources, e.g., `HomeFragment.java`, `MainActivity.java`)
  - `build.gradle` (project-level)
  - `app/build.gradle` (app-level)
  - `settings.gradle`
  - `gradle.properties`
  - `secrets.properties` (with provided API keys (provided in zip file, not available from direct cloning))
  - `AndroidManifest.xml`

#### 2. Configure Dependencies
- The project uses the following key dependencies (defined in `app/build.gradle`):
  - Google Places SDK (`com.google.android.libraries.places:places`)
  - OkHttp (`com.squareup.okhttp3:okhttp`) for API requests
  - Gson (`com.google.code.gson:gson`) for JSON parsing
  - CameraX (`androidx.camera:camera-core`, `androidx.camera:camera-camera2`, `androidx.camera:camera-lifecycle`) for camera integration
  - ML Kit (`com.google.mlkit:vision`) for image labeling
  - Secrets Gradle Plugin (`com.google.android.libraries.mapsplatform.secrets-gradle-plugin`) for API key management
- Gradle will automatically download dependencies during the first sync.

#### 3. Configure the Spotify Access Token
The `secrets.properties` file in the project root contains:
```properties
PLACES_API_KEY=your_actual_places_api_key
OPENWEATHER_API_KEY=your_actual_openweather_api_key
OPENAI_API_KEY=your_actual_openai_api_key
SPOTIFY_ACCESS_TOKEN=your_actual_spotify_access_token
AWS_MODEL_KEY=your_actual_aws_rekognition_key
```

The Spotify access token expires every hour. To generate a new token (The client-id and client-secret are provided as part of submission zip):

1. Use the provided Spotify Client ID and Client Secret:
   - **Client ID**: `your-client-id`
   - **Client Secret**: `your-client-secret`

2. Run the following command in a terminal:
```bash
   curl -X POST "https://accounts.spotify.com/api/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=client_credentials&client_id=your-client-id&client_secret=your-client-secret"
```

3. Copy the `access_token` from the JSON response (e.g., `{"access_token": "BQ..."}`).

4. Update `secrets.properties` by replacing the `SPOTIFY_ACCESS_TOKEN` value with the new token.

**Note**: Regenerate the token if authentication errors occur during testing.

#### 4. Open the Project in Android Studio
- Launch Android Studio and select **Open an existing project**.
- Navigate to the unzipped directory (e.g., `~/ContextTunes`) and open it.
- Allow Gradle to sync the project (this may take a few minutes the first time).

#### 5. Configure the Emulator or Device

**Emulator:**
- Create a Pixel 8 AVD (API level 34) in Android Studio.
- Ensure Google Play Services is installed (required for Google Places API).
- Enable sensors (accelerometer, GPS, camera) in the emulator's extended controls for full functionality.

**Physical Device:**
- Use a Pixel 8 or another Android device (API level 26 or higher).
- Enable Developer Options and USB Debugging.
- Ensure Google Play Services and required sensors (light, accelerometer, GPS, camera) are available.

#### 6. Configure Permissions
The app requires the following permissions (managed via `PermissionManager`):
- `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` for GPS-based location tracking.
- `ACTIVITY_RECOGNITION` for accelerometer-based motion detection.
- `CAMERA` for image capture and ML Kit analysis.
- `POST_NOTIFICATIONS` (Android 13+) for foreground service notifications.

Default settings (`SettingsManager`) enable all sensors (location, camera, light, accelerometer). Grant permissions when prompted during app execution.

#### 7. Build and Run the Project
- Select the target device/emulator in Android Studio.
- Click **Build > Rebuild Project** to resolve dependencies.
- Click **Run > Run 'app'** to compile and install the app.
- Verify the build succeeds by checking the Android Studio Console for "Gradle Build Finished" (see screenshot in the project report).

#### 8. Testing the App
- On first launch, grant all requested permissions (location, activity recognition, camera, notifications).
- Navigate to the **Home** tab and tap **Generate Playlist** to test sensor-driven recommendations.
- Use the **Snap** feature to capture an image and verify visual context integration.
- Select a generated playlist to open in the Spotify app (if installed) or a browser.
- Verify sensor data (light, speed, location, weather) is displayed on the Home screen.
- The app has been tested on a Pixel 8 emulator and physical device, ensuring reliable performance.

### Troubleshooting
- **Gradle Sync Issues**: Ensure an active internet connection. Check `build.gradle` and `settings.gradle` for errors.
- **API Key Errors**: Verify that `secrets.properties` contains valid keys. Regenerate the Spotify access token if needed.
- **Sensor Issues**: Ensure the emulator/device supports required sensors. Check `SettingsManager` settings in the app to enable sensors if disabled.
- **Camera Issues**: Confirm the emulator/device has a functional camera. Grant `CAMERA` permission when prompted.
- **Spotify Errors**: If playlist generation fails, regenerate the Spotify access token using the curl command.

## Notes
- The app uses a modular architecture with sensor fusion, AI reasoning, and Spotify integration, as detailed in the project report.
- Contact the project team (Group 5, Tutorial 5) for assistance with setup or API key issues.

