# WindowMonitor

WindowMonitor is an Android camera monitoring app for local experiments. It runs a foreground camera monitor, captures a photo about every 30 minutes, saves photos into the public Pictures library, and can send pending photos by SMTP after mail settings are configured in the app.

The current target device is Android 14 on vivo X Note.

## Current Features

- CameraX photo capture
- Foreground camera service for Android 14 compatibility
- 30-second capture loop while monitoring is enabled
- Screen-off capture using a partial wake lock
- Public photo storage through MediaStore
- In-app SMTP configuration form
- SMTP mail sender with image attachments
- Pending photo retry behavior when mail is not configured or sending fails
- Boot receiver to restore monitoring after reboot
- Daily heartbeat worker scaffold
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

For Gmail SMTP, use:

```text
SMTP host: smtp.gmail.com
Port: 587
SMTP username: your Gmail address
App password: Gmail App Password
From: your Gmail address
To: destination email address
```

Use a Gmail App Password, not the mailbox login password.

After saving settings, the app will try to send pending photos after each capture. If sending succeeds, the sent photo is deleted from the pending queue. If sending fails, the photo remains in `Pictures/WindowMonitor` for the next retry.

## Runtime Model

The app does not use WorkManager for the 30-second camera interval because Android WorkManager periodic jobs have a 15-minute minimum interval. Instead:

```text
MainActivity
  -> WorkScheduler
  -> CameraCaptureForegroundService
  -> CameraManager
  -> PhotoRepository
  -> MailQueue
  -> SmtpSender
```

`CameraCaptureForegroundService` is a foreground service with:

```xml
android:foregroundServiceType="camera"
```

It keeps a single loop running and uses a mutex so multiple capture requests do not close each other's camera session.

## Android 14 Notes

Android 14 restricts background camera use. This app uses a foreground service and visible notification so the camera can be used while the monitor is active.

Required permissions include:

```text
CAMERA
FOREGROUND_SERVICE
FOREGROUND_SERVICE_CAMERA
POST_NOTIFICATIONS
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
adb logcat -d -v time | grep -E "WindowMonitor|CameraService|ImageCaptureException|SecurityException"
```

Check foreground service:

```sh
adb shell dumpsys activity services com.baaltommysu.windowmonitor
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
│   └── BootReceiver.kt
├── remote/
│   ├── CommandApi.kt
│   └── FcmService.kt
├── service/
│   └── CameraCaptureForegroundService.kt
├── storage/
│   └── PhotoRepository.kt
├── util/
│   ├── AppLogger.kt
│   ├── DeviceStatus.kt
│   └── PreferenceStore.kt
└── worker/
    ├── CameraWorker.kt
    ├── CommandPollingWorker.kt
    ├── HeartbeatWorker.kt
    ├── MailRetryWorker.kt
    └── WorkScheduler.kt
```

## Known Limitations

- SMTP credentials are stored in app-local preferences for the current prototype.
- FCM and remote command polling are placeholders.
- Heartbeat worker exists but depends on completed mail settings.
- The app is intended for personal testing and non-Play distribution.
- Long-term camera use may still be affected by vendor power management unless the app is excluded from battery optimization.
