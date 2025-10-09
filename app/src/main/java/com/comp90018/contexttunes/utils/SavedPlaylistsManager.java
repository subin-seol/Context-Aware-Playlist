package com.comp90018.contexttunes.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.comp90018.contexttunes.domain.Playlist;
import com.comp90018.contexttunes.domain.Recommendation;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class SavedPlaylistsManager {
    private static final String PREFS_NAME = "saved_playlists";
    private static final String KEY_SAVED_RECOMMENDATIONS = "recommendations";

    private final SharedPreferences prefs;
    private final Gson gson;

    public SavedPlaylistsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void saveRecommendation(Recommendation recommendation) {
        List<Recommendation> savedRecommendations = getSavedRecommendations();

        // Check if recommendation is already saved (by playlist name and URI)
        boolean alreadySaved = savedRecommendations.stream()
                .anyMatch(r -> r.playlist.name.equals(recommendation.playlist.name) &&
                              r.playlist.spotifyUri.equals(recommendation.playlist.spotifyUri));

        if (!alreadySaved) {
            savedRecommendations.add(recommendation);
            saveRecommendations(savedRecommendations);
        }
    }

    public void unsaveRecommendation(Recommendation recommendation) {
        List<Recommendation> savedRecommendations = getSavedRecommendations();
        savedRecommendations.removeIf(r -> r.playlist.name.equals(recommendation.playlist.name) &&
                                          r.playlist.spotifyUri.equals(recommendation.playlist.spotifyUri));
        saveRecommendations(savedRecommendations);
    }

    public boolean isRecommendationSaved(Recommendation recommendation) {
        List<Recommendation> savedRecommendations = getSavedRecommendations();
        return savedRecommendations.stream()
                .anyMatch(r -> r.playlist.name.equals(recommendation.playlist.name) &&
                              r.playlist.spotifyUri.equals(recommendation.playlist.spotifyUri));
    }

    // Legacy methods for backward compatibility with existing playlist-based code
    public void savePlaylist(Playlist playlist) {
        // Create a basic recommendation with empty reason for backward compatibility
        Recommendation rec = new Recommendation(playlist, "Saved playlist");
        saveRecommendation(rec);
    }

    public void unsavePlaylist(Playlist playlist) {
        List<Recommendation> savedRecommendations = getSavedRecommendations();
        savedRecommendations.removeIf(r -> r.playlist.name.equals(playlist.name) &&
                                          r.playlist.spotifyUri.equals(playlist.spotifyUri));
        saveRecommendations(savedRecommendations);
    }

    public boolean isPlaylistSaved(Playlist playlist) {
        List<Recommendation> savedRecommendations = getSavedRecommendations();
        return savedRecommendations.stream()
                .anyMatch(r -> r.playlist.name.equals(playlist.name) &&
                              r.playlist.spotifyUri.equals(playlist.spotifyUri));
    }

    public List<Playlist> getSavedPlaylists() {
        List<Recommendation> savedRecommendations = getSavedRecommendations();
        List<Playlist> playlists = new ArrayList<>();
        for (Recommendation rec : savedRecommendations) {
            playlists.add(rec.playlist);
        }
        return playlists;
    }

    public List<Recommendation> getSavedRecommendations() {
        String json = prefs.getString(KEY_SAVED_RECOMMENDATIONS, "[]");
        Type listType = new TypeToken<List<Recommendation>>(){}.getType();
        List<Recommendation> recommendations = gson.fromJson(json, listType);
        return recommendations != null ? recommendations : new ArrayList<>();
    }

    private void saveRecommendations(List<Recommendation> recommendations) {
        String json = gson.toJson(recommendations);
        prefs.edit().putString(KEY_SAVED_RECOMMENDATIONS, json).apply();
    }
}
