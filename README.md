# WindowMonitor

An Android app scaffold for monitoring foreground window changes.

## What is included

- Kotlin Android app module
- Jetpack Compose starter UI
- Accessibility service skeleton for observing window state changes
- Gradle version catalog

## Build

```sh
./gradlew build
```

## Next steps

1. Define the window events that should be recorded.
2. Decide where monitored events should be stored.
3. Add an in-app screen for service status and event history.

#Design logic
# Android 周期拍照邮件终端设计方案

## 1. 项目目标

开发一个 Android 应用，实现：

* 每 30 分钟自动拍照
* 自动发送邮件
* 长期无人值守运行
* 支持远程触发拍照
* 支持设备重启后自动恢复
* 支持异常恢复与故障诊断

应用主要用于：

* 自用测试
* 远程观察
* 实验环境记录
* 非 Google Play 上架场景

---

# 2. 系统架构

```text
┌──────────────────────┐
│ BootReceiver         │
│ 开机自动恢复          │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ WorkManager          │
│ 每30分钟执行一次      │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Camera Service       │
│ CameraX拍照          │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Local Storage        │
│ 本地缓存照片          │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ SMTP Mail Sender     │
│ 邮件发送              │
└───────┬──────────────┘
        │
 ┌──────┴───────┐
 │              │
 ▼              ▼
成功          失败
 │              │
 ▼              ▼
删除文件     保留重试
```

---

# 3. 功能模块设计

## 3.1 Camera Module

### 技术选型

推荐：

* CameraX

优势：

* 官方支持
* 生命周期管理简单
* 兼容性好

功能：

* 打开摄像头
* 自动拍照
* JPEG编码
* 保存图片

输出：

```text
photo_20260601_103000.jpg
```

---

## 3.2 Scheduler Module

### 技术选型

```text
WorkManager
```

执行周期：

```text
30分钟
```

系统限制：

```text
最小周期15分钟
```

注意：

WorkManager 不保证严格准点执行。

例如：

```text
10:00
10:30
11:00
```

实际可能变为：

```text
10:02
10:31
11:01
```

但对于监控类场景足够。

---

## 3.3 Local Storage Module

保存位置：

```text
/data/data/<package>/files/photos/
```

或：

```text
Android/data/<package>/files/photos/
```

缓存策略：

### 发送成功

```text
删除照片
```

### 发送失败

```text
保留照片
等待下次补发
```

建议：

最多缓存：

```text
1000张
```

超过自动删除最旧文件。

---

## 3.4 Mail Module

### 实现方案

SMTP发送

推荐：

```text
Gmail SMTP
```

配置：

```text
smtp.gmail.com
port: 587
TLS
```

认证：

```text
App Password
```

不要使用：

```text
邮箱主密码
```

发送内容：

* 拍照时间
* 电量状态
* 图片附件

邮件示例：

```text
Subject:
Camera Report

Body:
Capture Time: 2026-06-01 10:30:00
Battery: 92%
Storage Free: 118GB
```

附件：

```text
photo.jpg
```

---

# 4. 开机自动恢复

## 权限

```xml
<uses-permission
    android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
```

## BootReceiver

监听：

```text
BOOT_COMPLETED
```

流程：

```text
设备重启
↓
BootReceiver
↓
恢复WorkManager
↓
继续执行周期任务
```

---

# 5. 故障恢复设计

## 5.1 网络异常

情况：

```text
WiFi断开
移动网络异常
SMTP不可达
```

处理：

```text
保留照片
Result.retry()
```

下次继续发送。

---

## 5.2 拍照失败

情况：

```text
Camera Busy
Camera Crash
```

处理：

```text
记录日志
等待下一次任务
```

避免整个应用崩溃。

---

## 5.3 邮件发送失败

情况：

```text
SMTP认证失败
网络失败
附件上传失败
```

处理：

```text
缓存照片
下次补发
```

---

## 5.4 应用崩溃

采用：

```text
WorkManager
```

进程被杀后：

```text
任务仍然存在
```

下次调度继续执行。

---

# 6. 心跳机制

目的：

判断设备是否仍然正常运行。

## 每日发送

```text
Heartbeat Mail
```

内容：

```text
Device Alive

Battery: 95%
Storage Free: 120GB

Last Success:
2026-06-01 09:30
```

推荐：

```text
每天1次
```

---

# 7. 远程控制设计

## 方案一：FCM

服务器发送：

```json
{
  "action":"take_photo_now"
}
```

客户端：

```text
FCM
↓
WorkManager
↓
立即拍照
↓
发送邮件
```

适合：

```text
远程即时拍照
```

---

## 方案二：服务器轮询

客户端：

```http
GET /device/{id}/command
```

服务器返回：

```json
{
  "take_photo_now": true,
  "interval_minutes": 30
}
```

优势：

* 实现简单
* 不依赖推送
* 稳定性高

---

# 8. 电源与运行环境

建议：

```text
持续接电
```

原因：

* Camera启动耗电
* JPEG压缩耗电
* SMTP发送耗电
* WiFi保持连接耗电

推荐配置：

```text
USB-C持续供电
WiFi连接
关闭电池优化
保持网络在线
```

---

# 9. 数据记录

建议保存：

```json
{
  "last_photo_time":"",
  "last_send_time":"",
  "last_success_time":"",
  "last_failure_reason":""
}
```

用于：

* 调试
* 故障恢复
* 心跳邮件生成

---

# 10. 推荐目录结构

```text
app/
 ├── receiver/
 │    └── BootReceiver
 │
 ├── worker/
 │    └── CameraWorker
 │
 ├── camera/
 │    └── CameraManager
 │
 ├── mail/
 │    └── SmtpSender
 │
 ├── storage/
 │    └── PhotoRepository
 │
 ├── remote/
 │    ├── FcmService
 │    └── CommandApi
 │
 ├── heartbeat/
 │    └── HeartbeatManager
 │
 └── util/
      ├── Logger
      └── PreferenceStore
```

---

# 11. 后续增强路线

## Phase 1

基础版本

* 周期拍照
* SMTP发送
* 本地缓存
* 开机恢复

---

## Phase 2

远程管理

* FCM控制
* 修改拍照频率
* 查看设备状态

---

## Phase 3

监控平台

* Web控制台
* 在线状态管理
* 多设备支持

---

## Phase 4

AI增强

* 图像分析
* 人体检测
* 异常事件检测
* 自动告警

例如：

```text
检测到有人进入房间
↓
立即发送邮件
↓
附带照片
```

---

# 最终目标

构建一个长期无人值守运行的 Android 远程观察终端：

* 自动拍照
* 自动发送
* 自动恢复
* 远程控制
* 故障自恢复
* 支持 AI 分析扩展

无需人工干预即可连续运行数周甚至数月。

