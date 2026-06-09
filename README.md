# WindowMonitor

WindowMonitor is an Android camera monitoring app for local experiments. It keeps a foreground camera monitor alive, captures photos on a configurable minute interval, saves photos into the public Pictures library, and can periodically send pending photos by SMTP in batches.

The current target device is Android 14 on vivo X Note. Current app version: `0.3.2`.

## Current Features

- CameraX photo capture
- Foreground camera service kept alive for Android 14 camera foreground-service rules
- Configurable capture loop, defaulting to 30 minutes while monitoring is enabled
- Configurable periodic mail delivery, defaulting to 120 minutes
- AlarmManager wakeups plus an in-service capture loop for periodic scheduling
- Screen-off capture and mail delivery using partial wake locks
- Public photo storage through MediaStore
- MediaStore decode through file descriptors for vivo Android 14 compatibility
- Camera warmup using ImageCapture plus ImageAnalysis before taking a photo
- In-app SMTP configuration form, defaulting to Sina SMTP
- SMTP mail sender with up to six image attachments per message
- Pending photo retry behavior when mail is not configured or sending fails
- Boot receiver to restore monitoring after reboot
- Remote command polling and FCM handler placeholders

## Photo Location

Photos are saved to the public system Pictures directory:

```text
/sdcard/Pictures/WindowMonitor/
```

On the phone this should appear in the Gallery app as `WindowMonitor` or under another album group such as `Other albums`.

Example filename:

```text
photo_20260601_224113.jpg
```

## Mail Settings

Open the app and scroll to `Mail settings`.

The default host is `smtp.sina.com`. For Sina SMTP, use:

```text
SMTP host: smtp.sina.com
Port: 465
SMTP username: your Sina email address
App password: Sina SMTP authorization code
From: your Sina email address
To: destination email address
```

For 163 SMTP, use:

```text
SMTP host: smtp.163.com
Port: 465
SMTP username: your 163 email address
App password: 163 SMTP authorization code
From: your 163 email address
To: destination email address
```

Use a 163 SMTP authorization code, not the mailbox login password.

After saving settings, enable `Periodic mail` and set the mail interval in minutes. With the default 30-minute capture interval and 120-minute mail interval, the app normally sends one message containing up to six photos. Sent photos are retained in `Pictures/WindowMonitor`; the app trims older local photos only when the cache exceeds the configured retention limit.

Mail settings are stored in app-local preferences. Use upgrade installs (`adb install -r`) to preserve them; uninstalling or clearing app data removes the saved SMTP information.

## Runtime Model

The app uses a foreground service for the camera loop so it can keep running while the screen is off. The service must be started while the app is visible because Android 14 restricts starting a `camera` foreground service from the background.

Periodic capture is scheduled with `AlarmManager.setAndAllowWhileIdle` and checked again inside the foreground service using the last successful photo time. This prevents opening the app from pushing the next capture out by a full interval.

Periodic mail delivery is decoupled from the camera service. Alarm wakeups start a separate `dataSync` foreground service for mail delivery, while WorkManager remains as a periodic mail retry path.

```text
MainActivity
  -> WorkScheduler
  -> CameraCaptureForegroundService
  -> CameraManager
  -> PhotoRepository

WorkManager
  -> MailRetryWorker
  -> MailQueue
  -> SmtpSender

AlarmReceiver
  -> CameraCaptureForegroundService
  -> MailDeliveryForegroundService
```

`CameraCaptureForegroundService` is a foreground service with:

```xml
android:foregroundServiceType="camera"
```

`MailDeliveryForegroundService` is a foreground service with:

```xml
android:foregroundServiceType="dataSync"
```

The camera service keeps a single loop running and uses a mutex so multiple capture requests do not close each other's camera session.

## Android 14 Notes

Android 14 restricts background camera use. This app uses a foreground service and visible notification so the camera can be used while the monitor is active.

Important behavior:

- Turn monitoring on while the app is open so the camera foreground service starts from a visible state.
- If the app process is force-stopped or killed by vendor power management, open the app once to restart the foreground camera monitor.
- Periodic mail does not depend on the camera foreground-service type.

Required permissions include:

```text
CAMERA
FOREGROUND_SERVICE
FOREGROUND_SERVICE_CAMERA
FOREGROUND_SERVICE_DATA_SYNC
POST_NOTIFICATIONS
READ_MEDIA_IMAGES
WAKE_LOCK
RECEIVE_BOOT_COMPLETED
INTERNET
```

For long-running tests, use:

```text
USB-C power connected
Wi-Fi connected
Battery optimization disabled for this app
Notification permission granted
Camera permission granted
```

## Build

The project uses the Gradle wrapper.

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install with adb:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Grant runtime permissions during install when possible:

```sh
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
```

On vivo devices, the installer may show a risk confirmation page for APKs from unknown sources. Check the confirmation box and continue the installation.

## Useful ADB Commands

List generated photos:

```sh
adb shell ls -lt /sdcard/Pictures/WindowMonitor
```

Check app state:

```sh
adb shell run-as com.baaltommysu.windowmonitor cat shared_prefs/window_monitor_state.xml
```

View relevant logs:

```sh
adb logcat -d -v time | grep -E "WindowMonitor|CameraService|MailQueue|SmtpSender|ImageCaptureException|SecurityException"
```

Check foreground service:

```sh
adb shell dumpsys activity services com.baaltommysu.windowmonitor
```

Check scheduled alarms:

```sh
adb shell dumpsys alarm | grep -E "ALARM_CAPTURE|ALARM_MAIL"
```

Check WorkManager state:

```sh
adb exec-out run-as com.baaltommysu.windowmonitor cat no_backup/androidx.work.workdb > /tmp/windowmonitor-workdb
sqlite3 /tmp/windowmonitor-workdb "select WorkName.name, WorkSpec.worker_class_name, WorkSpec.state from WorkName join WorkSpec on WorkSpec.id = WorkName.work_spec_id;"
```

## Source Layout

```text
app/src/main/java/com/baaltommysu/windowmonitor/
├── MainActivity.kt
├── camera/
│   └── CameraManager.kt
├── heartbeat/
│   └── HeartbeatManager.kt
├── mail/
│   ├── MailQueue.kt
│   ├── SmtpConfig.kt
│   └── SmtpSender.kt
├── receiver/
│   ├── AlarmReceiver.kt
│   └── BootReceiver.kt
├── remote/
│   ├── CommandApi.kt
│   └── FcmService.kt
├── service/
│   ├── CameraCaptureForegroundService.kt
│   └── MailDeliveryForegroundService.kt
├── storage/
│   └── PhotoRepository.kt
├── util/
│   ├── AppLogger.kt
│   ├── DeviceStatus.kt
│   └── PreferenceStore.kt
└── worker/
    ├── CaptureSchedulePolicy.kt
    ├── CameraWorker.kt
    ├── CommandPollingWorker.kt
    ├── HeartbeatWorker.kt
    ├── MailRetryWorker.kt
    └── WorkScheduler.kt
```

## Known Limitations

- SMTP credentials are stored in app-local preferences for the current prototype.
- FCM and remote command polling are placeholders.
- WorkManager periodic mail delivery uses Android's minimum periodic interval, so values below 15 minutes are rounded up. Alarm scheduling is used alongside it for the active periodic mail path.
- The app is intended for personal testing and non-Play distribution.
- Long-term camera use may still be affected by vendor power management unless the app is excluded from battery optimization and allowed to run in the background.
