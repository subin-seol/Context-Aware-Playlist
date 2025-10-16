package com.comp90018.contexttunes.data.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.comp90018.contexttunes.BuildConfig;
import com.comp90018.contexttunes.domain.Context;
import com.comp90018.contexttunes.domain.Playlist;
import com.comp90018.contexttunes.domain.Recommendation;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AI-powered playlist recommender using OpenAI's GPT-4o-mini.
 *
 * This class handles the complete flow of:
 * 1. Formatting context data into a detailed prompt
 * 2. Calling OpenAI API with proper error handling
 * 3. Parsing JSON responses into Recommendation objects
 * 4. Graceful degradation on failures
 *
 * Usage:
 *   AIPlaylistRecommender recommender = new AIPlaylistRecommender();
 *   recommender.getRecommendations(context, new AICallback() {
 *       @Override
 *       public void onSuccess(List<Recommendation> recommendations) {
 *           // Update UI with AI-powered recommendations
 *       }
 *
 *       @Override
 *       public void onError(Exception e) {
 *           // Fall back to rule engine
 *       }
 *   });
 */


// return a spotify string
// store location - ask ayush
// (50,100) - gym. (52,100) -> gym - ask ayush again
// How to truly capture/analyze sensor data - rather than passing it over?
    // light: dim [0,10] , normal [10,20], bright [20,100]
    // location:
// incorporate AWS Rekognition + Spotify API
// Amazon Bedrock - Nova + Nova lite
// log in with spotify -> put saved playlists in app.
// incorporate reasoning -- more info.
// separate page for playlist details?
// button from Go to View?
// match rate? -> confidence score from AI? Perhaps alternative cards?

public class AIPlaylistRecommender {

    private static final String TAG = "AIPlaylistRecommender";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";
    private static final int TIMEOUT_SECONDS = 10;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final Handler mainHandler;

    // Available playlists (same as RuleEngine)
    private static final Playlist FOCUS_PLAYLIST = new Playlist(
            "Focus",
            "spotify:playlist:37i9dQZF1DX0XUsuxWHRQd",
            "https://open.spotify.com/playlist/37i9dQZF1DX0XUsuxWHRQd"
    );

    private static final Playlist PUMP_UP_PLAYLIST = new Playlist(
            "Pump-up",
            "spotify:playlist:37i9dQZF1DX4JAvHpjipBk",
            "https://open.spotify.com/playlist/37i9dQZF1DX4JAvHpjipBk"
    );

    private static final Playlist CHILL_PLAYLIST = new Playlist(
            "Chill",
            "spotify:playlist:37i9dQZF1DWYcDQ1hSjOpY",
            "https://open.spotify.com/playlist/37i9dQZF1DWYcDQ1hSjOpY"
    );

    private static final Playlist MORNING_PLAYLIST = new Playlist(
            "Morning",
            "spotify:playlist:37i9dQZF1DX0yEZaMOXna3",
            "https://open.spotify.com/playlist/37i9dQZF1DX0yEZaMOXna3"
    );

    public interface AICallback {
        void onSuccess(@NonNull List<Recommendation> recommendations);

        void onError(@NonNull Exception e);
    }

