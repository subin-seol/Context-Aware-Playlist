package com.comp90018.contexttunes.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import com.comp90018.contexttunes.domain.SpotifyPlaylist;

public class PlaylistOpener {

    /** Open a Spotify playlist. Prefer app deep link via ID, otherwise use external web URL. */
    public static void openPlaylist(Context context, SpotifyPlaylist playlist) {
        if (playlist == null) {
            Toast.makeText(context, "Playlist not available", Toast.LENGTH_SHORT).show();
            return;
        }

        final String appUri = (playlist.id != null && !playlist.id.isEmpty())
                ? "spotify:playlist:" + playlist.id
                : null;
        final String webUrl = playlist.externalUrl;

        boolean hasSpotify = isSpotifyInstalled(context);

        if (hasSpotify && appUri != null) {
            // Open directly in Spotify app
            openUri(context, appUri);
            return;
        }

        if (webUrl != null && !webUrl.isEmpty()) {
            // Fallback to browser
            openUri(context, webUrl);
            if (!hasSpotify) {
                Toast.makeText(context, "Opening in browser", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Nothing usable
        Toast.makeText(context, "Playlist link not available", Toast.LENGTH_SHORT).show();
    }

    private static boolean isSpotifyInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo("com.spotify.music", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static void openUri(Context context, String uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show();
        }
    }
}
