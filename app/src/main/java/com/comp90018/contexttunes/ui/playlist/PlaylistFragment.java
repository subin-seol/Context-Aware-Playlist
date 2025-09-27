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

/**
 * Receives speed/cadence/activity frames from SpeedSensorService via AppEvents,
 * caches the latest context, and (optionally) triggers playlist generation.
 *
 * Notes:
 *  - Current AppEvents only guarantees: EXTRA_SPEED_KMH, EXTRA_CADENCE_SPM, EXTRA_ACTIVITY.
 *  - We derive "source" (steps/gps) from whether cadence is present.
 *  - We optionally suggest a target BPM from the activity label.
 */
public class PlaylistFragment extends Fragment {

    // --- Cached context from SpeedSensorService ---
    private float  lastSpeedKmh   = 0f;
    @Nullable private Float  lastCadenceSpm = null;   // steps/min (NaN → null)
    @Nullable private String lastActivity   = null;   // "-", still|walking|running|wheels
    @Nullable private Integer lastTargetBpm = null;   // derived by suggestTargetBpm(...)
    @Nullable private String lastSource     = null;   // derived: "steps" if cadence present, else "gps"

    private final BroadcastReceiver speedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (!AppEvents.ACTION_SPEED_UPDATE.equals(intent.getAction())) return;

            // Guaranteed by AppEvents/Service
            lastSpeedKmh = intent.getFloatExtra(AppEvents.EXTRA_SPEED_KMH, 0f);

            float spm = intent.getFloatExtra(AppEvents.EXTRA_CADENCE_SPM, Float.NaN);
            lastCadenceSpm = Float.isNaN(spm) ? null : spm;

            lastActivity = intent.getStringExtra(AppEvents.EXTRA_ACTIVITY);
            if (lastActivity == null || lastActivity.isEmpty()) lastActivity = "-";

            // Derived fields (since the service no longer sends these extras)
            lastSource = (lastCadenceSpm != null) ? "steps" : "gps";
            lastTargetBpm = suggestTargetBpm(lastActivity, lastCadenceSpm, lastSpeedKmh);

            // If you want to refresh UI/VM on every live update, do it here:
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
        // Receive both local + system broadcasts (service sends both).
        IntentFilter f = new IntentFilter(AppEvents.ACTION_SPEED_UPDATE);
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
            // already unregistered, ignore
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Kick a fresh 5s sampling window to get a recent context.
        // IMPORTANT: our SpeedSensorService is not a foreground service; use startService().
        Intent now = new Intent(requireContext().getApplicationContext(), SpeedSensorService.class);
        now.setAction(AppEvents.ACTION_SPEED_SAMPLE_NOW);
        requireContext().getApplicationContext().startService(now);
    }

    // Example hook: call this when you actually build the playlist
    private void generatePlaylistWithContext() {
        // Use these: lastSpeedKmh, lastActivity, lastTargetBpm, lastCadenceSpm, lastSource
    }

    /**
     * Very simple BPM suggestion, purely for UX glue. Tune as you like.
     * If cadence is available, you could lock BPM ~ cadence*2 (common for running)
     * or near cadence for walking; otherwise pick from label buckets.
     */
    @Nullable
    private Integer suggestTargetBpm(@NonNull String activity,
                                     @Nullable Float cadenceSpm,
                                     float speedKmh) {
        // If we have cadence, give it priority (runner often likes BPM≈cadence or 2x cadence)
        if (cadenceSpm != null && cadenceSpm > 0) {
            int bpm = Math.round(cadenceSpm);        // walking: ~100 spm, running: ~160 spm
            bpm = clamp(bpm, 80, 180);
            return bpm;
        }

        // Otherwise, decide by label/speed buckets
        switch (activity) {
            case "still":   return 70;   // chill
            case "walking": return 100;  // light groove
            case "running": return 160;  // upbeat
            case "wheels":  return 130;  // flowy
            default:
                // Fallback by speed if label is "-"
                if (speedKmh <= 0.6f) return 70;
                if (speedKmh <= 7.0f) return 100;
                if (speedKmh <= 15.0f) return 160;
                return 130;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
