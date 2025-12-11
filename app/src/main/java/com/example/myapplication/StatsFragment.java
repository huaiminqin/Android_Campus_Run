package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StatsFragment extends Fragment {

    private static final String TAG = "StatsFragment";
    private TextView tvTotalDistance, tvTotalTimes, tvTotalDuration;
    private TextView tvTotalSteps, tvTotalCalories, tvAvgPace;
    private TextView tvWeekDistance, tvWeekTimes, tvWeekCalories;
    private RunDataManager dataManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = null;
        try {
            view = inflater.inflate(R.layout.fragment_stats, container, false);
            dataManager = new RunDataManager(requireContext());
            initViews(view);
        } catch (Exception e) {
            Log.e(TAG, "onCreateView error", e);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            loadStats();
        } catch (Exception e) {
            Log.e(TAG, "onResume error", e);
        }
    }

    private void initViews(View view) {
        if (view == null) return;
        tvTotalDistance = view.findViewById(R.id.tv_total_distance);
        tvTotalTimes = view.findViewById(R.id.tv_total_times);
        tvTotalDuration = view.findViewById(R.id.tv_total_duration);
        tvTotalSteps = view.findViewById(R.id.tv_total_steps);
        tvTotalCalories = view.findViewById(R.id.tv_total_calories);
        tvAvgPace = view.findViewById(R.id.tv_avg_pace);
        tvWeekDistance = view.findViewById(R.id.tv_week_distance);
        tvWeekTimes = view.findViewById(R.id.tv_week_times);
        tvWeekCalories = view.findViewById(R.id.tv_week_calories);
    }

    private void loadStats() {
        if (dataManager == null) return;
        
        List<RunDataManager.RunRecord> records = dataManager.getRecords();
        if (records == null) records = new ArrayList<>();
        
        double totalDist = 0;
        int totalDur = 0;
        int totalSteps = 0;
        int totalCal = 0;
        
        for (RunDataManager.RunRecord r : records) {
            totalDist += r.distance;
            totalDur += r.duration;
            totalSteps += r.steps;
            totalCal += r.calories;
        }
        
        int count = records.size();
        double avgPace = totalDist > 0 ? (totalDur / 60.0) / (totalDist / 1000) : 0;
        
        if (tvTotalDistance != null) tvTotalDistance.setText(String.format(Locale.CHINA, "%.1f", totalDist / 1000));
        if (tvTotalTimes != null) tvTotalTimes.setText(String.valueOf(count));
        if (tvTotalDuration != null) tvTotalDuration.setText(String.format(Locale.CHINA, "%d:%02d", totalDur / 3600, (totalDur % 3600) / 60));
        if (tvTotalSteps != null) tvTotalSteps.setText(String.valueOf(totalSteps));
        if (tvTotalCalories != null) tvTotalCalories.setText(String.valueOf(totalCal));
        
        int paceMin = (int) avgPace;
        int paceSec = (int) ((avgPace - paceMin) * 60);
        if (tvAvgPace != null) tvAvgPace.setText(String.format(Locale.CHINA, "%d:%02d", paceMin, paceSec));
        
        if (tvWeekDistance != null) tvWeekDistance.setText(String.format(Locale.CHINA, "%.1f", totalDist / 1000));
        if (tvWeekTimes != null) tvWeekTimes.setText(String.valueOf(count));
        if (tvWeekCalories != null) tvWeekCalories.setText(String.valueOf(totalCal));
    }
}
