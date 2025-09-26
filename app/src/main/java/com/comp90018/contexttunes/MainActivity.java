package com.comp90018.contexttunes;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.comp90018.contexttunes.data.sensors.LightSensor;
import com.comp90018.contexttunes.databinding.ActivityMainBinding;
import com.comp90018.contexttunes.ui.home.HomeFragment;
import com.comp90018.contexttunes.ui.playlist.PlaylistFragment;
import com.comp90018.contexttunes.ui.snap.SnapFragment;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    // Light sensor instance
    private LightSensor lightSensor;
    private final MutableLiveData<LightBucket> lightBucketLive = new MutableLiveData<>(LightBucket.UNKNOWN);
    //public LiveData<LightBucket> getLightBucketLive() { return lightBucketLive; }

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

        // Initialise LightSensor
        lightSensor = new LightSensor(this, bucket -> {
            Log.d("LightSensor", "Current light bucket = " + bucket);
            lightBucketLive.postValue(bucket);
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (lightSensor != null) lightSensor.start();
    }

    @Override protected void onPause() {
        if (lightSensor != null) lightSensor.stop();
        super.onPause();
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

    public void selectTab(int menuId) {
        if (binding != null) {
            binding.bottomNav.setSelectedItemId(menuId);
        }
    }
}
