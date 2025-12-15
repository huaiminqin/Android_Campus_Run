package com.example.myapplication;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;

import java.util.List;
import java.util.Locale;

public class RunFragment extends Fragment implements SensorEventListener {

    private static final String TAG = "RunFragment";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private MapView mapView;
    private AMap aMap;
    private Polyline trackPolyline;

    private TextView tvDistance, tvDuration, tvPace, tvSteps, tvCalories;
    private Button btnStart, btnPause, btnStop;
    private ImageView ivCompass;

    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer;
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private float currentDegree = 0f;



    private RunDataManager dataManager;
    private DatabaseHelper dbHelper;
    
    // 服务绑定
    private RunningService runningService;
    private boolean serviceBound = false;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RunningService.RunningBinder binder = (RunningService.RunningBinder) service;
            runningService = binder.getService();
            serviceBound = true;
            
            runningService.setListener(new RunningService.OnRunningUpdateListener() {
                @Override
                public void onTimeUpdate(long elapsedSeconds) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            int hours = (int) (elapsedSeconds / 3600);
                            int minutes = (int) ((elapsedSeconds % 3600) / 60);
                            int secs = (int) (elapsedSeconds % 60);
                            tvDuration.setText(String.format(Locale.CHINA, "%02d:%02d:%02d", hours, minutes, secs));
                            updateCaloriesAndPace();
                        });
                    }
                }

                @Override
                public void onDistanceUpdate(double distance) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            tvDistance.setText(String.format(Locale.CHINA, "%.2f", distance / 1000));
                            updateCaloriesAndPace();
                        });
                    }
                }

                @Override
                public void onLocationUpdate(LatLng location, List<LatLng> track) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (aMap != null) {
                                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 17));
                            }
                            drawTrack(track);
                        });
                    }
                }
            });
            
            // 恢复UI状态
            restoreUIState();
            
            // 立即更新一次步数显示
            if (runningService.isRunning()) {
                tvSteps.setText(String.valueOf(runningService.getStepCount()));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            runningService = null;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_run, container, false);
        
        dataManager = new RunDataManager(requireContext());
        dbHelper = DatabaseHelper.getInstance(requireContext());
        initPrivacy();
        initViews(view);
        initMap(view, savedInstanceState);
        initSensors();
        checkPermissions();
        
        return view;
    }

    private void initPrivacy() {
        try {
            AMapLocationClient.updatePrivacyShow(requireContext(), true, true);
            AMapLocationClient.updatePrivacyAgree(requireContext(), true);
            MapsInitializer.updatePrivacyShow(requireContext(), true, true);
            MapsInitializer.updatePrivacyAgree(requireContext(), true);
        } catch (Exception e) {
            Log.e(TAG, "Privacy init failed", e);
        }
    }

    private void initViews(View view) {
        tvDistance = view.findViewById(R.id.tv_distance);
        tvDuration = view.findViewById(R.id.tv_duration);
        tvPace = view.findViewById(R.id.tv_pace);
        tvSteps = view.findViewById(R.id.tv_steps);
        tvCalories = view.findViewById(R.id.tv_calories);
        btnStart = view.findViewById(R.id.btn_start);
        btnPause = view.findViewById(R.id.btn_pause);
        btnStop = view.findViewById(R.id.btn_stop);
        ivCompass = view.findViewById(R.id.iv_compass);

        btnStart.setOnClickListener(v -> startRunning());
        btnPause.setOnClickListener(v -> togglePause());
        btnStop.setOnClickListener(v -> stopRunning());
    }

    private void initMap(View view, Bundle savedInstanceState) {
        try {
            mapView = view.findViewById(R.id.map_view);
            if (mapView != null) {
                mapView.onCreate(savedInstanceState);
                aMap = mapView.getMap();
                if (aMap != null) {
                    aMap.setMyLocationEnabled(true);
                    aMap.getUiSettings().setZoomControlsEnabled(false);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Map init failed", e);
        }
    }

    private void initSensors() {
        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    private void checkPermissions() {
        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        boolean allGranted = true;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (!allGranted) {
            requestPermissions(permissions, PERMISSION_REQUEST_CODE);
        }
    }

    private void startRunning() {
        // 启动前台服务
        Intent serviceIntent = new Intent(requireContext(), RunningService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent);
        } else {
            requireContext().startService(serviceIntent);
        }
        requireContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        // 清除轨迹线
        if (trackPolyline != null) {
            trackPolyline.remove();
            trackPolyline = null;
        }

        btnStart.setVisibility(View.GONE);
        btnPause.setVisibility(View.VISIBLE);
        btnStop.setVisibility(View.VISIBLE);
        btnPause.setText("暂停");

        // 延迟启动服务中的跑步
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (serviceBound && runningService != null) {
                runningService.startRunning();
            }
        }, 500);

        Toast.makeText(requireContext(), "开始跑步!", Toast.LENGTH_SHORT).show();
    }

    private void togglePause() {
        if (serviceBound && runningService != null) {
            runningService.togglePause();
            if (runningService.isPaused()) {
                btnPause.setText("继续");
            } else {
                btnPause.setText("暂停");
            }
        }
    }

    private void stopRunning() {
        if (serviceBound && runningService != null) {
            RunningService.RunResult result = runningService.stopRunning();
            
            // 保存到SQLite数据库（使用服务中的步数）
            long insertId = dbHelper.insertRunningRecord(
                1, result.date, result.distance, result.duration,
                result.steps, result.calories, result.pace, result.track
            );
            Log.d(TAG, "保存到SQLite, insertId=" + insertId);
            
            // 同时保存到SharedPreferences
            RunDataManager.RunRecord record = new RunDataManager.RunRecord(
                result.date, result.distance, result.duration,
                result.steps, result.calories, result.pace, result.track
            );
            dataManager.saveRecord(record);

            String msg = String.format(Locale.CHINA, "跑步结束!\n距离: %.2f公里\n步数: %d\n消耗: %d千卡",
                    result.distance / 1000, result.steps, result.calories);
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
        }
        
        // 解绑服务
        if (serviceBound) {
            requireContext().unbindService(serviceConnection);
            serviceBound = false;
        }

        btnStart.setVisibility(View.VISIBLE);
        btnPause.setVisibility(View.GONE);
        btnStop.setVisibility(View.GONE);

        tvDistance.setText("0.00");
        tvDuration.setText("00:00:00");
        tvPace.setText("0:00");
        tvSteps.setText("0");
        tvCalories.setText("0");
    }

    private void restoreUIState() {
        if (runningService != null && runningService.isRunning()) {
            btnStart.setVisibility(View.GONE);
            btnPause.setVisibility(View.VISIBLE);
            btnStop.setVisibility(View.VISIBLE);
            btnPause.setText(runningService.isPaused() ? "继续" : "暂停");
            
            // 恢复显示数据（从服务获取）
            double dist = runningService.getTotalDistance();
            int steps = runningService.getStepCount();
            tvDistance.setText(String.format(Locale.CHINA, "%.2f", dist / 1000));
            tvSteps.setText(String.valueOf(steps));
            
            // 恢复卡路里和配速
            updateCaloriesAndPace();
            
            // 恢复轨迹
            List<LatLng> track = runningService.getTrackPoints();
            if (track != null && !track.isEmpty()) {
                drawTrack(track);
                if (aMap != null) {
                    aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(track.get(track.size() - 1), 17));
                }
            }
        }
    }

    private void updateCaloriesAndPace() {
        if (runningService != null) {
            double dist = runningService.getTotalDistance();
            double weight = 70;
            int calories = (int) (weight * (dist / 1000) * 1.036);
            tvCalories.setText(String.valueOf(calories));
            
            if (dist > 0) {
                long elapsed = System.currentTimeMillis() - runningService.getStartTime() - runningService.getPausedDuration();
                double pace = (elapsed / 60000.0) / (dist / 1000);
                int paceMin = (int) pace;
                int paceSec = (int) ((pace - paceMin) * 60);
                tvPace.setText(String.format(Locale.CHINA, "%d:%02d", paceMin, paceSec));
            }
        }
    }

    private void drawTrack(List<LatLng> trackPoints) {
        if (trackPoints != null && trackPoints.size() > 1 && aMap != null) {
            if (trackPolyline != null) {
                trackPolyline.setPoints(trackPoints);
            } else {
                trackPolyline = aMap.addPolyline(new PolylineOptions()
                        .addAll(trackPoints)
                        .width(14)
                        .color(Color.argb(200, 220, 50, 50))
                        .geodesic(true));
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                gravity = event.values.clone();
                updateCompass();
                // 更新步数显示（从服务获取）
                if (serviceBound && runningService != null && runningService.isRunning()) {
                    tvSteps.setText(String.valueOf(runningService.getStepCount()));
                }
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                geomagnetic = event.values.clone();
                updateCompass();
                break;
        }
    }

    private void updateCompass() {
        float[] R = new float[9];
        if (SensorManager.getRotationMatrix(R, null, gravity, geomagnetic)) {
            float[] orientation = new float[3];
            SensorManager.getOrientation(R, orientation);
            float azimuth = (float) Math.toDegrees(orientation[0]);
            azimuth = (azimuth + 360) % 360;
            RotateAnimation ra = new RotateAnimation(currentDegree, -azimuth,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            ra.setDuration(200);
            ra.setFillAfter(true);
            if (ivCompass != null) {
                ivCompass.startAnimation(ra);
            }
            currentDegree = -azimuth;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onStart() {
        super.onStart();
        // 尝试绑定已存在的服务
        Intent serviceIntent = new Intent(requireContext(), RunningService.class);
        requireContext().bindService(serviceIntent, serviceConnection, 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            if (mapView != null) mapView.onResume();
            if (sensorManager != null) {
                if (accelerometer != null)
                    sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
                if (magnetometer != null)
                    sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
            }
        } catch (Exception e) {
            Log.e(TAG, "onResume error", e);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            if (mapView != null) mapView.onPause();
            if (sensorManager != null) sensorManager.unregisterListener(this);
        } catch (Exception e) {
            Log.e(TAG, "onPause error", e);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // 解绑服务但不停止服务
        if (serviceBound) {
            try {
                requireContext().unbindService(serviceConnection);
            } catch (Exception e) {
                Log.e(TAG, "unbindService error", e);
            }
            serviceBound = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mapView != null) mapView.onDestroy();
        } catch (Exception e) {
            Log.e(TAG, "onDestroy error", e);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            if (mapView != null) mapView.onSaveInstanceState(outState);
        } catch (Exception e) {
            Log.e(TAG, "onSaveInstanceState error", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean granted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (!granted) {
                Toast.makeText(requireContext(), "需要位置权限才能记录跑步轨迹", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
