package com.comp90018.contexttunes.ui.playlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.comp90018.contexttunes.R;
import com.comp90018.contexttunes.domain.SpotifyPlaylist;
import com.comp90018.contexttunes.utils.PlaylistOpener;
import com.comp90018.contexttunes.utils.SavedPlaylistsManager;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class PlaylistFragment extends Fragment {

    private LinearLayout savedPlaylistsContainer;
    private ScrollView playlistScrollView;
    private LinearLayout emptyStateLayout;
    private SavedPlaylistsManager savedPlaylistsManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate layout
        return inflater.inflate(R.layout.fragment_playlist, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        savedPlaylistsContainer = view.findViewById(R.id.savedPlaylistsContainer);
        playlistScrollView = view.findViewById(R.id.playlistScrollView);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);

        savedPlaylistsManager = new SavedPlaylistsManager(requireContext());

        loadSavedPlaylists();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh the playlist display when returning to this tab
        loadSavedPlaylists();
    }

    private void loadSavedPlaylists() {
        List<SpotifyPlaylist> savedPlaylists = savedPlaylistsManager.getSavedSpotifyPlaylists();

        // Clear existing views
        savedPlaylistsContainer.removeAllViews();

        if (savedPlaylists.isEmpty()) {
            showEmptyState();
        } else {
            showPlaylistsContent(savedPlaylists);
        }
    }

    private void showEmptyState() {
        playlistScrollView.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.VISIBLE);
    }

    private void showPlaylistsContent(List<SpotifyPlaylist> savedPlaylists) {
        emptyStateLayout.setVisibility(View.GONE);
        playlistScrollView.setVisibility(View.VISIBLE);

        for (SpotifyPlaylist playlist : savedPlaylists) {
            View card = getLayoutInflater().inflate(
                    R.layout.item_saved_playlist_card,
                    savedPlaylistsContainer,
                    false
            );

            ImageView img = card.findViewById(R.id.playlistImage);
            TextView playlistName = card.findViewById(R.id.playlistName);
            TextView playlistMeta = card.findViewById(R.id.playlistMeta);
            MaterialButton btnPlay = card.findViewById(R.id.btnPlay);
            MaterialButton btnSave = card.findViewById(R.id.btnSave);

            playlistName.setText(playlist.name);
            playlistMeta.setText(playlist.ownerName + " • " + playlist.totalTracks + " tracks");
            if (playlist.imageUrl != null && !playlist.imageUrl.isEmpty()) {
                Glide.with(requireContext()).load(playlist.imageUrl).into(img);
            }

            // Play button functionality
            btnPlay.setOnClickListener(v -> PlaylistOpener.openPlaylist(requireContext(), playlist));

            // Remove button functionality (bookmark is filled since it's saved)
            btnSave.setOnClickListener(v -> {
                savedPlaylistsManager.unsaveSpotifyPlaylist(playlist);
                Toast.makeText(requireContext(), "Playlist removed from saved", Toast.LENGTH_SHORT).show();
                loadSavedPlaylists(); // Refresh the list
            });

            savedPlaylistsContainer.addView(card);
        }
    }
}
