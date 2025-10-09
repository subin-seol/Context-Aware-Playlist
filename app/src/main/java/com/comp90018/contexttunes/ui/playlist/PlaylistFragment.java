package com.comp90018.contexttunes.ui.playlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.comp90018.contexttunes.R;
import com.comp90018.contexttunes.domain.Playlist;
import com.comp90018.contexttunes.domain.Recommendation;
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
        List<Recommendation> savedRecommendations = savedPlaylistsManager.getSavedRecommendations();

        // Clear existing views
        savedPlaylistsContainer.removeAllViews();

        if (savedRecommendations.isEmpty()) {
            showEmptyState();
        } else {
            showPlaylistsContent(savedRecommendations);
        }
    }

    private void showEmptyState() {
        playlistScrollView.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.VISIBLE);
    }

    private void showPlaylistsContent(List<Recommendation> savedRecommendations) {
        emptyStateLayout.setVisibility(View.GONE);
        playlistScrollView.setVisibility(View.VISIBLE);

        for (Recommendation recommendation : savedRecommendations) {
            View card = getLayoutInflater().inflate(
                    R.layout.item_saved_playlist_card,
                    savedPlaylistsContainer,
                    false
            );

            TextView playlistName = card.findViewById(R.id.playlistName);
            TextView playlistReason = card.findViewById(R.id.playlistReason);
            MaterialButton btnPlay = card.findViewById(R.id.btnPlay);
            MaterialButton btnSave = card.findViewById(R.id.btnSave);

            playlistName.setText(recommendation.playlist.name);
            playlistReason.setText(recommendation.reason);

            // Play button functionality
            btnPlay.setOnClickListener(v -> PlaylistOpener.openPlaylist(requireContext(), recommendation.playlist));

            // Remove button functionality (bookmark is filled since it's saved)
            btnSave.setOnClickListener(v -> {
                savedPlaylistsManager.unsaveRecommendation(recommendation);
                Toast.makeText(requireContext(), "Playlist removed from saved", Toast.LENGTH_SHORT).show();
                loadSavedPlaylists(); // Refresh the list
            });

            savedPlaylistsContainer.addView(card);
        }
    }
}
