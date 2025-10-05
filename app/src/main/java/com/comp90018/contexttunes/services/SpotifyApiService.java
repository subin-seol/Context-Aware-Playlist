package com.comp90018.contexttunes.services;

// SpotifyApiService.java
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpotifyApiService {

    private static final String BASE_URL = "https://api.spotify.com/v1/search";
    private String accessToken;
    private ExecutorService executorService;
    private Handler mainHandler;

    public SpotifyApiService(String accessToken) {
        this.accessToken = accessToken;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // Callback interface for async results
    public interface PlaylistCallback {
        void onSuccess(List<Playlist> playlists);
        void onError(String error);
    }

    // Search for playlists
    public void searchPlaylists(String query, int limit, PlaylistCallback callback) {
        executorService.execute(() -> {
            try {
                List<Playlist> playlists = performSearch(query, limit);
                mainHandler.post(() -> callback.onSuccess(playlists));
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                mainHandler.post(() -> callback.onError(errorMsg));
            }
        });
    }

    private List<Playlist> performSearch(String query, int limit) throws Exception {
        List<Playlist> playlists = new ArrayList<>();

        String encodedQuery = URLEncoder.encode(query, "UTF-8");
        String urlString = BASE_URL + "?q=" + encodedQuery +
                "&type=playlist&limit=" + limit;

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");

        int responseCode = conn.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONObject playlistsObj = jsonResponse.getJSONObject("playlists");
            JSONArray items = playlistsObj.getJSONArray("items");

            for (int i = 0; i < items.length(); i++) {
                try {
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

                    playlists.add(new Playlist(id, name, description, imageUrl,
                            ownerName, totalTracks, externalUrl));
                } catch (Exception e) {
                    // Skip this playlist if there's an error parsing it
                    continue;
                }
            }
        } else {
            throw new Exception("HTTP Error: " + responseCode);
        }

        conn.disconnect();
        return playlists;
    }

    // Clean up resources when done
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    // Playlist model class
    public static class Playlist {
        public String id;
        public String name;
        public String description;
        public String imageUrl;
        public String ownerName;
        public int totalTracks;
        public String externalUrl;

        public Playlist(String id, String name, String description, String imageUrl,
                        String ownerName, int totalTracks, String externalUrl) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.imageUrl = imageUrl;
            this.ownerName = ownerName;
            this.totalTracks = totalTracks;
            this.externalUrl = externalUrl;
        }
    }
}