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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    private static final String TAG = "HistoryFragment";
    private RecyclerView rvHistory;
    private RunDataManager dataManager;
    private List<RunDataManager.RunRecord> records = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = null;
        try {
            view = inflater.inflate(R.layout.fragment_history, container, false);
            dataManager = new RunDataManager(requireContext());
            rvHistory = view.findViewById(R.id.rv_history);
            if (rvHistory != null) {
                rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
            }
        } catch (Exception e) {
            Log.e(TAG, "onCreateView error", e);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            if (dataManager != null && rvHistory != null) {
                records = dataManager.getRecords();
                if (records == null) records = new ArrayList<>();
                rvHistory.setAdapter(new HistoryAdapter());
            }
        } catch (Exception e) {
            Log.e(TAG, "onResume error", e);
        }
    }

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RunDataManager.RunRecord record = records.get(position);
            holder.tvDate.setText(record.date);
            holder.tvDistance.setText(String.format(Locale.CHINA, "%.2f 公里", record.distance / 1000.0));
            
            int min = record.duration / 60;
            int sec = record.duration % 60;
            holder.tvDuration.setText(String.format(Locale.CHINA, "%d:%02d", min, sec));
            
            int paceMin = (int) record.pace;
            int paceSec = (int) ((record.pace - paceMin) * 60);
            holder.tvPace.setText(String.format(Locale.CHINA, "%d:%02d", paceMin, paceSec));
            
            holder.tvSteps.setText(String.valueOf(record.steps));
            holder.tvCalories.setText(String.valueOf(record.calories));
        }

        @Override
        public int getItemCount() {
            return records != null ? records.size() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDate, tvDistance, tvDuration, tvPace, tvSteps, tvCalories;

            ViewHolder(View itemView) {
                super(itemView);
                tvDate = itemView.findViewById(R.id.tv_date);
                tvDistance = itemView.findViewById(R.id.tv_distance);
                tvDuration = itemView.findViewById(R.id.tv_duration);
                tvPace = itemView.findViewById(R.id.tv_pace);
                tvSteps = itemView.findViewById(R.id.tv_steps);
                tvCalories = itemView.findViewById(R.id.tv_calories);
            }
        }
    }
}
