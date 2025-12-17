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
    private static final int DATABASE_VERSION = 2; // 升级版本以支持同步字段

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
    // 同步相关字段
    public static final String RECORD_SERVER_ID = "server_id";
    public static final String RECORD_IS_SYNCED = "is_synced";
    public static final String RECORD_SEMESTER_ID = "semester_id";
    public static final String RECORD_TASK_ID = "task_id";
    public static final String RECORD_IS_VALID = "is_valid";

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

        // 创建跑步记录表（包含同步字段）
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
                RECORD_SERVER_ID + " INTEGER DEFAULT NULL, " +
                RECORD_IS_SYNCED + " INTEGER DEFAULT 0, " +
                RECORD_SEMESTER_ID + " INTEGER DEFAULT NULL, " +
                RECORD_TASK_ID + " INTEGER DEFAULT NULL, " +
                RECORD_IS_VALID + " INTEGER DEFAULT 1, " +
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
        // 获取所有记录（不按用户过滤，用于调试）
        List<RunningRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_RUNNING_RECORDS, null, 
                null, null, 
                null, null, RECORD_DATE + " DESC");
        
        while (cursor.moveToNext()) {
            records.add(cursorToRunningRecord(cursor));
        }
        cursor.close();
        return records;
    }
    
    /**
     * 获取当前登录用户的跑步记录（用户数据隔离）
     */
    public List<RunningRecord> getRunningRecordsByUserId(int userId) {
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

    // ==================== 同步相关操作 ====================
    
    /**
     * 获取未同步的跑步记录
     */
    public List<RunningRecord> getUnsyncedRecords() {
        List<RunningRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_RUNNING_RECORDS, null,
                RECORD_IS_SYNCED + "=0", null,
                null, null, RECORD_DATE + " ASC");
        
        while (cursor.moveToNext()) {
            records.add(cursorToRunningRecord(cursor));
        }
        cursor.close();
        return records;
    }

    /**
     * 标记记录为已同步
     */
    public int markAsSynced(int localId, int serverId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(RECORD_SERVER_ID, serverId);
        values.put(RECORD_IS_SYNCED, 1);
        return db.update(TABLE_RUNNING_RECORDS, values,
                RECORD_ID + "=?", new String[]{String.valueOf(localId)});
    }

    /**
     * 按服务器ID查询记录
     */
    public RunningRecord getRecordByServerId(int serverId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_RUNNING_RECORDS, null,
                RECORD_SERVER_ID + "=?", new String[]{String.valueOf(serverId)},
                null, null, null);
        
        RunningRecord record = null;
        if (cursor.moveToFirst()) {
            record = cursorToRunningRecord(cursor);
        }
        cursor.close();
        return record;
    }

    /**
     * 获取未同步记录数量
     */
    public int getUnsyncedCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_RUNNING_RECORDS + " WHERE " + RECORD_IS_SYNCED + "=0",
                null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    /**
     * 插入或更新记录（用于从服务器同步）
     */
    public long insertOrUpdateFromServer(RunningRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        // 检查是否已存在
        RunningRecord existing = getRecordByServerId(record.serverId);
        
        ContentValues values = new ContentValues();
        values.put(RECORD_USER_ID, record.userId);
        values.put(RECORD_DATE, record.date);
        values.put(RECORD_DISTANCE, record.distance);
        values.put(RECORD_DURATION, record.duration);
        values.put(RECORD_STEPS, record.steps);
        values.put(RECORD_CALORIES, record.calories);
        values.put(RECORD_PACE, record.pace);
        values.put(RECORD_TRACK, record.track);
        values.put(RECORD_SERVER_ID, record.serverId);
        values.put(RECORD_IS_SYNCED, 1);
        values.put(RECORD_SEMESTER_ID, record.semesterId);
        values.put(RECORD_TASK_ID, record.taskId);
        values.put(RECORD_IS_VALID, record.isValid);
        
        if (existing != null) {
            // 更新现有记录（服务器优先）
            return db.update(TABLE_RUNNING_RECORDS, values,
                    RECORD_SERVER_ID + "=?", new String[]{String.valueOf(record.serverId)});
        } else {
            // 插入新记录
            return db.insert(TABLE_RUNNING_RECORDS, null, values);
        }
    }
    
    /**
     * 删除本地存在但服务器上已删除的记录
     * @param userId 用户ID
     * @param serverIds 服务器上存在的记录ID集合
     */
    public int deleteRecordsNotInServer(int userId, java.util.Set<Integer> serverIds) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        // 如果服务器返回空列表，不删除本地记录（可能是网络问题）
        if (serverIds == null || serverIds.isEmpty()) {
            return 0;
        }
        
        // 构建 NOT IN 条件
        StringBuilder inClause = new StringBuilder();
        for (Integer id : serverIds) {
            if (inClause.length() > 0) inClause.append(",");
            inClause.append(id);
        }
        
        // 删除该用户的、已同步的、但不在服务器列表中的记录
        String whereClause = RECORD_USER_ID + "=? AND " + RECORD_IS_SYNCED + "=1 AND " + 
                RECORD_SERVER_ID + " IS NOT NULL AND " + RECORD_SERVER_ID + " NOT IN (" + inClause + ")";
        
        return db.delete(TABLE_RUNNING_RECORDS, whereClause, new String[]{String.valueOf(userId)});
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
        // 同步字段
        int serverIdIndex = cursor.getColumnIndex(RECORD_SERVER_ID);
        if (serverIdIndex >= 0 && !cursor.isNull(serverIdIndex)) {
            record.serverId = cursor.getInt(serverIdIndex);
        }
        int syncedIndex = cursor.getColumnIndex(RECORD_IS_SYNCED);
        if (syncedIndex >= 0) {
            record.isSynced = cursor.getInt(syncedIndex) == 1;
        }
        int semesterIndex = cursor.getColumnIndex(RECORD_SEMESTER_ID);
        if (semesterIndex >= 0 && !cursor.isNull(semesterIndex)) {
            record.semesterId = cursor.getInt(semesterIndex);
        }
        int taskIndex = cursor.getColumnIndex(RECORD_TASK_ID);
        if (taskIndex >= 0 && !cursor.isNull(taskIndex)) {
            record.taskId = cursor.getInt(taskIndex);
        }
        int validIndex = cursor.getColumnIndex(RECORD_IS_VALID);
        if (validIndex >= 0 && !cursor.isNull(validIndex)) {
            record.isValid = cursor.getInt(validIndex);
        }
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
        // 同步相关字段
        public Integer serverId;
        public boolean isSynced;
        public Integer semesterId;
        public Integer taskId;
        public Integer isValid;
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
