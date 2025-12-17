# 校园跑步统计系统

基于 Android + Spring Boot + Vue.js 的校园跑步统计软件，采用前后端分离架构，集成GPS定位、计步器、传感器、指南针、地图轨迹等功能，支持用户认证、数据同步、任务管理、成绩统计。

（本项目授权于huaimin、怀民到底寝没寝、2304等，解释权归作者所有）

## 快速开始

### APK下载

下载 `app-debug.apk` 安装使用即可。不建议下载realse版本

### 在线访问

- 管理后台：http://113.44.92.166:4749
- 后端API：http://113.44.92.166:8080

## 系统架构

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Android App    │────▶│  Spring Boot    │────▶│     MySQL       │
│  (用户端)        │     │  (后端API)       │     │   (数据库)       │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                               ▲
                               │
                        ┌──────┴──────┐
                        │   Vue.js    │
                        │  (管理后台)  │
                        └─────────────┘
```

## 技术栈

| 端 | 技术 |
|---|---|
| Android | Java, 高德地图SDK 9.5.0, Retrofit2, Gson |
| 后端 | Spring Boot 3.x, MyBatis-Plus, JWT, MySQL 8.0 |
| 前端 | Vue 3, Arco Design, TypeScript, Vite |

## 功能模块

### Android客户端

| 模块 | 功能 |
|---|---|
| 登录认证 | 用户名密码登录、JWT Token认证、自动登录 |
| 跑步页面 | GPS定位、轨迹绘制、实时配速/距离/步数/卡路里、指南针 |
| 历史记录 | 跑步记录列表、本地缓存+云端同步 |
| 数据统计 | 累计数据、本周数据统计 |
| 工具页面 | 配速计算器、卡路里计算器、指南针 |
| 任务中心 | 跑步任务列表、完成进度、状态显示 |
| 公告通知 | 系统公告查看 |
| 问卷调查 | 用户反馈收集 |

### 管理后台

| 模块 | 功能 |
|---|---|
| 用户管理 | 用户列表、详情、批量导入、状态管理 |
| 记录管理 | 跑步记录查询、删除 |
| 任务管理 | 任务发布、编辑、学期关联 |
| 成绩统计 | 按学期/学院筛选、达标率、Excel导出 |
| 公告管理 | 公告发布、编辑 |
| 问卷管理 | 问卷结果查看 |

## 运行环境

### Android端
- Android Studio 2021.1.1+
- Gradle 7.1.2
- JDK 1.8
- minSdk 21, targetSdk 32

### 后端
- JDK 17
- Maven 3.8+
- MySQL 8.0

### 前端
- Node.js 18+
- npm 9+

## 项目结构

```
├── app/                          # Android客户端
│   ├── src/main/java/.../
│   │   ├── MainActivity.java     # 主页面
│   │   ├── LoginActivity.java    # 登录
│   │   ├── RunFragment.java      # 跑步页面
│   │   ├── RunningService.java   # 跑步服务
│   │   ├── HistoryFragment.java  # 历史记录
│   │   ├── StatsFragment.java    # 数据统计
│   │   ├── ToolsFragment.java    # 工具页面
│   │   └── api/                  # API接口
│   └── release/
│       ├── app-debug.apk         # Debug版APK
│       └── app-release.apk       # Release版APK
│
├── 校园跑/                        # 后端+前端
│   ├── src/main/java/.../        # Spring Boot后端
│   │   ├── controller/           # REST控制器
│   │   ├── service/              # 业务服务
│   │   ├── mapper/               # MyBatis映射
│   │   └── entity/               # 实体类
│   ├── frontend/                 # Vue.js管理后台
│   │   ├── src/views/            # 页面组件
│   │   └── src/api/              # API接口
│   └── pom.xml
│
└── campus_running.sql            # 数据库脚本
```

## 部署说明

### 1. 数据库

```bash
# 导入数据库
mysql -u root -p < campus_running.sql
```

### 2. 后端服务

```bash
cd 校园跑
mvn clean package -DskipTests
java -jar target/campus-running-admin-1.0.0.jar
# 服务运行在 http://localhost:8080
```

### 3. 前端管理后台

```bash
cd 校园跑/frontend
npm install
npm run build
# 将dist目录部署到Web服务器
```

### 4. Android APK

用 Android Studio 打开项目，点击 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`

## API接口

| 接口 | 方法 | 说明 |
|---|---|---|
| /api/auth/login | POST | 用户登录 |
| /api/auth/me | GET | 获取当前用户 |
| /api/records | GET | 分页查询记录 |
| /api/records | POST | 上传跑步记录 |
| /api/tasks | GET | 获取任务列表 |
| /api/tasks/my | GET | 获取用户任务 |
| /api/announcements | GET | 获取公告列表 |
| /api/achievements | GET | 获取成绩统计 |

## 权限说明

- **位置权限**：GPS定位和轨迹记录
- **传感器权限**：计步器和指南针
- **网络权限**：地图加载和数据同步
- **前台服务权限**：后台跑步记录

## 注意事项

1. 计步功能需要设备支持加速度传感器
2. 地图功能需要网络连接
3. 首次运行需要授予位置权限
4. 后台跑步需要允许前台服务通知

## License

本项目仅供学习交流使用，未经授权不得用于商业用途。
