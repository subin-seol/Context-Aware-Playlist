# ContextTunes 🎵

ContextTunes is an Android app that uses your smartphone’s sensors and metadata to automatically suggest music playlists that fit your current situation.

---

## 1. Project Structure

```
app/src/main/java/com/comp90018/contexttunes/
  data/        # Access to sensors, APIs, local storage
  domain/      # Core business logic (rules, models, use cases)
  services/    # Background services (sensor polling, auto mode)
  ui/          # Activities, fragments, UI components
  utils/       # Permission helpers, constants
```

---

## 4. Development Guidelines

- Use **Android View** in Android Studio for day-to-day coding.
- Use **Project View** for editing config files (e.g., .gitignore, build.gradle).
- Each new feature: create a new Git branch → open a Pull Request → merge to main.
- Keep commit messages short but descriptive (e.g., `feat: add accelerometer service`).

---

## Features & Requirements

### Sensors Used

- Camera
- GPS
- Light
- Accelerometer

### Functional Requirements

- Detect user activity using accelerometer
- Capture environmental cues: light, GPS, time, weather, camera
- Use context to select/recommend a music playlist
- Integrate with Spotify/Deezer/SoundCloud SDKs
- Notify user when a playlist is ready (push/in-app)
- Run a passive background service for context detection

### Non-Functional Requirements

- Usable, intuitive interface (minimal taps)
- Responsive, non-blocking (threads/services)
- Efficient data collection (battery-friendly)
- Privacy-respecting (camera permission, clear indication)

---

## Current High Level Status

- ✅ Empty Java project scaffolded
- ⬜ Add sensors
- ⬜ Add rule engine
- ⬜ Add Spotify integration

---

Feel free to update this README with more details as the project evolves.

