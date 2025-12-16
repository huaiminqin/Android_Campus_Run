package com.example.myapplication;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Locale;

public class ToolsFragment extends Fragment implements SensorEventListener {

    private static final String TAG = "ToolsFragment";
    private EditText etCalcDistance, etCalcTime, etWeight, etCalDistance;
    private TextView tvCalcResult, tvCaloriesResult, tvDirection, tvSyncStatus;
    private ImageView ivCompass;
    private Button btnSync;

    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer;
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private float currentDegree = 0f;
    
    private SyncService syncService;
    private boolean syncServiceBound = false;
    private DatabaseHelper dbHelper;

    private ServiceConnection syncConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SyncService.SyncBinder binder = (SyncService.SyncBinder) service;
            syncService = binder.getService();
            syncServiceBound = true;
            updateSyncStatus();
            
            syncService.setSyncListener(new SyncService.SyncListener() {
                @Override
                public void onSyncStarted() {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (btnSync != null) btnSync.setEnabled(false);
                            if (tvSyncStatus != null) tvSyncStatus.setText("同步中...");
                        });
                    }
                }

                @Override
                public void onSyncProgress(int completed, int total) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (tvSyncStatus != null) {
                                tvSyncStatus.setText(String.format(Locale.CHINA, 
                                    "同步中 %d/%d", completed, total));
                            }
                        });
                    }
                }

                @Override
                public void onSyncCompleted(int successCount, int failCount) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (btnSync != null) btnSync.setEnabled(true);
                            updateSyncStatus();
                            Toast.makeText(requireContext(), 
                                String.format(Locale.CHINA, "同步完成: %d成功, %d失败", 
                                    successCount, failCount), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            syncServiceBound = false;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = null;
        try {
            view = inflater.inflate(R.layout.fragment_tools, container, false);
            dbHelper = DatabaseHelper.getInstance(requireContext());
            initViews(view);
            initSensors();
        } catch (Exception e) {
            Log.e(TAG, "onCreateView error", e);
        }
        return view;
    }

    private void initViews(View view) {
        if (view == null) return;
        try {
            // 配速计算器
            etCalcDistance = view.findViewById(R.id.et_calc_distance);
            etCalcTime = view.findViewById(R.id.et_calc_time);
            tvCalcResult = view.findViewById(R.id.tv_calc_result);
            Button btnCalcPace = view.findViewById(R.id.btn_calc_pace);
            if (btnCalcPace != null) btnCalcPace.setOnClickListener(v -> calculatePace());

            // 卡路里计算器
            etWeight = view.findViewById(R.id.et_weight);
            etCalDistance = view.findViewById(R.id.et_cal_distance);
            tvCaloriesResult = view.findViewById(R.id.tv_calories_result);
            Button btnCalcCalories = view.findViewById(R.id.btn_calc_calories);
            if (btnCalcCalories != null) btnCalcCalories.setOnClickListener(v -> calculateCalories());

            // 指南针
            ivCompass = view.findViewById(R.id.iv_compass);
            tvDirection = view.findViewById(R.id.tv_direction);

            // 调查问卷入口
            Button btnOpenSurvey = view.findViewById(R.id.btn_open_survey);
            if (btnOpenSurvey != null) {
                btnOpenSurvey.setOnClickListener(v -> {
                    try {
                        Intent intent = new Intent(requireContext(), SurveyActivity.class);
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Open survey error", e);
                    }
                });
            }
            
            // 同步状态
            tvSyncStatus = view.findViewById(R.id.tv_sync_status);
            btnSync = view.findViewById(R.id.btn_sync);
            if (btnSync != null) {
                btnSync.setOnClickListener(v -> doSync());
            }
        } catch (Exception e) {
            Log.e(TAG, "initViews error", e);
        }
    }
    
    private void updateSyncStatus() {
        if (tvSyncStatus != null && dbHelper != null) {
            int count = dbHelper.getUnsyncedCount();
            if (count > 0) {
                tvSyncStatus.setText(String.format(Locale.CHINA, "待同步: %d条记录", count));
            } else {
                tvSyncStatus.setText("已全部同步");
            }
        }
    }
    
    private void doSync() {
        if (syncServiceBound && syncService != null) {
            syncService.syncPendingRecords();
        } else {
            Toast.makeText(requireContext(), "同步服务未就绪", Toast.LENGTH_SHORT).show();
        }
    }

    private void initSensors() {
        try {
            sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            }
        } catch (Exception e) {
            Log.e(TAG, "initSensors error", e);
        }
    }

    private void calculatePace() {
        String distStr = etCalcDistance.getText().toString().trim();
        String timeStr = etCalcTime.getText().toString().trim();

        if (distStr.isEmpty() || timeStr.isEmpty()) {
            Toast.makeText(requireContext(), "请输入距离和时间", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double distance = Double.parseDouble(distStr);
            double time = Double.parseDouble(timeStr);

            if (distance <= 0) {
                Toast.makeText(requireContext(), "距离必须大于0", Toast.LENGTH_SHORT).show();
                return;
            }

            double pace = time / distance;
            int paceMin = (int) pace;
            int paceSec = (int) ((pace - paceMin) * 60);

            tvCalcResult.setText(String.format(Locale.CHINA, "配速: %d'%02d\"", paceMin, paceSec));
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "请输入有效数字", Toast.LENGTH_SHORT).show();
        }
    }

    private void calculateCalories() {
        String weightStr = etWeight.getText().toString().trim();
        String distStr = etCalDistance.getText().toString().trim();

        if (weightStr.isEmpty() || distStr.isEmpty()) {
            Toast.makeText(requireContext(), "请输入体重和距离", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double weight = Double.parseDouble(weightStr);
            double distance = Double.parseDouble(distStr);
            double calories = weight * distance * 1.036;

            tvCaloriesResult.setText(String.format(Locale.CHINA, "消耗: %.0f 千卡", calories));
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "请输入有效数字", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values.clone();
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values.clone();
        }
        updateCompass();
    }

    private void updateCompass() {
        float[] R = new float[9];
        if (SensorManager.getRotationMatrix(R, null, gravity, geomagnetic)) {
            float[] orientation = new float[3];
            SensorManager.getOrientation(R, orientation);
            float azimuth = (float) Math.toDegrees(orientation[0]);
            azimuth = (azimuth + 360) % 360;

            RotateAnimation ra = new RotateAnimation(
                currentDegree, -azimuth,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
            ra.setDuration(200);
            ra.setFillAfter(true);
            ivCompass.startAnimation(ra);
            currentDegree = -azimuth;

            String direction = getDirection(azimuth);
            tvDirection.setText(String.format(Locale.CHINA, "%s %.0f°", direction, azimuth));
        }
    }

    private String getDirection(float degree) {
        if (degree >= 337.5 || degree < 22.5) return "北";
        else if (degree < 67.5) return "东北";
        else if (degree < 112.5) return "东";
        else if (degree < 157.5) return "东南";
        else if (degree < 202.5) return "南";
        else if (degree < 247.5) return "西南";
        else if (degree < 292.5) return "西";
        else return "西北";
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onResume() {
        super.onResume();
        try {
            if (sensorManager != null) {
                if (accelerometer != null) 
                    sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
                if (magnetometer != null) 
                    sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
            }
            // 绑定同步服务
            Intent intent = new Intent(requireContext(), SyncService.class);
            requireContext().bindService(intent, syncConnection, Context.BIND_AUTO_CREATE);
            
            updateSyncStatus();
        } catch (Exception e) {
            Log.e(TAG, "onResume error", e);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
            // 解绑同步服务
            if (syncServiceBound) {
                requireContext().unbindService(syncConnection);
                syncServiceBound = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "onPause error", e);
        }
    }
}
