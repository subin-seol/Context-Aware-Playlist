package com.comp90018.contexttunes.data.api;

import android.os.Handler;
import android.os.Looper;

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
                List<SpotifyPlaylist> playlists = performSearch(query, limit);
                mainHandler.post(() -> callback.onSuccess(playlists));
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                mainHandler.post(() -> callback.onError(errorMsg));
            }
        });
    }

    private List<SpotifyPlaylist> performSearch(String query, int limit) throws Exception {
        List<SpotifyPlaylist> playlists = new ArrayList<>();

        String encodedQuery = URLEncoder.encode(query, "UTF-8");
        String urlString = BASE_URL + "?q=" + encodedQuery +
                "&type=playlist&limit=" + limit;

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) {
            conn.disconnect();
            throw new Exception("HTTP " + responseCode);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder response = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) response.append(line);

            if (responseCode >= 400) throw new Exception("HTTP " + responseCode + ": " + response.toString());

            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONObject playlistsObj = jsonResponse.getJSONObject("playlists");
            JSONArray items = playlistsObj.getJSONArray("items");

            for (int i = 0; i < items.length(); i++) {
                    // Check if item is null
                    if (items.isNull(i)) {
                        continue;
                    }

                    JSONObject item = items.getJSONObject(i);

                    String id = item.optString("id", "");
                    String name = item.optString("name", "Unknown");
                    String description = item.optString("description", "");
                    String imageUrl = "";

                    JSONArray images = item.optJSONArray("images");
                    if (images != null && images.length() > 0) {
                        imageUrl = images.getJSONObject(0).optString("url", "");
                    }

                    String ownerName = "Unknown";
                    if (!item.isNull("owner")) {
                        JSONObject owner = item.getJSONObject("owner");
                        ownerName = owner.optString("display_name", "Unknown");
                    }

                    int totalTracks = 0;
                    if (!item.isNull("tracks")) {
                        JSONObject tracks = item.getJSONObject("tracks");
                        totalTracks = tracks.optInt("total", 0);
                    }

                    String externalUrl = "";
                    if (!item.isNull("external_urls")) {
                        JSONObject urls = item.getJSONObject("external_urls");
                        externalUrl = urls.optString("spotify", "");
                    }

                    playlists.add(new SpotifyPlaylist(id, name, description, imageUrl,
                            ownerName, totalTracks, externalUrl));
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