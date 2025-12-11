-- 跑步统计软件数据库
-- MySQL 8.0

CREATE DATABASE IF NOT EXISTS running_app DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE running_app;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50),
    gender TINYINT DEFAULT 0 COMMENT '0未知 1男 2女',
    age INT,
    weight DECIMAL(5,2) COMMENT '体重kg',
    height DECIMAL(5,2) COMMENT '身高cm',
    avatar VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 跑步记录表
CREATE TABLE IF NOT EXISTS running_records (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    duration INT DEFAULT 0 COMMENT '时长(秒)',
    distance DECIMAL(10,2) DEFAULT 0 COMMENT '距离(米)',
    steps INT DEFAULT 0 COMMENT '步数',
    calories DECIMAL(10,2) DEFAULT 0 COMMENT '消耗卡路里',
    avg_pace DECIMAL(5,2) DEFAULT 0 COMMENT '平均配速(分钟/公里)',
    max_pace DECIMAL(5,2) DEFAULT 0 COMMENT '最快配速',
    avg_speed DECIMAL(5,2) DEFAULT 0 COMMENT '平均速度(km/h)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 轨迹点表
CREATE TABLE IF NOT EXISTS track_points (
    id INT PRIMARY KEY AUTO_INCREMENT,
    record_id INT NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    altitude DECIMAL(10,2) COMMENT '海拔',
    speed DECIMAL(5,2) COMMENT '瞬时速度',
    timestamp DATETIME NOT NULL,
    FOREIGN KEY (record_id) REFERENCES running_records(id) ON DELETE CASCADE
);

-- 每日统计表
CREATE TABLE IF NOT EXISTS daily_stats (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    date DATE NOT NULL,
    total_steps INT DEFAULT 0,
    total_distance DECIMAL(10,2) DEFAULT 0,
    total_duration INT DEFAULT 0,
    total_calories DECIMAL(10,2) DEFAULT 0,
    run_count INT DEFAULT 0 COMMENT '跑步次数',
    UNIQUE KEY uk_user_date (user_id, date),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 插入测试用户
INSERT INTO users (username, password, nickname, gender, age, weight, height) 
VALUES ('test', '123456', '跑步达人', 1, 25, 70.00, 175.00);

-- 插入一些测试数据
INSERT INTO running_records (user_id, start_time, end_time, duration, distance, steps, calories, avg_pace, avg_speed)
VALUES 
(1, '2024-12-10 07:00:00', '2024-12-10 07:35:00', 2100, 5000, 6500, 350, 7.0, 8.57),
(1, '2024-12-09 18:00:00', '2024-12-09 18:45:00', 2700, 6200, 8100, 420, 7.26, 8.27),
(1, '2024-12-08 06:30:00', '2024-12-08 07:00:00', 1800, 4000, 5200, 280, 7.5, 8.0);

INSERT INTO daily_stats (user_id, date, total_steps, total_distance, total_duration, total_calories, run_count)
VALUES
(1, '2024-12-10', 6500, 5000, 2100, 350, 1),
(1, '2024-12-09', 8100, 6200, 2700, 420, 1),
(1, '2024-12-08', 5200, 4000, 1800, 280, 1);
