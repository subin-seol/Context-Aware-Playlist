package com.comp90018.contexttunes.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.comp90018.contexttunes.domain.SpotifyPlaylist;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SavedPlaylistsManager {
    private static final String PREFS_NAME = "saved_playlists";
    private static final String KEY_SAVED_SPOTIFY = "spotify_playlists";

    private final SharedPreferences prefs;
    private final Gson gson;

    public SavedPlaylistsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void saveSpotifyPlaylist(SpotifyPlaylist playlist) {
        if (playlist == null || playlist.id == null) return;
        List<SpotifyPlaylist> saved = getSavedSpotifyPlaylists();
        boolean already = false;
        for (SpotifyPlaylist p : saved) {
            if (Objects.equals(p.id, playlist.id)) { already = true; break; }
        }
        if (!already) {
            saved.add(playlist);
            saveList(saved);
        }
    }

    public void unsaveSpotifyPlaylist(SpotifyPlaylist playlist) {
        if (playlist == null || playlist.id == null) return;
        List<SpotifyPlaylist> saved = getSavedSpotifyPlaylists();
        saved.removeIf(p -> Objects.equals(p.id, playlist.id));
        saveList(saved);
    }

    public boolean isSpotifyPlaylistSaved(SpotifyPlaylist playlist) {
        String id = playlist == null ? null : playlist.id;
        for (SpotifyPlaylist p : getSavedSpotifyPlaylists()) {
            if (Objects.equals(p.id, id)) return true;
        }
        return false;
    }


    public List<SpotifyPlaylist> getSavedSpotifyPlaylists() {
        String json = prefs.getString(KEY_SAVED_SPOTIFY, "[]");
        Type t = new TypeToken<List<SpotifyPlaylist>>(){}.getType();
        List<SpotifyPlaylist> list = gson.fromJson(json, t);
        return list != null ? list : new ArrayList<>();
    }

    private void saveList(List<SpotifyPlaylist> list) {
        String json = gson.toJson(list);
        prefs.edit().putString(KEY_SAVED_SPOTIFY, json).apply();
    }
}
