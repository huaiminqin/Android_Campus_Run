package com.example.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.maps.model.Polyline;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RunFragment extends Fragment implements AMapLocationListener, SensorEventListener {

    private static final String TAG = "RunFragment";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private MapView mapView;
    private AMap aMap;
    private AMapLocationClient locationClient;
    private Polyline trackPolyline;

    private TextView tvDistance, tvDuration, tvPace, tvSteps, tvCalories;
    private Button btnStart, btnPause, btnStop;
    private ImageView ivCompass;

    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer, stepCounter;
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private float currentDegree = 0f;

    // ========== 计步算法参数 ==========
    private static final float STEP_THRESHOLD = 5.0f;  // 阈值调高，减少误触发
    private static final long MIN_STEP_INTERVAL_MS = 450;  // 最小步间隔250ms
    private float lastMagnitude = 0;
    private long lastStepTimeMs = 0;
    private boolean isRising = false;
    private int stepCountManual = 0;
    
    // ========== 卡尔曼滤波GPS参数 ==========
    private double kalmanLat = 0, kalmanLng = 0;
    private double kalmanVariance = 1;
    private static final double KALMAN_Q = 0.00001;  // 过程噪声
    private boolean kalmanInitialized = false;
    private long lastLocationTime = 0;
    private float lastSpeed = 0;

    private boolean isRunning = false;
    private boolean isPaused = false;
    private long startTime = 0;
    private long pausedDuration = 0;
    private long pauseStartTime = 0;
    private int stepCount = 0;
    private int initialStepCount = -1;
    private boolean useSystemStepCounter = false;  // 强制使用加速度计算法
    private double totalDistance = 0;
    private LatLng lastLocation = null;
    private List<LatLng> trackPoints = new ArrayList<>();

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private RunDataManager dataManager;
    private DatabaseHelper dbHelper;


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

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && !isPaused) {
                    updateDuration();
                    handler.postDelayed(this, 1000);
                }
            }
        };
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
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        
        // 强制使用加速度计算法，不用系统计步器（系统计步器需要真正走路，晃动不计步）
        useSystemStepCounter = false;
        Log.d(TAG, "Force using accelerometer step detection");
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
        if (allGranted) {
            initLocation();
        } else {
            requestPermissions(permissions, PERMISSION_REQUEST_CODE);
        }
    }

    private void initLocation() {
        try {
            locationClient = new AMapLocationClient(requireContext());
            locationClient.setLocationListener(this);
            AMapLocationClientOption option = new AMapLocationClientOption();
            option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            option.setInterval(1000);  // 1秒定位一次，提高轨迹精度
            option.setNeedAddress(false);
            locationClient.setLocationOption(option);
            locationClient.startLocation();
        } catch (Exception e) {
            Log.e(TAG, "Location init failed", e);
        }
    }

    private void startRunning() {
        isRunning = true;
        isPaused = false;
        startTime = System.currentTimeMillis();
        pausedDuration = 0;
        totalDistance = 0;
        stepCount = 0;
        stepCountManual = 0;
        initialStepCount = -1;
        trackPoints.clear();
        lastLocation = null;
        
        // 重置卡尔曼滤波
        kalmanInitialized = false;
        kalmanVariance = 1;
        
        // 重置计步参数
        lastStepTimeMs = 0;
        lastMagnitude = 0;
        isRising = false;
        
        // 只清除轨迹线，不清除定位蓝点
        if (trackPolyline != null) {
            trackPolyline.remove();
            trackPolyline = null;
        }

        btnStart.setVisibility(View.GONE);
        btnPause.setVisibility(View.VISIBLE);
        btnStop.setVisibility(View.VISIBLE);
        btnPause.setText("暂停");

        handler.post(timerRunnable);
        Toast.makeText(requireContext(), "开始跑步!", Toast.LENGTH_SHORT).show();
    }

    private void togglePause() {
        if (isPaused) {
            isPaused = false;
            pausedDuration += System.currentTimeMillis() - pauseStartTime;
            btnPause.setText("暂停");
            handler.post(timerRunnable);
        } else {
            isPaused = true;
            pauseStartTime = System.currentTimeMillis();
            btnPause.setText("继续");
        }
    }

    private void stopRunning() {
        isRunning = false;
        isPaused = false;
        handler.removeCallbacks(timerRunnable);

        long elapsed = System.currentTimeMillis() - startTime - pausedDuration;
        int duration = (int) (elapsed / 1000);
        int calories = calculateCalories();
        double pace = totalDistance > 0 ? (duration / 60.0) / (totalDistance / 1000) : 0;
        
        StringBuilder trackStr = new StringBuilder();
        for (LatLng p : trackPoints) {
            trackStr.append(p.latitude).append(",").append(p.longitude).append(";");
        }

        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date());
        
        // 保存到SQLite数据库
        long insertId = dbHelper.insertRunningRecord(
            1,  // 默认用户ID
            date,
            totalDistance,
            duration,
            stepCount,
            calories,
            pace,
            trackStr.toString()
        );
        Log.d(TAG, "保存到SQLite, insertId=" + insertId);
        
        // 同时保存到SharedPreferences（兼容旧版本）
        RunDataManager.RunRecord record = new RunDataManager.RunRecord(date, totalDistance, duration, stepCount, calories, pace, trackStr.toString());
        dataManager.saveRecord(record);

        btnStart.setVisibility(View.VISIBLE);
        btnPause.setVisibility(View.GONE);
        btnStop.setVisibility(View.GONE);

        String msg = String.format(Locale.CHINA, "跑步结束!\n距离: %.2f公里\n步数: %d\n消耗: %d千卡", totalDistance / 1000, stepCount, calories);
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();

        tvDistance.setText("0.00");
        tvDuration.setText("00:00:00");
        tvPace.setText("0:00");
        tvSteps.setText("0");
        tvCalories.setText("0");
    }

    private void updateDuration() {
        long elapsed = System.currentTimeMillis() - startTime - pausedDuration;
        int seconds = (int) (elapsed / 1000);
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        tvDuration.setText(String.format(Locale.CHINA, "%02d:%02d:%02d", hours, minutes, secs));
    }

    private int calculateCalories() {
        double weight = 70;
        return (int) (weight * (totalDistance / 1000) * 1.036);
    }


    // ========== 卡尔曼滤波GPS处理 ==========
    @Override
    public void onLocationChanged(AMapLocation location) {
        if (location == null || location.getErrorCode() != 0) return;
        
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        float accuracy = location.getAccuracy();
        float speed = location.getSpeed();
        long currentTime = System.currentTimeMillis();
        
        // 应用卡尔曼滤波
        LatLng filteredLocation = applyKalmanFilter(lat, lng, accuracy, currentTime);
        
        if (aMap != null) {
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(filteredLocation, 17));
        }
        
        if (isRunning && !isPaused) {
            if (lastLocation == null) {
                trackPoints.add(filteredLocation);
                lastLocation = filteredLocation;
                lastLocationTime = currentTime;
                lastSpeed = speed;
                return;
            }
            
            double dist = calculateDistance(lastLocation, filteredLocation);
            long timeDiff = currentTime - lastLocationTime;
            
            // 校园跑距离校验规则
            boolean isValidPoint = validateGpsPoint(dist, accuracy, speed, timeDiff);
            
            if (isValidPoint && dist > 1) {  // 至少移动1米
                totalDistance += dist * 2;  // 距离校正系数
                trackPoints.add(filteredLocation);
                lastLocation = filteredLocation;
                lastLocationTime = currentTime;
                lastSpeed = speed;
                drawTrack();
                updateUI();
            }
        }
    }

    // 卡尔曼滤波实现
    private LatLng applyKalmanFilter(double lat, double lng, float accuracy, long timestamp) {
        if (!kalmanInitialized) {
            kalmanLat = lat;
            kalmanLng = lng;
            kalmanVariance = accuracy * accuracy;
            kalmanInitialized = true;
            return new LatLng(lat, lng);
        }
        
        // 预测步骤
        kalmanVariance += KALMAN_Q;
        
        // 更新步骤
        double measurementVariance = accuracy * accuracy;
        double kalmanGain = kalmanVariance / (kalmanVariance + measurementVariance);
        
        kalmanLat = kalmanLat + kalmanGain * (lat - kalmanLat);
        kalmanLng = kalmanLng + kalmanGain * (lng - kalmanLng);
        kalmanVariance = (1 - kalmanGain) * kalmanVariance;
        
        return new LatLng(kalmanLat, kalmanLng);
    }

    // GPS点有效性校验（校园跑核心算法）
    private boolean validateGpsPoint(double distance, float accuracy, float speed, long timeDiffMs) {
        // 1. 精度过滤：精度>25米的点不可信
        if (accuracy > 25) {
            Log.d(TAG, "GPS filtered: accuracy=" + accuracy);
            return false;
        }
        
        // 2. 速度校验：计算瞬时速度
        double calcSpeed = (timeDiffMs > 0) ? (distance / timeDiffMs * 1000) : 0;  // m/s
        
        // 跑步速度范围：0.5-12 m/s (1.8-43.2 km/h)
        // 正常跑步2-6 m/s，冲刺可达10m/s
        if (calcSpeed > 12) {
            Log.d(TAG, "GPS filtered: speed too fast=" + calcSpeed + "m/s");
            return false;
        }
        
        // 3. 距离跳变过滤：单次移动不超过30米（1秒内）
        double maxDistance = Math.max(30, lastSpeed * timeDiffMs / 1000 * 1.5);
        if (distance > maxDistance) {
            Log.d(TAG, "GPS filtered: distance jump=" + distance);
            return false;
        }
        
        // 4. 静止检测：速度<0.3m/s且距离<2米视为静止
        if (calcSpeed < 0.3 && distance < 2) {
            return false;
        }
        
        return true;
    }

    private double calculateDistance(LatLng p1, LatLng p2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results);
        return results[0];
    }

    private void drawTrack() {
        if (trackPoints.size() > 1 && aMap != null) {
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

    private void updateUI() {
        tvDistance.setText(String.format(Locale.CHINA, "%.2f", totalDistance / 1000));
        tvSteps.setText(String.valueOf(stepCount));
        tvCalories.setText(String.valueOf(calculateCalories()));
        if (totalDistance > 0) {
            long elapsed = System.currentTimeMillis() - startTime - pausedDuration;
            double pace = (elapsed / 60000.0) / (totalDistance / 1000);
            int paceMin = (int) pace;
            int paceSec = (int) ((pace - paceMin) * 60);
            tvPace.setText(String.format(Locale.CHINA, "%d:%02d", paceMin, paceSec));
        }
    }


    // ========== 峰值检测计步算法（校园跑核心） ==========
    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                gravity = event.values.clone();
                updateCompass();
                if (!useSystemStepCounter && isRunning && !isPaused) {
                    detectStepPeakDetection(event);
                }
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                geomagnetic = event.values.clone();
                updateCompass();
                break;
            case Sensor.TYPE_STEP_COUNTER:
                if (useSystemStepCounter && isRunning && !isPaused) {
                    int total = (int) event.values[0];
                    if (initialStepCount < 0) initialStepCount = total;
                    stepCount = total - initialStepCount;
                    tvSteps.setText(String.valueOf(stepCount));
                }
                break;
        }
    }

    // 超灵敏计步算法 - 简单有效
    private void detectStepPeakDetection(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        
        // 计算加速度向量模
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        
        // 计算与重力的差值（检测运动）
        float delta = Math.abs(magnitude - SensorManager.GRAVITY_EARTH);
        
        long currentTimeMs = System.currentTimeMillis();
        
        // 超简单的峰值检测：上升后下降就算一步
        if (!isRising && delta > STEP_THRESHOLD && delta > lastMagnitude) {
            isRising = true;
        } else if (isRising && delta < lastMagnitude) {
            isRising = false;
            // 检查步间隔
            if (currentTimeMs - lastStepTimeMs > MIN_STEP_INTERVAL_MS) {
                stepCountManual++;
                stepCount = stepCountManual;
                lastStepTimeMs = currentTimeMs;
                
                if (tvSteps != null) {
                    tvSteps.setText(String.valueOf(stepCount));
                }
                Log.d(TAG, "Step detected! count=" + stepCount + ", delta=" + delta);
            }
        }
        
        lastMagnitude = delta;
    }

    private void updateCompass() {
        float[] R = new float[9];
        if (SensorManager.getRotationMatrix(R, null, gravity, geomagnetic)) {
            float[] orientation = new float[3];
            SensorManager.getOrientation(R, orientation);
            float azimuth = (float) Math.toDegrees(orientation[0]);
            azimuth = (azimuth + 360) % 360;
            RotateAnimation ra = new RotateAnimation(currentDegree, -azimuth, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            ra.setDuration(200);
            ra.setFillAfter(true);
            ivCompass.startAnimation(ra);
            currentDegree = -azimuth;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onResume() {
        super.onResume();
        try {
            if (mapView != null) mapView.onResume();
            if (sensorManager != null) {
                // 使用SENSOR_DELAY_FASTEST获取更精确的计步数据
                if (accelerometer != null) 
                    sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
                if (magnetometer != null) 
                    sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
                if (stepCounter != null) 
                    sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (locationClient != null) locationClient.startLocation();
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
            if (locationClient != null) locationClient.stopLocation();
        } catch (Exception e) {
            Log.e(TAG, "onPause error", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mapView != null) mapView.onDestroy();
            if (locationClient != null) locationClient.onDestroy();
            handler.removeCallbacks(timerRunnable);
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
                if (r != PackageManager.PERMISSION_GRANTED) { granted = false; break; }
            }
            if (granted) initLocation();
        }
    }
}
