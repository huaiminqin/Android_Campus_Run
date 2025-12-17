package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.model.LatLng;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RunningService extends Service implements AMapLocationListener, SensorEventListener {
    
    private static final String TAG = "RunningService";

    private static final String CHANNEL_ID = "running_channel";
    private static final int NOTIFICATION_ID = 1001;

    private final IBinder binder = new RunningBinder();
    private AMapLocationClient locationClient;
    
    // 跑步状态
    private boolean isRunning = false;
    private boolean isPaused = false;
    private long startTime = 0;
    private long pausedDuration = 0;
    private long pauseStartTime = 0;
    private int stepCount = 0;
    private double totalDistance = 0;
    private LatLng lastLocation = null;
    private List<LatLng> trackPoints = new ArrayList<>();
    
    // 卡尔曼滤波参数
    private double kalmanLat = 0, kalmanLng = 0;
    private double kalmanVariance = 1;
    private static final double KALMAN_Q = 0.00001;
    private boolean kalmanInitialized = false;
    private long lastLocationTime = 0;
    private float lastSpeed = 0;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private OnRunningUpdateListener listener;
    
    // 计步传感器
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private static final float STEP_THRESHOLD = 5.0f;
    private static final long MIN_STEP_INTERVAL_MS = 450;
    private float lastMagnitude = 0;
    private long lastStepTimeMs = 0;
    private boolean isRising = false;
    
    // WakeLock保持CPU运行
    private PowerManager.WakeLock wakeLock;

    public class RunningBinder extends Binder {
        public RunningService getService() {
            return RunningService.this;
        }
    }

    public interface OnRunningUpdateListener {
        void onTimeUpdate(long elapsedSeconds);
        void onDistanceUpdate(double distance);
        void onLocationUpdate(LatLng location, List<LatLng> track);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initLocation();
        initSensors();
        initWakeLock();
        
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && !isPaused) {
                    long elapsed = (System.currentTimeMillis() - startTime - pausedDuration) / 1000;
                    if (listener != null) {
                        listener.onTimeUpdate(elapsed);
                    }
                    updateNotification();
                    handler.postDelayed(this, 1000);
                }
            }
        };
    }
    
    private void initWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "RunningApp::RunningWakeLock"
            );
        }
    }
    
    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "跑步记录",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("跑步进行中");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void initLocation() {
        try {
            AMapLocationClient.updatePrivacyShow(this, true, true);
            AMapLocationClient.updatePrivacyAgree(this, true);
            locationClient = new AMapLocationClient(this);
            locationClient.setLocationListener(this);
            AMapLocationClientOption option = new AMapLocationClientOption();
            option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            // 减小定位间隔到500ms，提高轨迹实时性和配速计算精度
            option.setInterval(500);
            option.setNeedAddress(false);
            // 启用后台定位
            option.setLocationCacheEnable(false);
            // 启用传感器辅助定位，提高精度
            option.setSensorEnable(true);
            locationClient.setLocationOption(option);
            // 启用后台定位模式
            locationClient.enableBackgroundLocation(NOTIFICATION_ID, createNotification());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY;
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long elapsed = isRunning ? (System.currentTimeMillis() - startTime - pausedDuration) / 1000 : 0;
        String timeStr = String.format(Locale.CHINA, "%02d:%02d:%02d", 
                elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60);
        String distStr = String.format(Locale.CHINA, "%.2f公里", totalDistance / 1000);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("跑步中 " + timeStr)
                .setContentText("距离: " + distStr + " | 步数: " + stepCount)
                .setSmallIcon(R.drawable.ic_run)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }

    public void startRunning() {
        isRunning = true;
        isPaused = false;
        startTime = System.currentTimeMillis();
        pausedDuration = 0;
        totalDistance = 0;
        stepCount = 0;
        trackPoints.clear();
        lastLocation = null;
        kalmanInitialized = false;
        kalmanVariance = 1;
        
        // 重置计步参数
        lastStepTimeMs = 0;
        lastMagnitude = 0;
        isRising = false;
        
        // 重置配速窗口
        resetPaceWindow();
        
        if (locationClient != null) {
            locationClient.startLocation();
        }
        
        // 启动传感器监听
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        }
        
        // 获取WakeLock保持CPU运行
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(10 * 60 * 60 * 1000L); // 最长10小时
            Log.d(TAG, "WakeLock acquired");
        }
        
        handler.post(timerRunnable);
        updateNotification();
    }

    public void pauseRunning() {
        if (!isPaused) {
            isPaused = true;
            pauseStartTime = System.currentTimeMillis();
        }
    }

    public void resumeRunning() {
        if (isPaused) {
            isPaused = false;
            pausedDuration += System.currentTimeMillis() - pauseStartTime;
            handler.post(timerRunnable);
        }
    }

    public void togglePause() {
        if (isPaused) {
            resumeRunning();
        } else {
            pauseRunning();
        }
    }

    public RunResult stopRunning() {
        isRunning = false;
        isPaused = false;
        handler.removeCallbacks(timerRunnable);
        
        if (locationClient != null) {
            locationClient.stopLocation();
        }
        
        // 停止传感器监听
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        
        // 释放WakeLock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock released");
        }

        // 计算时长（确保不为负数）
        long elapsed = System.currentTimeMillis() - startTime - pausedDuration;
        if (elapsed < 0) elapsed = 0;
        int duration = (int) (elapsed / 1000);
        
        Log.d(TAG, "停止跑步: startTime=" + startTime + ", pausedDuration=" + pausedDuration + ", elapsed=" + elapsed + ", duration=" + duration + "s");
        
        int calories = calculateCalories();
        double pace = totalDistance > 0 ? (duration / 60.0) / (totalDistance / 1000) : 0;
        
        StringBuilder trackStr = new StringBuilder();
        for (LatLng p : trackPoints) {
            trackStr.append(p.latitude).append(",").append(p.longitude).append(";");
        }
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date());
        
        stopForeground(true);
        stopSelf();
        
        return new RunResult(date, totalDistance, duration, stepCount, calories, pace, trackStr.toString());
    }

    private int calculateCalories() {
        double weight = 70;
        return (int) (weight * (totalDistance / 1000) * 1.036);
    }

    @Override
    public void onLocationChanged(AMapLocation location) {
        if (location == null || location.getErrorCode() != 0) return;
        if (!isRunning || isPaused) return;
        
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        float accuracy = location.getAccuracy();
        float speed = location.getSpeed(); // GPS提供的速度 (m/s)
        long currentTime = System.currentTimeMillis();
        
        // 使用原始位置进行显示（提高实时性），使用滤波位置计算距离
        LatLng rawLocation = new LatLng(lat, lng);
        
        if (lastLocation == null) {
            // 第一个点：初始化
            trackPoints.add(rawLocation);
            lastLocation = rawLocation;
            lastLocationTime = currentTime;
            lastSpeed = speed;
            // 初始化卡尔曼滤波
            kalmanLat = lat;
            kalmanLng = lng;
            kalmanVariance = accuracy * accuracy;
            kalmanInitialized = true;
            if (listener != null) {
                listener.onLocationUpdate(rawLocation, trackPoints);
            }
            return;
        }
        
        // 计算原始距离（用于距离累计）
        double rawDist = calculateDistance(lastLocation, rawLocation);
        long timeDiff = currentTime - lastLocationTime;
        
        // 使用GPS速度计算距离（更准确）
        double gpsBasedDist = speed * timeDiff / 1000.0; // 转换为米
        
        // 验证GPS点有效性（放宽条件）
        boolean isValidPoint = accuracy < 30 && timeDiff > 0;
        
        // 防止异常跳跃：如果计算距离远大于GPS速度推算的距离，使用GPS速度
        double finalDist = rawDist;
        if (speed > 0.5 && rawDist > gpsBasedDist * 2) {
            // 距离异常，使用GPS速度推算
            finalDist = gpsBasedDist;
        }
        
        // 过滤静止状态的噪声
        if (speed < 0.3 && rawDist < 3) {
            // 几乎静止，不更新
            return;
        }
        
        // 过滤异常高速（超过12m/s约43km/h）
        double calcSpeed = timeDiff > 0 ? (rawDist / timeDiff * 1000) : 0;
        if (calcSpeed > 12) {
            return;
        }
        
        if (isValidPoint) {
            // 始终添加到轨迹（提高轨迹实时性）
            trackPoints.add(rawLocation);
            
            // 累计距离（使用校正后的距离）
            if (finalDist > 0.5) {
                totalDistance += finalDist;
                
                // 使用GPS速度计算实时配速（更准确）
                if (speed > 0.5) {
                    // 配速 = 1000 / speed / 60 (分钟/公里)
                    // speed是m/s，转换为分钟/公里
                    realtimePace = (1000.0 / speed) / 60.0;
                    realtimePace = Math.max(MIN_PACE, Math.min(MAX_PACE, realtimePace));
                }
                
                if (listener != null) {
                    listener.onDistanceUpdate(totalDistance);
                }
            }
            
            lastLocation = rawLocation;
            lastLocationTime = currentTime;
            lastSpeed = speed;
            
            // 更新UI位置
            if (listener != null) {
                listener.onLocationUpdate(rawLocation, trackPoints);
            }
        }
    }
    
    // 实时配速（分钟/公里）- 基于GPS速度计算
    private double realtimePace = 0;
    private static final double MIN_PACE = 2.0;  // 最快2分钟/公里
    private static final double MAX_PACE = 30.0; // 最慢30分钟/公里
    
    public double getRealtimePace() {
        return realtimePace;
    }
    
    /**
     * 重置配速（跑步开始时调用）
     */
    private void resetPaceWindow() {
        realtimePace = 0;
    }

    private LatLng applyKalmanFilter(double lat, double lng, float accuracy, long timestamp) {
        if (!kalmanInitialized) {
            kalmanLat = lat;
            kalmanLng = lng;
            kalmanVariance = accuracy * accuracy;
            kalmanInitialized = true;
            return new LatLng(lat, lng);
        }
        kalmanVariance += KALMAN_Q;
        double measurementVariance = accuracy * accuracy;
        double kalmanGain = kalmanVariance / (kalmanVariance + measurementVariance);
        kalmanLat = kalmanLat + kalmanGain * (lat - kalmanLat);
        kalmanLng = kalmanLng + kalmanGain * (lng - kalmanLng);
        kalmanVariance = (1 - kalmanGain) * kalmanVariance;
        return new LatLng(kalmanLat, kalmanLng);
    }

    private boolean validateGpsPoint(double distance, float accuracy, float speed, long timeDiffMs) {
        if (accuracy > 25) return false;
        double calcSpeed = (timeDiffMs > 0) ? (distance / timeDiffMs * 1000) : 0;
        if (calcSpeed > 12) return false;
        double maxDistance = Math.max(30, lastSpeed * timeDiffMs / 1000 * 1.5);
        if (distance > maxDistance) return false;
        if (calcSpeed < 0.3 && distance < 2) return false;
        return true;
    }

    private double calculateDistance(LatLng p1, LatLng p2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results);
        return results[0];
    }

    public void addSteps(int steps) {
        this.stepCount += steps;
    }

    public void setListener(OnRunningUpdateListener listener) {
        this.listener = listener;
    }
    
    // 传感器计步
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (isRunning && !isPaused) {
                detectStep(event);
            }
        }
    }
    
    private void detectStep(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        float delta = Math.abs(magnitude - SensorManager.GRAVITY_EARTH);
        long currentTimeMs = System.currentTimeMillis();
        
        if (!isRising && delta > STEP_THRESHOLD && delta > lastMagnitude) {
            isRising = true;
        } else if (isRising && delta < lastMagnitude) {
            isRising = false;
            if (currentTimeMs - lastStepTimeMs > MIN_STEP_INTERVAL_MS) {
                stepCount++;
                lastStepTimeMs = currentTimeMs;
                Log.d(TAG, "Step detected in service! count=" + stepCount);
            }
        }
        lastMagnitude = delta;
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // Getters
    public boolean isRunning() { return isRunning; }
    public boolean isPaused() { return isPaused; }
    public long getStartTime() { return startTime; }
    public long getPausedDuration() { return pausedDuration; }
    public int getStepCount() { return stepCount; }
    public double getTotalDistance() { return totalDistance; }
    public List<LatLng> getTrackPoints() { return trackPoints; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerRunnable);
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (locationClient != null) {
            locationClient.stopLocation();
            locationClient.onDestroy();
        }
    }

    public static class RunResult {
        public String date;
        public double distance;
        public int duration;
        public int steps;
        public int calories;
        public double pace;
        public String track;

        public RunResult(String date, double distance, int duration, int steps, int calories, double pace, String track) {
            this.date = date;
            this.distance = distance;
            this.duration = duration;
            this.steps = steps;
            this.calories = calories;
            this.pace = pace;
            this.track = track;
        }
    }
}
