package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.api.ApiCallback;
import com.example.myapplication.api.AuthManager;
import com.example.myapplication.api.RunningRecordApi;
import com.example.myapplication.api.RunningRecordDto;
import com.example.myapplication.api.UserInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    private static final String TAG = "HistoryFragment";
    private static final int DEFAULT_SHOW_COUNT = 3;  // 默认显示3条
    
    private RecyclerView rvHistory;
    private TextView tvEmpty;
    private TextView btnLoadMore;
    private ProgressBar progressBar;
    private DatabaseHelper dbHelper;
    private AuthManager authManager;
    private List<DatabaseHelper.RunningRecord> allRecords = new ArrayList<>();
    private List<DatabaseHelper.RunningRecord> displayRecords = new ArrayList<>();
    private boolean isExpanded = false;
    private HistoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = null;
        try {
            view = inflater.inflate(R.layout.fragment_history, container, false);
            dbHelper = DatabaseHelper.getInstance(requireContext());
            authManager = AuthManager.getInstance(requireContext());
            rvHistory = view.findViewById(R.id.rv_history);
            tvEmpty = view.findViewById(R.id.tv_empty);
            btnLoadMore = view.findViewById(R.id.btn_load_more);
            progressBar = view.findViewById(R.id.progress_bar);
            
            if (rvHistory != null) {
                rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
            }
            
            if (btnLoadMore != null) {
                btnLoadMore.setOnClickListener(v -> toggleExpand());
            }
        } catch (Exception e) {
            Log.e(TAG, "onCreateView error", e);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }
    
    private void loadData() {
        // 先显示本地数据
        loadLocalData();
        // 然后尝试从服务器获取
        fetchFromServer();
    }
    
    private void loadLocalData() {
        try {
            if (dbHelper != null && rvHistory != null) {
                // 用户数据隔离：只加载当前登录用户的记录
                int currentUserId = getCurrentUserId();
                if (currentUserId > 0) {
                    allRecords = dbHelper.getRunningRecordsByUserId(currentUserId);
                } else {
                    allRecords = new ArrayList<>();
                }
                if (allRecords == null) allRecords = new ArrayList<>();
                
                Log.d(TAG, "从SQLite加载用户" + currentUserId + "的记录数: " + allRecords.size());
                
                // 显示空状态提示
                if (tvEmpty != null) {
                    tvEmpty.setVisibility(allRecords.isEmpty() ? View.VISIBLE : View.GONE);
                }
                rvHistory.setVisibility(allRecords.isEmpty() ? View.GONE : View.VISIBLE);
                
                // 更新显示列表
                updateDisplayRecords();
                
                // 显示/隐藏加载更多按钮
                updateLoadMoreButton();
                
                adapter = new HistoryAdapter();
                rvHistory.setAdapter(adapter);
            }
        } catch (Exception e) {
            Log.e(TAG, "loadLocalData error", e);
        }
    }
    
    /**
     * 获取当前登录用户ID
     */
    private int getCurrentUserId() {
        if (authManager != null && authManager.isLoggedIn()) {
            UserInfo user = authManager.getCurrentUser();
            if (user != null && user.getId() != null) {
                return user.getId();
            }
        }
        return -1; // 未登录返回-1
    }
    
    private void fetchFromServer() {
        if (!authManager.isLoggedIn()) {
            return;
        }
        
        UserInfo user = authManager.getCurrentUser();
        Integer userId = user != null ? user.getId() : null;
        
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        
        // 获取足够多的记录以确保同步删除正确（增加到200条）
        RunningRecordApi.getInstance().getRecords(userId, 1, 200, 
            new ApiCallback<RunningRecordApi.PageResponse<RunningRecordDto>>() {
                @Override
                public void onSuccess(RunningRecordApi.PageResponse<RunningRecordDto> data) {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    
                    if (data != null && data.getRecords() != null) {
                        // 收集服务器上的所有记录ID
                        java.util.Set<Integer> serverIds = new java.util.HashSet<>();
                        for (RunningRecordDto dto : data.getRecords()) {
                            if (dto.getId() != null) {
                                serverIds.add(dto.getId());
                            }
                            // 更新本地缓存
                            DatabaseHelper.RunningRecord record = dtoToRecord(dto);
                            dbHelper.insertOrUpdateFromServer(record);
                        }
                        
                        // 删除本地存在但服务器上已删除的记录
                        if (userId != null && !serverIds.isEmpty()) {
                            int deleted = dbHelper.deleteRecordsNotInServer(userId, serverIds);
                            if (deleted > 0) {
                                Log.d(TAG, "删除了 " + deleted + " 条服务器已删除的本地记录");
                            }
                        }
                        
                        // 重新加载本地数据
                        loadLocalData();
                        Log.d(TAG, "从服务器同步了 " + data.getRecords().size() + " 条记录");
                    }
                }

                @Override
                public void onError(int code, String message) {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    Log.w(TAG, "从服务器获取数据失败: " + message);
                }

                @Override
                public void onNetworkError(Exception e) {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    Log.w(TAG, "网络错误，使用本地缓存: " + e.getMessage());
                }
            });
    }
    
    private DatabaseHelper.RunningRecord dtoToRecord(RunningRecordDto dto) {
        DatabaseHelper.RunningRecord record = new DatabaseHelper.RunningRecord();
        record.serverId = dto.getId();
        record.userId = dto.getUserId() != null ? dto.getUserId() : 1;
        record.date = dto.getDate();
        record.distance = dto.getDistance() != null ? dto.getDistance() : 0;
        record.duration = dto.getDuration() != null ? dto.getDuration() : 0;
        record.steps = dto.getSteps() != null ? dto.getSteps() : 0;
        record.calories = dto.getCalories() != null ? dto.getCalories() : 0;
        record.pace = dto.getPace() != null ? dto.getPace() : 0;
        record.track = dto.getTrack();
        record.isSynced = true;
        record.semesterId = dto.getSemesterId();
        record.taskId = dto.getTaskId();
        record.isValid = dto.getIsValid();
        return record;
    }
    
    private void updateDisplayRecords() {
        displayRecords.clear();
        if (isExpanded || allRecords.size() <= DEFAULT_SHOW_COUNT) {
            displayRecords.addAll(allRecords);
        } else {
            displayRecords.addAll(allRecords.subList(0, DEFAULT_SHOW_COUNT));
        }
    }
    
    private void updateLoadMoreButton() {
        if (btnLoadMore != null) {
            if (allRecords.size() > DEFAULT_SHOW_COUNT) {
                btnLoadMore.setVisibility(View.VISIBLE);
                int hiddenCount = allRecords.size() - DEFAULT_SHOW_COUNT;
                if (isExpanded) {
                    btnLoadMore.setText("▲ 收起历史记录");
                } else {
                    btnLoadMore.setText("▼ 查看更早记录 (" + hiddenCount + "条)");
                }
            } else {
                btnLoadMore.setVisibility(View.GONE);
            }
        }
    }
    
    private void toggleExpand() {
        isExpanded = !isExpanded;
        updateDisplayRecords();
        updateLoadMoreButton();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        
        // 展开时滚动到底部，收起时滚动到顶部
        if (rvHistory != null) {
            if (isExpanded) {
                rvHistory.scrollToPosition(displayRecords.size() - 1);
            } else {
                rvHistory.scrollToPosition(0);
            }
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
            DatabaseHelper.RunningRecord record = displayRecords.get(position);
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
            return displayRecords != null ? displayRecords.size() : 0;
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
