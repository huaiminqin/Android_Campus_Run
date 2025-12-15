package com.example.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "running_app.db";
    private static final int DATABASE_VERSION = 1;

    // 表名
    public static final String TABLE_USERS = "users";
    public static final String TABLE_RUNNING_RECORDS = "running_records";
    public static final String TABLE_SURVEY_RESULTS = "survey_results";

    // 用户表字段
    public static final String USER_ID = "id";
    public static final String USER_USERNAME = "username";
    public static final String USER_PASSWORD = "password";
    public static final String USER_NICKNAME = "nickname";
    public static final String USER_WEIGHT = "weight";
    public static final String USER_CREATED_AT = "created_at";

    // 跑步记录表字段
    public static final String RECORD_ID = "id";
    public static final String RECORD_USER_ID = "user_id";
    public static final String RECORD_DATE = "date";
    public static final String RECORD_DISTANCE = "distance";
    public static final String RECORD_DURATION = "duration";
    public static final String RECORD_STEPS = "steps";
    public static final String RECORD_CALORIES = "calories";
    public static final String RECORD_PACE = "pace";
    public static final String RECORD_TRACK = "track";
    public static final String RECORD_CREATED_AT = "created_at";

    // 问卷表字段
    public static final String SURVEY_ID = "id";
    public static final String SURVEY_USER_ID = "user_id";
    public static final String SURVEY_FREQUENCY = "frequency";
    public static final String SURVEY_PURPOSE = "purpose";
    public static final String SURVEY_SATISFACTION = "satisfaction";
    public static final String SURVEY_SUGGESTION = "suggestion";
    public static final String SURVEY_CREATED_AT = "created_at";

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建用户表
        String createUsersTable = "CREATE TABLE " + TABLE_USERS + " (" +
                USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                USER_USERNAME + " VARCHAR(50) NOT NULL UNIQUE, " +
                USER_PASSWORD + " VARCHAR(100) NOT NULL, " +
                USER_NICKNAME + " VARCHAR(50), " +
                USER_WEIGHT + " DECIMAL(5,2), " +
                USER_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP)";

        // 创建跑步记录表
        String createRecordsTable = "CREATE TABLE " + TABLE_RUNNING_RECORDS + " (" +
                RECORD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                RECORD_USER_ID + " INTEGER NOT NULL, " +
                RECORD_DATE + " DATETIME NOT NULL, " +
                RECORD_DISTANCE + " DECIMAL(10,2) DEFAULT 0, " +
                RECORD_DURATION + " INTEGER DEFAULT 0, " +
                RECORD_STEPS + " INTEGER DEFAULT 0, " +
                RECORD_CALORIES + " INTEGER DEFAULT 0, " +
                RECORD_PACE + " DECIMAL(5,2) DEFAULT 0, " +
                RECORD_TRACK + " TEXT, " +
                RECORD_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (" + RECORD_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + USER_ID + "))";

        // 创建问卷调查表
        String createSurveyTable = "CREATE TABLE " + TABLE_SURVEY_RESULTS + " (" +
                SURVEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                SURVEY_USER_ID + " INTEGER NOT NULL, " +
                SURVEY_FREQUENCY + " VARCHAR(50), " +
                SURVEY_PURPOSE + " VARCHAR(50), " +
                SURVEY_SATISFACTION + " INTEGER, " +
                SURVEY_SUGGESTION + " TEXT, " +
                SURVEY_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (" + SURVEY_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + USER_ID + "))";

        db.execSQL(createUsersTable);
        db.execSQL(createRecordsTable);
        db.execSQL(createSurveyTable);

        // 插入默认用户
        ContentValues defaultUser = new ContentValues();
        defaultUser.put(USER_USERNAME, "default");
        defaultUser.put(USER_PASSWORD, "123456");
        defaultUser.put(USER_NICKNAME, "跑步达人");
        defaultUser.put(USER_WEIGHT, 70.0);
        db.insert(TABLE_USERS, null, defaultUser);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SURVEY_RESULTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RUNNING_RECORDS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // ==================== 用户相关操作 ====================
    public long insertUser(String username, String password, String nickname, double weight) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(USER_USERNAME, username);
        values.put(USER_PASSWORD, password);
        values.put(USER_NICKNAME, nickname);
        values.put(USER_WEIGHT, weight);
        return db.insert(TABLE_USERS, null, values);
    }

    public User getDefaultUser() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, USER_ID + "=?", 
                new String[]{"1"}, null, null, null);
        User user = null;
        if (cursor.moveToFirst()) {
            user = cursorToUser(cursor);
        }
        cursor.close();
        return user;
    }

    // ==================== 跑步记录相关操作 ====================
    public long insertRunningRecord(int userId, String date, double distance, 
            int duration, int steps, int calories, double pace, String track) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(RECORD_USER_ID, userId);
        values.put(RECORD_DATE, date);
        values.put(RECORD_DISTANCE, distance);
        values.put(RECORD_DURATION, duration);
        values.put(RECORD_STEPS, steps);
        values.put(RECORD_CALORIES, calories);
        values.put(RECORD_PACE, pace);
        values.put(RECORD_TRACK, track);
        return db.insert(TABLE_RUNNING_RECORDS, null, values);
    }

    public List<RunningRecord> getRunningRecords(int userId) {
        List<RunningRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_RUNNING_RECORDS, null, 
                RECORD_USER_ID + "=?", new String[]{String.valueOf(userId)}, 
                null, null, RECORD_DATE + " DESC");
        
        while (cursor.moveToNext()) {
            records.add(cursorToRunningRecord(cursor));
        }
        cursor.close();
        return records;
    }

    public List<RunningRecord> getAllRunningRecords() {
        return getRunningRecords(1); // 默认用户ID为1
    }

    // ==================== 问卷相关操作 ====================
    public long insertSurveyResult(int userId, String frequency, String purpose, 
            int satisfaction, String suggestion) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SURVEY_USER_ID, userId);
        values.put(SURVEY_FREQUENCY, frequency);
        values.put(SURVEY_PURPOSE, purpose);
        values.put(SURVEY_SATISFACTION, satisfaction);
        values.put(SURVEY_SUGGESTION, suggestion);
        return db.insert(TABLE_SURVEY_RESULTS, null, values);
    }

    public List<SurveyResult> getSurveyResults(int userId) {
        List<SurveyResult> results = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SURVEY_RESULTS, null,
                SURVEY_USER_ID + "=?", new String[]{String.valueOf(userId)},
                null, null, SURVEY_CREATED_AT + " DESC");

        while (cursor.moveToNext()) {
            results.add(cursorToSurveyResult(cursor));
        }
        cursor.close();
        return results;
    }

    // ==================== 辅助方法 ====================
    private User cursorToUser(Cursor cursor) {
        User user = new User();
        user.id = cursor.getInt(cursor.getColumnIndexOrThrow(USER_ID));
        user.username = cursor.getString(cursor.getColumnIndexOrThrow(USER_USERNAME));
        user.password = cursor.getString(cursor.getColumnIndexOrThrow(USER_PASSWORD));
        user.nickname = cursor.getString(cursor.getColumnIndexOrThrow(USER_NICKNAME));
        user.weight = cursor.getDouble(cursor.getColumnIndexOrThrow(USER_WEIGHT));
        user.createdAt = cursor.getString(cursor.getColumnIndexOrThrow(USER_CREATED_AT));
        return user;
    }

    private RunningRecord cursorToRunningRecord(Cursor cursor) {
        RunningRecord record = new RunningRecord();
        record.id = cursor.getInt(cursor.getColumnIndexOrThrow(RECORD_ID));
        record.userId = cursor.getInt(cursor.getColumnIndexOrThrow(RECORD_USER_ID));
        record.date = cursor.getString(cursor.getColumnIndexOrThrow(RECORD_DATE));
        record.distance = cursor.getDouble(cursor.getColumnIndexOrThrow(RECORD_DISTANCE));
        record.duration = cursor.getInt(cursor.getColumnIndexOrThrow(RECORD_DURATION));
        record.steps = cursor.getInt(cursor.getColumnIndexOrThrow(RECORD_STEPS));
        record.calories = cursor.getInt(cursor.getColumnIndexOrThrow(RECORD_CALORIES));
        record.pace = cursor.getDouble(cursor.getColumnIndexOrThrow(RECORD_PACE));
        record.track = cursor.getString(cursor.getColumnIndexOrThrow(RECORD_TRACK));
        record.createdAt = cursor.getString(cursor.getColumnIndexOrThrow(RECORD_CREATED_AT));
        return record;
    }

    private SurveyResult cursorToSurveyResult(Cursor cursor) {
        SurveyResult result = new SurveyResult();
        result.id = cursor.getInt(cursor.getColumnIndexOrThrow(SURVEY_ID));
        result.userId = cursor.getInt(cursor.getColumnIndexOrThrow(SURVEY_USER_ID));
        result.frequency = cursor.getString(cursor.getColumnIndexOrThrow(SURVEY_FREQUENCY));
        result.purpose = cursor.getString(cursor.getColumnIndexOrThrow(SURVEY_PURPOSE));
        result.satisfaction = cursor.getInt(cursor.getColumnIndexOrThrow(SURVEY_SATISFACTION));
        result.suggestion = cursor.getString(cursor.getColumnIndexOrThrow(SURVEY_SUGGESTION));
        result.createdAt = cursor.getString(cursor.getColumnIndexOrThrow(SURVEY_CREATED_AT));
        return result;
    }

    // ==================== 数据模型类 ====================
    public static class User {
        public int id;
        public String username;
        public String password;
        public String nickname;
        public double weight;
        public String createdAt;
    }

    public static class RunningRecord {
        public int id;
        public int userId;
        public String date;
        public double distance;
        public int duration;
        public int steps;
        public int calories;
        public double pace;
        public String track;
        public String createdAt;
    }

    public static class SurveyResult {
        public int id;
        public int userId;
        public String frequency;
        public String purpose;
        public int satisfaction;
        public String suggestion;
        public String createdAt;
    }
}
