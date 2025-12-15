package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class RunDataManager {
    private static final String PREF_NAME = "running_data";
    private static final String KEY_RECORDS = "records";
    private SharedPreferences prefs;
    private Gson gson;

    public RunDataManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void saveRecord(RunRecord record) {
        List<RunRecord> records = getRecords();
        records.add(0, record);
        // 不再限制记录数量，所有数据保存到SQLite
        String json = gson.toJson(records);
        prefs.edit().putString(KEY_RECORDS, json).apply();
    }

    public List<RunRecord> getRecords() {
        String json = prefs.getString(KEY_RECORDS, "[]");
        Type type = new TypeToken<ArrayList<RunRecord>>(){}.getType();
        List<RunRecord> records = gson.fromJson(json, type);
        return records != null ? records : new ArrayList<>();
    }

    public static class RunRecord {
        public String date;
        public double distance;
        public int duration;
        public int steps;
        public int calories;
        public double pace;
        public String track;

        public RunRecord(String date, double distance, int duration, int steps, int calories, double pace, String track) {
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
