package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.maps.MapsInitializer;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始化高德地图隐私协议（必须在setContentView之前）
        try {
            AMapLocationClient.updatePrivacyShow(this, true, true);
            AMapLocationClient.updatePrivacyAgree(this, true);
            MapsInitializer.updatePrivacyShow(this, true, true);
            MapsInitializer.updatePrivacyAgree(this, true);
        } catch (Exception e) {
            Log.e(TAG, "AMap privacy init failed", e);
        }
        
        setContentView(R.layout.activity_main);

        try {
            bottomNav = findViewById(R.id.bottom_nav);
            
            // 默认显示跑步页面
            if (savedInstanceState == null) {
                loadFragment(new RunFragment());
            }

            if (bottomNav != null) {
                bottomNav.setOnItemSelectedListener(item -> {
                    Fragment fragment = null;
                    try {
                        int itemId = item.getItemId();
                        
                        if (itemId == R.id.nav_run) {
                            fragment = new RunFragment();
                        } else if (itemId == R.id.nav_history) {
                            fragment = new HistoryFragment();
                        } else if (itemId == R.id.nav_stats) {
                            fragment = new StatsFragment();
                        } else if (itemId == R.id.nav_tools) {
                            fragment = new ToolsFragment();
                        }
                        
                        if (fragment != null) {
                            loadFragment(fragment);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Navigation error", e);
                    }
                    return true;
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "onCreate error", e);
        }
    }

    private void loadFragment(Fragment fragment) {
        try {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commitAllowingStateLoss();
        } catch (Exception e) {
            Log.e(TAG, "loadFragment error", e);
        }
    }
}
