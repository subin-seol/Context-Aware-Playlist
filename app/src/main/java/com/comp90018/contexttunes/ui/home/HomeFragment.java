package com.comp90018.contexttunes.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.comp90018.contexttunes.MainActivity;
import com.comp90018.contexttunes.databinding.FragmentHomeBinding;
import com.comp90018.contexttunes.R;

import android.widget.TextView;
import java.util.Locale;
import com.comp90018.contexttunes.data.sensors.LightSensor;
import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;

import com.comp90018.contexttunes.domain.Playlist;
import com.comp90018.contexttunes.utils.PlaylistOpener;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    private LightSensor lightSensor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // username could come from prefs later
        binding.welcomeTitle.setText("Welcome back Alex!");

        // --- LIGHT SENSOR WIRING ---
        lightSensor = new LightSensor(requireContext(), bucket -> {
            if (binding == null) return;
            requireActivity().runOnUiThread(() -> {
                String label;
                switch (bucket) {
                    case DIM:
                        label = "Light: Dim";
                        break;
                    case NORMAL:
                        label = "Light: Normal";
                        break;
                    case BRIGHT:
                        label = "Light: Bright";
                        break;
                    default:
                        label = "Light: N/A";
                }
                binding.lightValue.setText(label);
            });
        });


        // Camera button -> switch to Snap tab
        binding.btnSnap.setOnClickListener(v -> {
            MainActivity act = (MainActivity) requireActivity();
            act.goToHomeTab(); // ensures listener exists
            // directly set selected tab:
            // R.id.nav_snap is your menu id in BottomNavigationView
            ((MainActivity) requireActivity()).selectTab(R.id.nav_snap);
            // simpler: call through MainActivity if you add a helper; for now:
            act.runOnUiThread(() ->
                    ((MainActivity) requireActivity()).selectTab(R.id.nav_snap)

            );
        });

        // GO button -> trigger recommendation
        binding.btnGo.setOnClickListener(v -> {
            // Create a test playlist
            Playlist testPlaylist = new Playlist(
                    "Focus Playlist",
                    "spotify:playlist:37i9dQZF1DX0XUsuxWHRQd", // Example Spotify URI
                    "https://open.spotify.com/playlist/37i9dQZF1DX0XUsuxWHRQd" // Web fallback
            );

            // Test the playlist opener
            PlaylistOpener.openPlaylist(requireContext(), testPlaylist);
        });
    }

    // Small helper to print nicer bucket names
    private String pretty(LightBucket b) {
        if (b == null) return "N/A";
        switch (b) {
            case DIM: return "Dim";
            case NORMAL: return "Normal";
            case BRIGHT: return "Bright";
            default: return "N/A";
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (lightSensor != null) lightSensor.start(); // begin receiving updates
    }

    @Override
    public void onStop() {
        if (lightSensor != null) lightSensor.stop(); // stop to save battery
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
