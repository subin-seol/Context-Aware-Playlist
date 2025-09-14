package com.comp90018.contexttunes.ui.playlist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.comp90018.contexttunes.R;
import com.comp90018.contexttunes.services.SpeedSensorService;
import com.comp90018.contexttunes.utils.AppEvents;

public class PlaylistFragment extends Fragment {

    // --- Cached context from SpeedSensorService ---
    private float  lastSpeedKmh   = 0f;
    @Nullable private Float  lastCadenceSpm = null;  // steps/min
    @Nullable private String lastActivity   = null;  // still|walking|running
    @Nullable private Integer lastTargetBpm = null;  // suggested BPM
    @Nullable private String lastSource     = null;  // gps|steps

    private final BroadcastReceiver speedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (!AppEvents.ACTION_SPEED_UPDATE.equals(intent.getAction())) return;

            lastSpeedKmh = intent.getFloatExtra(AppEvents.EXTRA_SPEED_KMH, 0f);

            if (intent.hasExtra(AppEvents.EXTRA_CADENCE_SPM)) {
                lastCadenceSpm = intent.getFloatExtra(AppEvents.EXTRA_CADENCE_SPM, 0f);
            } else {
                lastCadenceSpm = null;
            }

            lastActivity = intent.getStringExtra(AppEvents.EXTRA_ACTIVITY);

            if (intent.hasExtra(AppEvents.EXTRA_TARGET_BPM)) {
                lastTargetBpm = intent.getIntExtra(AppEvents.EXTRA_TARGET_BPM, 0);
            } else {
                lastTargetBpm = null;
            }

            lastSource = intent.getStringExtra(AppEvents.EXTRA_SOURCE);

            // TODO: If you want auto-refresh on every update, call your VM/controller here.
            // viewModel.onSpeedContext(lastSpeedKmh, lastActivity, lastTargetBpm, lastCadenceSpm, lastSource);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlist, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(AppEvents.ACTION_SPEED_UPDATE);
        // Use ContextCompat to register with proper flags across all API levels.
        ContextCompat.registerReceiver(
                requireContext(),
                speedReceiver,
                f,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            requireContext().unregisterReceiver(speedReceiver);
        } catch (IllegalArgumentException ignored) {
            // receiver might already be unregistered in edge cases
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Trigger an immediate 5s sampling burst to refresh context
        Intent now = new Intent(requireContext().getApplicationContext(), SpeedSensorService.class);
        now.setAction(AppEvents.ACTION_SPEED_SAMPLE_NOW);
        ContextCompat.startForegroundService(requireContext().getApplicationContext(), now);
    }

    // Example: call this when you want to use cached context for recommendation
    private void generatePlaylistWithContext() {
        // TODO: call your recommendation entry with:
        // lastSpeedKmh, lastActivity, lastTargetBpm, lastCadenceSpm, lastSource
    }
}
