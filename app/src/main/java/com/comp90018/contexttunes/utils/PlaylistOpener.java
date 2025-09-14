package com.comp90018.contexttunes.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import com.comp90018.contexttunes.domain.Playlist;

public class PlaylistOpener {

    public static void openPlaylist(Context context, Playlist playlist) {
        // Try Spotify app first
        if (isSpotifyInstalled(context)) {
            openInSpotify(context, playlist.spotifyUri);
        } else {
            // Fall back to web browser
            openInBrowser(context, playlist.webUrl);
        }
    }

    private static boolean isSpotifyInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo("com.spotify.music", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static void openInSpotify(Context context, String spotifyUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri));
        context.startActivity(intent);
    }

    private static void openInBrowser(Context context, String webUrl) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
        context.startActivity(intent);
        Toast.makeText(context, "Opening in browser", Toast.LENGTH_SHORT).show();
    }
}