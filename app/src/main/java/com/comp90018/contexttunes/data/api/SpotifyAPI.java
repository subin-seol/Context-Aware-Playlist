package com.comp90018.contexttunes.data.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.comp90018.contexttunes.domain.SpotifyPlaylist;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpotifyAPI {

    private static final String BASE_URL = "https://api.spotify.com/v1/search";
    private static final int MAX_OFFSET_ATTEMPTS = 5; // Maximum pagination attempts
    private static final int BATCH_SIZE = 20; // Fetch more items per request to reduce API calls

    private String accessToken;
    private ExecutorService executorService;
    private Handler mainHandler;

    public SpotifyAPI(String accessToken) {
        this.accessToken = accessToken;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // Callback interface for async results
    public interface PlaylistCallback {
        void onSuccess(List<SpotifyPlaylist> playlists);
        void onError(String error);
    }

    // Search for playlists
    public void searchPlaylists(String query, int limit, PlaylistCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d("SpotifyAPI", "Searching albums for query: " + query + ", limit: " + limit);
                List<SpotifyPlaylist> playlists = performSearchWithExactLimit(query, limit);
                mainHandler.post(() -> callback.onSuccess(playlists));
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                Log.e("SpotifyAPI", "Search error: " + errorMsg, e);
                mainHandler.post(() -> callback.onError(errorMsg));
            }
        });
    }

    /**
     * Keeps querying the API with pagination until we have exactly 'limit' valid items
     * or we've exhausted available results.
     */
    private List<SpotifyPlaylist> performSearchWithExactLimit(String query, int limit) throws Exception {
        List<SpotifyPlaylist> results = new ArrayList<>();
        int offset = 0;
        int attempts = 0;

        while (results.size() < limit && attempts < MAX_OFFSET_ATTEMPTS) {
            // Fetch a batch of items
            int requestSize = Math.min(BATCH_SIZE, limit * 2); // Request more to account for nulls
            List<SpotifyPlaylist> batch = performSearch(query, requestSize, offset);

            if (batch.isEmpty()) {
                Log.d("SpotifyAPI", "No more results available at offset " + offset);
                break; // No more results available
            }

            // Add valid items until we reach the limit
            for (SpotifyPlaylist playlist : batch) {
                if (results.size() >= limit) {
                    break;
                }
                results.add(playlist);
            }

            Log.d("SpotifyAPI", "Collected " + results.size() + "/" + limit + " items (attempt " + (attempts + 1) + ")");

            // Move to next batch
            offset += requestSize;
            attempts++;
        }

        // Return exactly 'limit' items (or fewer if not enough available)
        if (results.size() > limit) {
            results = results.subList(0, limit);
        }

        Log.d("SpotifyAPI", "Final result: " + results.size() + " valid items");
        return results;
    }

    private List<SpotifyPlaylist> performSearch(String query, int limit, int offset) throws Exception {
        List<SpotifyPlaylist> playlists = new ArrayList<>();

        String encodedQuery = URLEncoder.encode(query, "UTF-8");
        String urlString = BASE_URL + "?q=" + encodedQuery +
                "&type=album&limit=" + limit + "&offset=" + offset + "&market=AU";

        Log.d("SpotifyAPI", "➡️ Request URL: " + urlString);

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        Log.d("SpotifyAPI", "⬅️ Status: " + responseCode);

        InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) {
            conn.disconnect();
            throw new Exception("HTTP " + responseCode);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder response = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) {
                response.append(line);
            }

            if (responseCode >= 400) {
                throw new Exception("HTTP " + responseCode + ": " + response.toString());
            }

            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONObject albumsObj = jsonResponse.getJSONObject("albums");
            JSONArray items = albumsObj.getJSONArray("items");

            int nullCount = 0;
            for (int i = 0; i < items.length(); i++) {
                // Skip null items
                if (items.isNull(i)) {
                    nullCount++;
                    continue;
                }

                JSONObject item = items.getJSONObject(i);

                // Validate essential fields before creating playlist object
                String id = item.optString("id", "");
                if (id.isEmpty()) {
                    nullCount++;
                    continue; // Skip items without valid ID
                }

                String name = item.optString("name", "Unknown");
                String description = item.optString("album_type", "album");

                // Get image URL
                String imageUrl = "";
                JSONArray images = item.optJSONArray("images");
                if (images != null && images.length() > 0) {
                    imageUrl = images.getJSONObject(0).optString("url", "");
                }

                // Get artist name
                String ownerName = "Unknown";
                JSONArray artists = item.optJSONArray("artists");
                if (artists != null && artists.length() > 0) {
                    JSONObject artist = artists.getJSONObject(0);
                    ownerName = artist.optString("name", "Unknown");
                }

                // Get total tracks
                int totalTracks = item.optInt("total_tracks", 0);

                // Get external URL
                String externalUrl = "";
                JSONObject urls = item.optJSONObject("external_urls");
                if (urls != null) {
                    externalUrl = urls.optString("spotify", "");
                }

                // Only add if we have a valid external URL
                if (!externalUrl.isEmpty()) {
                    playlists.add(new SpotifyPlaylist(id, name, description, imageUrl,
                            ownerName, totalTracks, externalUrl));
                } else {
                    nullCount++;
                }
            }

            if (nullCount > 0) {
                Log.d("SpotifyAPI", "Skipped " + nullCount + " null/invalid items in this batch");
            }

        } finally {
            conn.disconnect();
        }

        return playlists;
    }

    // Clean up resources when done
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}