    public AIPlaylistRecommender() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Get AI-powered playlist recommendations based on comprehensive context.
     * Callbacks are executed on the main thread for UI safety.
     */
    public void getRecommendations(@NonNull Context context, @NonNull AICallback callback) {

        String prompt = buildDetailedPrompt(context);
        String requestJson = buildRequestJson(prompt);

        Log.d(TAG, "Sending AI request with context: " + context.timeOfDay + ", " +
                context.lightLevel + ", " + context.weather + ", " + context.getLocationSummary());

        RequestBody body = RequestBody.create(
                requestJson,
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(OPENAI_API_URL)
                .post(body)
                .addHeader("Authorization", "Bearer " + BuildConfig.OPENAI_API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "API call failed", e);
                mainHandler.post(() -> callback.onError(e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful()) {
                        throw new IOException("API returned error: " + response.code() + " " + response.message());
                    }

                    String responseBody = response.body().string();
                    Log.d(TAG, "Received response from OpenAI");

                    List<Recommendation> recommendations = parseResponse(responseBody);
                    mainHandler.post(() -> callback.onSuccess(recommendations));

                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse response", e);
                    mainHandler.post(() -> callback.onError(e));
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Build a comprehensive, structured prompt for OpenAI.
     * <p>
     * The prompt includes:
     * - Clear role definition (music recommendation AI)
     * - Complete context data (sensors + location)
     * - Available playlist descriptions
     * - Reasoning guidelines
     * - Few-shot examples
     * - Strict JSON output format
     */
    @NonNull
    private String buildDetailedPrompt(@NonNull Context context) {
        StringBuilder prompt = new StringBuilder();

        // === ROLE DEFINITION ===
        prompt.append("You are an AI music recommendation expert for ContextTunes, ");
        prompt.append("a mobile app that suggests playlists based on real-time context from smartphone sensors.\n\n");

        // === CURRENT CONTEXT ===
        prompt.append("=== USER'S CURRENT CONTEXT ===\n");
        prompt.append("Analyze the following sensor data and environmental conditions:\n\n");

        prompt.append("PHYSICAL ACTIVITY:\n");
        prompt.append("  - Current activity: ").append(context.activity).append("\n");
        prompt.append("  - Interpretation: ");
        switch (context.activity.toLowerCase()) {
            case "still":
                prompt.append("User is stationary (sitting, standing, or lying down)\n");
                break;
            case "walking":
                prompt.append("User is walking at moderate pace\n");
                break;
            case "running":
                prompt.append("User is running or doing high-intensity exercise\n");
                break;
            default:
                prompt.append("Activity level unknown\n");
        }
        prompt.append("\n");

        prompt.append("LIGHTING CONDITIONS:\n");
        prompt.append("  - Ambient light: ").append(context.lightLevel.name()).append("\n");
        prompt.append("  - Interpretation: ");
        switch (context.lightLevel) {
            case BRIGHT:
                prompt.append("Very bright environment (>500 lux), likely outdoors or well-lit office\n");
                break;
            case NORMAL:
                prompt.append("Normal indoor lighting (100-500 lux), typical room brightness\n");
                break;
            case DIM:
                prompt.append("Low light (<100 lux), evening/night or dark room\n");
                break;
            default:
                prompt.append("Light level unknown\n");
        }
        prompt.append("\n");

        prompt.append("TIME CONTEXT:\n");
        prompt.append("  - Time of day: ").append(context.timeOfDay).append("\n");
        prompt.append("  - Typical activities during ").append(context.timeOfDay).append(": ");
        switch (context.timeOfDay.toLowerCase()) {
            case "morning":
                prompt.append("waking up, commuting, starting work, breakfast\n");
                break;
            case "afternoon":
                prompt.append("working, studying, lunch break, outdoor activities\n");
                break;
            case "evening":
                prompt.append("commuting home, dinner, relaxation, social activities\n");
                break;
            case "night":
                prompt.append("winding down, leisure time, preparing for sleep\n");
                break;
        }
        prompt.append("\n");

        prompt.append("WEATHER CONDITIONS:\n");
        prompt.append("  - Current weather: ").append(context.weather.name()).append("\n");
        prompt.append("  - Impact on mood: ");
        switch (context.weather) {
            case SUNNY:
                prompt.append("Bright weather typically elevates mood and energy levels\n");
                break;
            case CLOUDY:
                prompt.append("Overcast conditions may promote calm or introspective mood\n");
                break;
            case RAINY:
                prompt.append("Rainy weather often encourages cozy, relaxed, or melancholic mood\n");
                break;
            default:
                prompt.append("Weather impact unknown\n");
        }
        prompt.append("\n");

        prompt.append("LOCATION CONTEXT:\n");
        if (context.placeTag != null) {
            prompt.append("  - Tagged location: ").append(context.placeTag).append("\n");
            prompt.append("  - User has intentionally marked this location as '").append(context.placeTag).append("'\n");
            prompt.append("  - Typical activities at ").append(context.placeTag).append(": ");
            switch (context.placeTag.toLowerCase()) {
                case "home":
                    prompt.append("relaxing, personal time, household activities\n");
                    break;
                case "gym":
                    prompt.append("exercising, working out, physical training\n");
                    break;
                case "office":
                    prompt.append("working, meetings, professional tasks\n");
                    break;
                case "library":
                    prompt.append("studying, reading, focused work\n");
                    break;
                case "park":
                    prompt.append("outdoor leisure, walking, recreation\n");
                    break;
                case "cafe":
                    prompt.append("socializing, casual work, coffee break\n");
                    break;
                default:
                    prompt.append("varied activities\n");
            }
        } else if (!context.nearbyPlaceTypes.isEmpty()) {
            prompt.append("  - Nearby places: ").append(String.join(", ", context.nearbyPlaceTypes)).append("\n");
            prompt.append("  - User is in an area with these amenities/venues\n");
        } else {
            prompt.append("  - Location: Unknown or unavailable\n");
        }
        prompt.append("\n");

        // === AVAILABLE PLAYLISTS ===
        prompt.append("=== AVAILABLE PLAYLISTS ===\n");
        prompt.append("You must recommend EXACTLY 3 playlists from these 4 options:\n\n");

        prompt.append("1. FOCUS\n");
        prompt.append("   - Energy level: Low to medium\n");
        prompt.append("   - Characteristics: Instrumental, ambient, minimal vocals\n");
        prompt.append("   - Best for: Deep work, studying, concentration tasks, reading\n");
        prompt.append("   - Mood: Calm, focused, productive\n");
        prompt.append("   - Example scenarios: Library studying, office work, exam preparation\n\n");

        prompt.append("2. PUMP-UP\n");
        prompt.append("   - Energy level: Very high\n");
        prompt.append("   - Characteristics: Electronic, dance, hip-hop, fast tempo (120+ BPM)\n");
        prompt.append("   - Best for: Workouts, running, gym sessions, high-intensity activities\n");
        prompt.append("   - Mood: Energetic, motivated, powerful\n");
        prompt.append("   - Example scenarios: Gym workout, running, sports, cleaning with energy\n\n");

        prompt.append("3. CHILL\n");
        prompt.append("   - Energy level: Low\n");
        prompt.append("   - Characteristics: Acoustic, soft vocals, slow tempo (<100 BPM)\n");
        prompt.append("   - Best for: Relaxation, unwinding, evening downtime, cozy moments\n");
        prompt.append("   - Mood: Peaceful, mellow, introspective\n");
        prompt.append("   - Example scenarios: Rainy evening at home, bedtime routine, meditation\n\n");

        prompt.append("4. MORNING\n");
        prompt.append("   - Energy level: Medium to high\n");
        prompt.append("   - Characteristics: Upbeat, positive vibes, indie/pop, medium tempo\n");
        prompt.append("   - Best for: Starting the day, morning routines, commuting, breakfast\n");
        prompt.append("   - Mood: Optimistic, fresh, invigorating\n");
        prompt.append("   - Example scenarios: Morning coffee, shower, getting ready, sunrise\n\n");

        // === REASONING GUIDELINES ===
        prompt.append("=== REASONING GUIDELINES ===\n");
        prompt.append("CRITICAL: You MUST consider ALL context factors and provide DIVERSE recommendations.\n\n");

        prompt.append("1. PRIORITIZATION HIERARCHY:\n");
        prompt.append("   HIGH PRIORITY (Must strongly influence decision):\n");
        prompt.append("   - Tagged location (Home/Gym/Office/Library/Park/Cafe) - OVERRIDE other factors\n");
        prompt.append("   - Activity level (still/walking/running) - Physical state\n");
        prompt.append("   \n");
        prompt.append("   MEDIUM PRIORITY (Modifier/mood setter):\n");
        prompt.append("   - Weather conditions - Emotional impact\n");
        prompt.append("   - Time of day - Daily rhythm\n");
        prompt.append("   \n");
        prompt.append("   LOW PRIORITY (Fine-tuning):\n");
        prompt.append("   - Lighting level - Ambiance detail\n");
        prompt.append("   - Nearby place types - General area vibe\n\n");

        prompt.append("2. LOCATION-SPECIFIC RULES (MUST FOLLOW):\n");
        prompt.append("   - Cafe = Focus or Chill (casual work/socializing)\n");
        prompt.append("   - Gym = Pump-up (always prioritize high energy)\n");
        prompt.append("   - Library = Focus (always prioritize concentration)\n");
        prompt.append("   - Home + Evening = Chill (relaxation)\n");
        prompt.append("   - Home + Morning = Morning (fresh start)\n");
        prompt.append("   - Office = Focus (work productivity)\n");
        prompt.append("   - Park = Morning or Chill (outdoor leisure)\n\n");

        prompt.append("3. VARIATION REQUIREMENT:\n");
        prompt.append("   - Each recommendation MUST have a UNIQUE reason\n");
        prompt.append("   - Reference SPECIFIC context details (e.g., 'cafe atmosphere', 'gym energy')\n");
        prompt.append("   - Avoid generic phrases like 'cozy atmosphere' or 'calm background'\n");
        prompt.append("   - Be CREATIVE and SPECIFIC to the exact context\n\n");

        prompt.append("4. CONTEXT COMBINATIONS:\n");
        prompt.append("   - Running + Gym = Pump-up (obvious workout)\n");
        prompt.append("   - Still + Library + Bright = Focus (studying environment)\n");
        prompt.append("   - Still + Home + Evening + Rainy = Chill (cozy relaxation)\n");
        prompt.append("   - Walking + Morning + Sunny = Morning (energizing start)\n");
        prompt.append("   - Still + Dim + Night = Chill (winding down)\n\n");

        prompt.append("5. EDGE CASES:\n");
        prompt.append("   - If context is ambiguous, default to medium-energy options\n");
        prompt.append("   - If multiple playlists fit equally, prioritize based on time of day\n");
        prompt.append("   - Always provide 3 distinct recommendations (no duplicates)\n\n");

        // === FEW-SHOT EXAMPLES ===
        prompt.append("=== EXAMPLE RECOMMENDATIONS ===\n");
        prompt.append("Learn from these examples of good context-to-playlist mappings:\n\n");

        prompt.append("Example 1:\n");
        prompt.append("Context: running, BRIGHT, afternoon, SUNNY, at Gym\n");
        prompt.append("Recommendations:\n");
        prompt.append("  1. Pump-up - \"High-energy workout at the gym\"\n");
        prompt.append("  2. Morning - \"Bright sunny day boosts energy\"\n");
        prompt.append("  3. Focus - \"Cool-down after intense exercise\"\n\n");

        prompt.append("Example 2:\n");
        prompt.append("Context: still, BRIGHT, afternoon, SUNNY, at Library\n");
        prompt.append("Recommendations:\n");
        prompt.append("  1. Focus - \"Perfect study environment detected\"\n");
        prompt.append("  2. Morning - \"Bright afternoon energy boost\"\n");
        prompt.append("  3. Chill - \"Relaxed background for reading\"\n\n");

        prompt.append("Example 3:\n");
        prompt.append("Context: still, DIM, evening, RAINY, at Home\n");
        prompt.append("Recommendations:\n");
        prompt.append("  1. Chill - \"Cozy rainy evening at home\"\n");
        prompt.append("  2. Focus - \"Calm atmosphere for reflection\"\n");
        prompt.append("  3. Morning - \"Gentle uplift for mood\"\n\n");

        prompt.append("Example 4:\n");
        prompt.append("Context: walking, NORMAL, morning, CLOUDY, near park, cafe\n");
        prompt.append("Recommendations:\n");
        prompt.append("  1. Morning - \"Fresh start to the day\"\n");
        prompt.append("  2. Chill - \"Peaceful morning walk\"\n");
        prompt.append("  3. Pump-up - \"Energize your commute\"\n\n");

        prompt.append("Example 5: (IMPORTANT - Cafe vs Home)\n");
        prompt.append("Context: still, DIM, night, CLOUDY, at Cafe\n");
        prompt.append("Recommendations:\n");
        prompt.append("  1. Focus - \"Cafe ambiance perfect for work\"\n");
        prompt.append("  2. Chill - \"Relaxed coffee shop vibes\"\n");
        prompt.append("  3. Morning - \"Late-night study session boost\"\n\n");

        prompt.append("Example 6: (IMPORTANT - Park vs Cafe)\n");
        prompt.append("Context: still, DIM, night, CLOUDY, near park\n");
        prompt.append("Recommendations:\n");
        prompt.append("  1. Chill - \"Evening park walk relaxation\"\n");
        prompt.append("  2. Focus - \"Peaceful outdoor reflection\"\n");
        prompt.append("  3. Morning - \"Nature-inspired calm energy\"\n\n");

        // === OUTPUT FORMAT ===
        prompt.append("=== OUTPUT FORMAT ===\n");
        prompt.append("You MUST respond with ONLY valid JSON in this EXACT format:\n\n");
        prompt.append("{\n");
        prompt.append("  \"recommendations\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"playlist\": \"<playlist name>\",\n");
        prompt.append("      \"reason\": \"<brief explanation, max 50 characters>\"\n");
        prompt.append("    },\n");
        prompt.append("    {\n");
        prompt.append("      \"playlist\": \"<playlist name>\",\n");
        prompt.append("      \"reason\": \"<brief explanation, max 50 characters>\"\n");
        prompt.append("    },\n");
        prompt.append("    {\n");
        prompt.append("      \"playlist\": \"<playlist name>\",\n");
        prompt.append("      \"reason\": \"<brief explanation, max 50 characters>\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");

        prompt.append("CRITICAL RULES:\n");
        prompt.append("- Return EXACTLY 3 recommendations (no more, no less)\n");
        prompt.append("- Each playlist name must be one of: Focus, Pump-up, Chill, Morning\n");
        prompt.append("- Keep reasons concise (max 50 characters each)\n");
        prompt.append("- Order by best match first, then alternatives\n");
        prompt.append("- Do NOT include any text outside the JSON\n");
        prompt.append("- Do NOT use markdown code blocks\n");
        prompt.append("- Do NOT include explanations or commentary\n\n");

        prompt.append("Now analyze the user's current context and provide your recommendations:\n");

        return prompt.toString();
    }

    /**
     * Build the OpenAI API request JSON with optimal parameters.
     */
    @NonNull
    private String buildRequestJson(@NonNull String prompt) {
        JsonObject request = new JsonObject();
        request.addProperty("model", MODEL);
        request.addProperty("temperature", 0.9);  // Some creativity, but not too random
        request.addProperty("max_tokens", 300);   // Enough for 3 recommendations
        request.addProperty("top_p", 0.9);        // Nucleus sampling for quality

        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content",
                "You are a professional music recommendation AI. You always respond with valid JSON and never include explanatory text.");
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        request.add("messages", messages);

        return gson.toJson(request);
    }

    /**
     * Parse OpenAI API response and convert to Recommendation objects.
     * Handles various error cases gracefully.
     */
    @NonNull
    private List<Recommendation> parseResponse(@NonNull String responseJson) throws Exception {
        OpenAIResponse apiResponse = gson.fromJson(responseJson, OpenAIResponse.class);

        if (apiResponse.choices == null || apiResponse.choices.isEmpty()) {
            throw new Exception("No choices in API response");
        }

        String content = apiResponse.choices.get(0).message.content.trim();

        // Remove markdown code blocks if present
        content = content.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");

        Log.d(TAG, "Parsing AI response: " + content);

        // Parse the AI's JSON response
        AIRecommendationResponse aiRecs = gson.fromJson(content, AIRecommendationResponse.class);

        if (aiRecs.recommendations == null || aiRecs.recommendations.size() != 3) {
            throw new Exception("Expected 3 recommendations, got " +
                    (aiRecs.recommendations != null ? aiRecs.recommendations.size() : 0));
        }

        List<Recommendation> recommendations = new ArrayList<>();

        for (AIRecommendationResponse.PlaylistRec rec : aiRecs.recommendations) {
            Playlist playlist = getPlaylistByName(rec.playlist);
            if (playlist != null) {
                recommendations.add(new Recommendation(playlist, rec.reason));
            } else {
                Log.w(TAG, "Unknown playlist name from AI: " + rec.playlist);
            }
        }
        return recommendations;
    }

    /**
     * Map playlist name to Playlist object.
     * Case-insensitive matching with normalization.
     */
    @androidx.annotation.Nullable
    private Playlist getPlaylistByName(@NonNull String name) {
        String normalized = name.trim().toLowerCase();
        switch (normalized) {
            case "focus":
                return FOCUS_PLAYLIST;
            case "pump-up":
            case "pump up":
            case "pumpup":
                return PUMP_UP_PLAYLIST;
            case "chill":
                return CHILL_PLAYLIST;
            case "morning":
                return MORNING_PLAYLIST;
            default:
                Log.w(TAG, "Unknown playlist name: " + name);
                return null;
        }
    }

// ==================== Data Classes ====================

    /**
     * OpenAI API response structure.
     */
    private static class OpenAIResponse {
        @SerializedName("choices")
        List<Choice> choices;

        static class Choice {
            @SerializedName("message")
            Message message;
        }

        static class Message {
            @SerializedName("content")
            String content;
        }
    }

    /**
     * AI's recommendation response structure.
     */
    private static class AIRecommendationResponse {
        @SerializedName("recommendations")
        List<PlaylistRec> recommendations;

        static class PlaylistRec {
            @SerializedName("playlist")
            String playlist;

            @SerializedName("reason")
            String reason;
        }
    }



}
