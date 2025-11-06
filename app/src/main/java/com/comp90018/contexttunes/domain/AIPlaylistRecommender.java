package com.comp90018.contexttunes.domain;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.comp90018.contexttunes.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AI component that converts a sensed Context into a single Spotify search string
 * plus a short reason. No playlist selection is done here.
 * This class handles the complete flow of:
 * 1. Formatting context data into a detailed prompt
 * 2. Calling OpenAI API with proper error handling
 * 3. Parsing JSON responses into SearchRecommendation objects
 * 4. Graceful degradation on failures
 *
 * Usage:
 *   AIPlaylistRecommender recommender = new AIPlaylistRecommender();
 *   recommender.getRecommendations(context, new AIPlaylistRecommender.AICallback() {
 *   @Override public void onSuccess(SearchRecommendation rec) { // use rec.searchQuery}
 *   @Override public void onError(Exception e) { // fallback }
 */

public class AIPlaylistRecommender {

    private static final String TAG = "AIPlaylistRecommender";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";
    private static final int TIMEOUT_SECONDS = 10;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final Handler mainHandler;

    public interface AICallback {
        void onSuccess(@NonNull SearchRecommendation recommendation);
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
     * Request a single Spotify search string + reason from the AI based on the provided Context.
     */
    public void getSearchRecommendation(@NonNull Context context, @NonNull AICallback callback) {

        String prompt = buildDetailedPrompt(context);
        String requestJson = buildRequestJson(prompt);

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

        Log.d(TAG, "Sending OpenAI request with prompt: " + prompt);

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "OpenAI call failed", e);
                mainHandler.post(() -> callback.onError(e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful()) {
                        throw new IOException("API returned error: " + response.code() + " " + response.message());
                    }
                    String responseBody = response.body().string();
                    SearchRecommendation rec = parseResponse(responseBody);
                    mainHandler.post(() -> callback.onSuccess(rec));

                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse AI response", e);
                    mainHandler.post(() -> callback.onError(e));
                } finally {
                    response.close();
                }
            }
        });
    }

    // ---------------- Prompt & Request ----------------
    // Prompt engineering here
    // Go over this carefully to ensure high-quality AI responses
    @NonNull
    private String buildDetailedPrompt(@NonNull Context context) {
        StringBuilder prompt = new StringBuilder();

        // === ROLE DEFINITION ===
        prompt.append("You are an AI music recommendation expert for ContextTunes, ");
        prompt.append("a mobile app that suggests playlists based on real-time context from smartphone sensors.\n\n");
        prompt.append("You turn mobile context into a single Spotify search query.\n");
        prompt.append("You ONLY output a concise search string that would yield good playlists.\n");
        prompt.append("Also include a short reason explaining why this search string fits the context.\n\n");

        // Context
        prompt.append("=== CONTEXT ===\n");
        prompt.append("Activity: ").append(nullSafe(context.activity)).append("\n");
        prompt.append("Light: ").append(context.lightLevel != null ? context.lightLevel.name() : "UNKNOWN").append("\n");
        prompt.append("Time of day: ").append(nullSafe(context.timeOfDay)).append("\n");
        prompt.append("Weather: ").append(context.weather != null ? context.weather.name() : "UNKNOWN").append("\n");
        if (context.placeTag != null) {
            prompt.append("Place tag: ").append(context.placeTag).append("\n");
        } else if (context.nearbyPlaceTypes != null && !context.nearbyPlaceTypes.isEmpty()) {
            prompt.append("Nearby places: ").append(String.join(", ", context.nearbyPlaceTypes)).append("\n");
        } else {
            prompt.append("Location: unknown\n");
        }
        // Image labels (top-N strings)
        if (context.imageLabels != null && !context.imageLabels.isEmpty()) {
            prompt.append("Image labels: ")
                    .append(String.join(", ", context.imageLabels))
                    .append("\n");
        } else {
            prompt.append("Image labels: none\n");
        }
        prompt.append("\n");

        // === REASONING GUIDELINES ===
        prompt.append("=== GUIDELINES ===\n");
        prompt.append("- Provide a concise natural-language search string (3–7 words is typical), e.g., ")
                .append("\"gym 140 bpm\", \"rainy night lo-fi\", \"upbeat indie morning\", \"instrumental focus\".\n")
                .append("- Consider importance: place/camera > activity > time > weather > light.\n")
                .append("- The reason must be short (<= 100 chars) and reference specific context.\n\n");

        // === OUTPUT FORMAT ===
        prompt.append("=== OUTPUT FORMAT (JSON only) ===\n")
                .append("{\n")
                .append("  \"search_query\": \"<string to search on Spotify>\",\n")
                .append("  \"reason\": \"<short explanation (<=100 chars)>\"\n")
                .append("}\n")
                .append("No other text. No markdown.\n");

        return prompt.toString();
    }

    @NonNull
    private String nullSafe(String s) { return s == null ? "unknown" : s; }

    /**
     * Build the OpenAI API request JSON with optimal parameters.
     */
    @NonNull
    private String buildRequestJson(@NonNull String prompt) {
        JsonObject request = new JsonObject();
        request.addProperty("model", MODEL);
        request.addProperty("temperature", 0.9);  // Some creativity, but not too random
        request.addProperty("max_tokens", 150);
        request.addProperty("top_p", 0.9);        // Nucleus sampling for quality

        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content",
                "You output only valid JSON per the user's schema. No markdown, no prose.");
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        request.add("messages", messages);

        return gson.toJson(request);
    }

    // ---------------- Parsing ----------------

    /**
     * Parse OpenAI API response and convert to SearchRecommendation object.
     * Handles various error cases gracefully.
     */
    @NonNull
    private SearchRecommendation parseResponse(@NonNull String responseJson) throws Exception {
        OpenAIResponse apiResponse = gson.fromJson(responseJson, OpenAIResponse.class);

        if (apiResponse.choices == null || apiResponse.choices.isEmpty() || apiResponse.choices.get(0).message == null) {
            throw new Exception("No choices in API response");
        }

        String content = apiResponse.choices.get(0).message.content.trim();
        Log.d(TAG, "Raw AI response content: " + content);
        if (content == null) throw new Exception("Empty content from API");
        // Remove markdown code blocks if present
        content = content.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");

        AIPayload payload = gson.fromJson(content, AIPayload.class);
        if (payload == null || payload.searchQuery == null || payload.searchQuery.trim().isEmpty()) {
            throw new Exception("Missing search_query");
        }
        String reason = payload.reason == null ? "" : payload.reason.trim();
        return new SearchRecommendation(payload.searchQuery.trim(), reason);
    }

    // ---------------- DTOs ----------------

    private static class OpenAIResponse {
        @SerializedName("choices") java.util.List<Choice> choices;
        static class Choice { @SerializedName("message") Message message; }
        static class Message { @SerializedName("content") String content; }
    }

    private static class AIPayload {
        @SerializedName("search_query") String searchQuery;
        @SerializedName("reason") String reason;
    }



}
