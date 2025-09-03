package com.comp90018.contexttunes;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.comp90018.contexttunes.ui.playlist.PlaylistFragment;
import com.comp90018.contexttunes.ui.snap.SnapFragment;
import com.comp90018.contexttunes.ui.home.HomeFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Find the bottom navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        // Automatically load HomeFragment when app starts
        if (savedInstanceState == null){
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                    new HomeFragment()).commit();
        }

        // Then set Home tab as selected
        bottomNav.setSelectedItemId(R.id.nav_home);

        // Handle clicks
        // Defines behaviour when the user clicks on the bottom nav bar
        bottomNav.setOnItemSelectedListener(item ->{
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

    }


}