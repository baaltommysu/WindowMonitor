# WindowMonitor

WindowMonitor is an Android camera monitoring app for local experiments. It runs a foreground camera monitor, captures photos on a configurable minute interval, saves photos into the public Pictures library, and can periodically send pending photos by SMTP in batches.

The current target device is Android 14 on vivo X Note.

## Current Features

- CameraX photo capture
- Foreground camera service for Android 14 compatibility
- Configurable capture loop, defaulting to 30 minutes while monitoring is enabled
- Configurable periodic mail delivery, defaulting to 120 minutes
- Screen-off capture using a partial wake lock
- Public photo storage through MediaStore
- In-app SMTP configuration form, defaulting to 163 SMTP
- SMTP mail sender with up to four image attachments per message
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

After saving settings, enable `Periodic mail` and set the mail interval in minutes. With the default 30-minute capture interval and 120-minute mail interval, the app normally sends one message containing four photos. If sending succeeds, those photos are deleted from the pending queue. If sending fails, the photos remain in `Pictures/WindowMonitor` for the next retry.

## Runtime Model

The app uses a foreground service for the camera loop so it can keep running while the screen is off. Periodic mail delivery uses WorkManager, whose periodic interval has a 15-minute minimum.

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
- WorkManager periodic mail delivery uses Android's minimum periodic interval, so values below 15 minutes are rounded up.
- The app is intended for personal testing and non-Play distribution.
- Long-term camera use may still be affected by vendor power management unless the app is excluded from battery optimization.
