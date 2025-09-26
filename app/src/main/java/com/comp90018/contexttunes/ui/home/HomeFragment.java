package com.comp90018.contexttunes.ui.home;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager; // ADDED: for permission result constant
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat; // KEEP: single import
import androidx.fragment.app.Fragment;

import com.comp90018.contexttunes.databinding.FragmentHomeBinding;
import com.comp90018.contexttunes.services.SpeedSensorService;
import com.comp90018.contexttunes.utils.AppEvents;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    // Guard to avoid being stuck on "Measuring…"
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable measuringTimeout;

    // Activity Result API launcher for runtime permissions
    private ActivityResultLauncher<String[]> permLauncher;

    // Receive speed results from SpeedSensorService
    private final BroadcastReceiver speedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (!AppEvents.ACTION_SPEED_UPDATE.equals(intent.getAction())) return;

            String tag = intent.getStringExtra(AppEvents.EXTRA_SPEED_TAG);
            if (tag == null) tag = "—";

            if (binding != null) {
                // Map internal tag to pretty label
                String pretty = tag.equals("STILL")   ? "Still"   :
                        tag.equals("WALK")    ? "Walking" :
                                tag.equals("RUN")     ? "Running" :
                                        tag.equals("WHEELS")  ? "Wheels"  : tag;
                binding.recentTag.setText(pretty);

                // Reset GO button
                binding.btnGo.setEnabled(true);
                binding.btnGo.setText("GO");
            }

            // Cancel fallback timeout
            if (measuringTimeout != null) ui.removeCallbacks(measuringTimeout);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Register permission launcher once
        permLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean fine   = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                    boolean coarse = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (fine || coarse) {
                        startSpeedBurst();
                    } else {
                        resetGoButton();
                        Toast.makeText(requireContext(),
                                "Location permission is required for speed sensing.",
                                Toast.LENGTH_LONG).show();
                    }
                });

        binding.btnGo.setOnClickListener(v -> {
            // Optimistic UI
            binding.btnGo.setEnabled(false);
            binding.btnGo.setText("Measuring…");
            // Reset tag while measuring to avoid stale value
            binding.recentTag.setText("—");

            String[] perms = buildPermissionArray();
            if (!hasPerms(perms)) {
                // Request permissions, continue in callback
                permLauncher.launch(perms);
                return;
            }
            // Already granted → start measurement
            startSpeedBurst();
        });
    }

    private void startSpeedBurst() {
        Intent now = new Intent(requireContext().getApplicationContext(), SpeedSensorService.class);
        now.setAction(AppEvents.ACTION_SPEED_SAMPLE_NOW);
        ContextCompat.startForegroundService(requireContext().getApplicationContext(), now);

        // Fallback: if no broadcast within 8s, reset UI and hint user
        measuringTimeout = () -> {
            resetGoButton();
            Toast.makeText(requireContext(),
                    "No speed result. Check permissions/GPS.",
                    Toast.LENGTH_SHORT).show();
        };
        ui.postDelayed(measuringTimeout, 8000);
    }

    private void resetGoButton() {
        if (binding != null) {
            binding.btnGo.setEnabled(true);
            binding.btnGo.setText("GO");
        }
    }

    // FIXED: use ContextCompat + PackageManager for a consistent check
    private boolean hasPerms(String[] perms) {
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(requireContext(), p)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // Build the permissions we may request (POST_NOTIFICATIONS is optional on 33+)
    private String[] buildPermissionArray() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return list.toArray(new String[0]);
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(AppEvents.ACTION_SPEED_UPDATE);
        // minSdk=33 → always use 3-arg register with flag
        requireContext().registerReceiver(speedReceiver, f, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onStop() {
        try { requireContext().unregisterReceiver(speedReceiver); } catch (Exception ignore) {}
        if (measuringTimeout != null) ui.removeCallbacks(measuringTimeout);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
