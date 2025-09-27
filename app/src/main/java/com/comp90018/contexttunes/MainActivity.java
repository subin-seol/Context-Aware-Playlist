package com.comp90018.contexttunes;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.comp90018.contexttunes.databinding.ActivityMainBinding;
import com.comp90018.contexttunes.ui.home.HomeFragment;
import com.comp90018.contexttunes.ui.playlist.PlaylistFragment;
import com.comp90018.contexttunes.ui.snap.SnapFragment;

// imports for Activity Result API and permissions/service
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.comp90018.contexttunes.services.SpeedSensorService;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    // Activity Result API launcher for multi-permission request
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Inflate binding instead of using setContentView(R.layout...)
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Use view binding for root view
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Automatically load HomeFragment when app starts
        if (savedInstanceState == null){
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                    new HomeFragment()).commit();
        }

        // Then set Home tab as selected
        binding.bottomNav.setSelectedItemId(R.id.nav_home);

        // Handle clicks
        // Defines behaviour when the user clicks on the bottom nav bar
        binding.bottomNav.setOnItemSelectedListener(item ->{
            Fragment selectedFragment = null;

            // Handle navigation item clicks
            int id = item.getItemId();
            if (id == R.id.nav_home){
                // Go to home
                selectedFragment = new HomeFragment();
            } else if (id == R.id.nav_snap){
                // Go to snap
                selectedFragment = new SnapFragment();
            } else if (id == R.id.nav_playlist){
                // Go to playlist
                selectedFragment = new PlaylistFragment();
            } else if (id == R.id.nav_settings){
                // Go to settings
//                selectedFragment =
            }

            if (selectedFragment != null){
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                        selectedFragment).commit();
                return true;
            }
            return false;
        });

        // register Activity Result launcher to handle permission results
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    // After user action, check essentials again; start service if granted
                    if (hasEssentialPermissions()) {
                        startSpeedServiceIfNotRunning();
                    }
                }
        );

        // kick off permission flow or start service if already granted
        ensurePermissionsAndMaybeStartService();
    }

    public void setBottomNavVisibility(boolean visible) {
        if (binding != null) {
            binding.bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void goToHomeTab() {
        // This triggers the BottomNavigationView listener and loads HomeFragment
        binding.bottomNav.setSelectedItemId(R.id.nav_home);
    }

    // ===== permissions + service helpers =====

    // Build the permission array to request (includes optional POST_NOTIFICATIONS on 33+)
    private String[] buildPermissionArray() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            // foreground service notification visibility
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return perms.toArray(new String[0]);
    }

    // Check essential permissions (location + activity recognition when applicable)
    private boolean hasEssentialPermissions() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean activityOk = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activityOk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return fine && coarse && activityOk;
    }

    // If essentials are missing, request them via Activity Result API; else start service
    private void ensurePermissionsAndMaybeStartService() {
        if (hasEssentialPermissions()) {
            startSpeedServiceIfNotRunning();
        } else {
            permissionLauncher.launch(buildPermissionArray());
        }
    }

    // Start (or keep alive) the foreground SpeedSensorService (idempotent)
    private void startSpeedServiceIfNotRunning() {
        Intent i = new Intent(getApplicationContext(), SpeedSensorService.class);
        ContextCompat.startForegroundService(getApplicationContext(), i);
        // NOTE: PlaylistFragment will still trigger ACTION_SPEED_SAMPLE_NOW on resume for fresh data
    }
}
