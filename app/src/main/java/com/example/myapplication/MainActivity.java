package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.maps.MapsInitializer;
import com.example.myapplication.api.AuthManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private BottomNavigationView bottomNav;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始化认证管理器
        authManager = AuthManager.getInstance(this);
        authManager.init();
        
        // 检查登录状态
        if (!authManager.isLoggedIn()) {
            goToLogin();
            return;
        }
        
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            doLogout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doLogout() {
        authManager.logout();
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
