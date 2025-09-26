package com.comp90018.contexttunes.ui.playlist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.comp90018.contexttunes.R;
import com.comp90018.contexttunes.utils.AppEvents;

public class PlaylistFragment extends Fragment {

    // Cached last context (optional fields for your recommender)
    private float  lastSpeedKmh   = 0f;
    @Nullable private Float  lastCadenceSpm = null;
    @Nullable private String lastActivity   = null;   // still|walking|running
    @Nullable private Integer lastTargetBpm = null;
    @Nullable private String lastSource     = null;   // gps|steps
    @Nullable private String lastSpeedTag   = null;   // STILL|WALK|RUN|WHEELS

    private final BroadcastReceiver speedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (!AppEvents.ACTION_SPEED_UPDATE.equals(intent.getAction())) return;

            lastSpeedKmh   = intent.getFloatExtra(AppEvents.EXTRA_SPEED_KMH, 0f);
            lastCadenceSpm = intent.hasExtra(AppEvents.EXTRA_CADENCE_SPM)
                    ? intent.getFloatExtra(AppEvents.EXTRA_CADENCE_SPM, 0f) : null;
            lastActivity   = intent.getStringExtra(AppEvents.EXTRA_ACTIVITY);
            lastTargetBpm  = intent.getIntExtra(AppEvents.EXTRA_TARGET_BPM, 0);
            lastSource     = intent.getStringExtra(AppEvents.EXTRA_SOURCE);
            lastSpeedTag   = intent.getStringExtra(AppEvents.EXTRA_SPEED_TAG);

            // TODO: feed into your recommender / update UI
            // viewModel.onSpeedContext(lastSpeedTag, lastTargetBpm, lastSpeedKmh, lastCadenceSpm, lastSource);
        }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlist, container, false);
    }

    @Override public void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(AppEvents.ACTION_SPEED_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) {
            requireContext().registerReceiver(speedReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(speedReceiver, f);
        }
    }

    @Override public void onStop() {
        super.onStop();
        requireContext().unregisterReceiver(speedReceiver);
    }
}
