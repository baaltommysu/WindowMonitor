package com.baaltommysu.windowmonitor.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.baaltommysu.windowmonitor.R
import com.baaltommysu.windowmonitor.camera.CameraManager
import com.baaltommysu.windowmonitor.mail.MailDeliveryPolicy
import com.baaltommysu.windowmonitor.mail.MailQueue
import com.baaltommysu.windowmonitor.mail.SmtpConfig
import com.baaltommysu.windowmonitor.storage.PhotoRepository
import com.baaltommysu.windowmonitor.util.AppLogger
import com.baaltommysu.windowmonitor.util.PreferenceStore
import com.baaltommysu.windowmonitor.worker.CaptureSchedulePolicy
import com.baaltommysu.windowmonitor.worker.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant

class CameraCaptureForegroundService : LifecycleService() {
    private val store by lazy { PreferenceStore(this) }
    private val repository by lazy { PhotoRepository(this) }
    private val cameraManager by lazy { CameraManager(this) }
    private val captureMutex = Mutex()
    private var wakeLock: PowerManager.WakeLock? = null
    private var monitoringJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action
        if (action == ActionStopMonitoring) {
            stopMonitoring()
            return Service.START_NOT_STICKY
        }
        if (!promoteToForeground(action)) {
            return Service.START_NOT_STICKY
        }

        when (action) {
            ActionCaptureOnce -> captureOnce(
                stopWhenDone = !store.monitoringEnabled,
                startId = startId,
                force = intent.getBooleanExtra(ExtraForceCapture, true)
            )
            else -> startMonitoring()
        }

        return if (store.monitoringEnabled) Service.START_STICKY else Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun promoteToForeground(action: String?): Boolean {
        return try {
            startForeground(NotificationId, buildNotification("Camera monitor is active"))
            true
        } catch (error: SecurityException) {
            handleForegroundStartFailure(action, error)
            false
        } catch (error: RuntimeException) {
            handleForegroundStartFailure(action, error)
            false
        }
    }

    private fun handleForegroundStartFailure(action: String?, error: RuntimeException) {
        AppLogger.e(Tag, "camera foreground start failed", error)
        val reason = error.message ?: error.javaClass.simpleName
        store.markFailure(reason)
        val actionLabel = if (action == ActionStartMonitoring) "监控" else "拍照"
        store.appendLog(actionLabel, "失败，原因=系统限制后台相机服务启动：$reason")
        WorkScheduler.scheduleNextCaptureAlarm(this)
        stopSelf()
    }

    private fun startMonitoring() {
        store.monitoringEnabled = true
        WorkScheduler.cancelAbandonedWork(this)
        WorkScheduler.scheduleNextCaptureAlarm(this)
        ensureMonitoringLoop()
    }

    private fun stopMonitoring() {
        store.monitoringEnabled = false
        monitoringJob?.cancel()
        monitoringJob = null
        releaseWakeLock()
        stopSelf()
    }

    private fun ensureMonitoringLoop() {
        if (monitoringJob?.isActive == true) return
        store.appendLog("监控", "拍照前台服务已保持运行")
        monitoringJob = lifecycleScope.launch {
            while (isActive && store.monitoringEnabled) {
                val waitMillis = CaptureSchedulePolicy.millisUntilDue(
                    lastPhotoTime = store.lastPhotoTime,
                    intervalMinutes = store.captureIntervalMinutes
                )
                if (waitMillis > 0) {
                    delay(waitMillis)
                }
                if (!store.monitoringEnabled) break

                acquireWakeLock()
                try {
                    runCaptureCycle(force = false)
                } finally {
                    releaseWakeLock()
                }
                delay(MonitorLoopPauseMillis)
            }
        }
    }

    private fun captureOnce(stopWhenDone: Boolean, startId: Int, force: Boolean) {
        lifecycleScope.launch {
            acquireWakeLock()
            try {
                runCaptureCycle(force)
            } finally {
                releaseWakeLock()
                if (stopWhenDone) {
                    stopSelf(startId)
                }
            }
        }
    }

    private suspend fun runCaptureCycle(force: Boolean) {
        captureMutex.withLock {
            try {
                if (!force && !CaptureSchedulePolicy.isDue(store.lastPhotoTime, store.captureIntervalMinutes)) {
                    WorkScheduler.scheduleNextCaptureAlarm(this@CameraCaptureForegroundService)
                    return
                }
                store.appendLog("拍照", "开始")
                if (!hasCameraPermission()) {
                    throw IllegalStateException("Camera permission is not granted")
                }

                val capturedPhoto = captureUsablePhoto()
                store.lastPhotoTime = Instant.now().toString()
                repository.trimCache(MaxStoredPhotos)

                AppLogger.d(Tag, "capture saved to Pictures/WindowMonitor: ${capturedPhoto.name}")
                store.appendLog("拍照", "成功，文件=${capturedPhoto.name}")
                WorkScheduler.scheduleNextCaptureAlarm(this@CameraCaptureForegroundService)
                sendMailIfDue()
            } catch (error: Exception) {
                AppLogger.e(Tag, "capture failed", error)
                val reason = error.message ?: "Capture failed"
                store.markFailure(reason)
                store.appendLog("拍照", "失败，原因=$reason")
                WorkScheduler.scheduleNextCaptureAlarm(this@CameraCaptureForegroundService)
            }
        }
    }

    private suspend fun captureUsablePhoto(): com.baaltommysu.windowmonitor.storage.CapturedPhoto {
        var lastReason = "Unknown quality problem"
        repeat(MaxCaptureAttempts) { attempt ->
            val photo = repository.createPhotoTarget()
            val capturedPhoto = cameraManager.capturePhoto(this@CameraCaptureForegroundService, photo)
            val quality = waitForPhotoQuality(capturedPhoto)
            if (quality.isUsable) {
                repository.addTimestampOverlay(capturedPhoto, photo.capturedAt)
                repository.markPhotoReady(capturedPhoto)
                store.appendLog("拍照", "画面检测通过，${quality.summary}")
                return capturedPhoto
            }

            lastReason = quality.summary
            repository.deletePhoto(capturedPhoto)
            store.appendLog("拍照", "画面异常已丢弃，第${attempt + 1}次，$lastReason")
            delay(CaptureRetryDelayMillis)
        }
        throw IllegalStateException("Captured photo quality failed after $MaxCaptureAttempts attempts: $lastReason")
    }

    private suspend fun waitForPhotoQuality(
        photo: com.baaltommysu.windowmonitor.storage.CapturedPhoto
    ): com.baaltommysu.windowmonitor.storage.PhotoQuality {
        var lastError: Throwable? = null
        repeat(PhotoReadAttempts) {
            try {
                return repository.analyzePhoto(photo)
            } catch (error: Exception) {
                lastError = error
                delay(PhotoReadRetryDelayMillis)
            }
        }
        repository.deletePhoto(photo)
        throw IllegalStateException(lastError?.message ?: "Could not read captured photo")
    }

    private suspend fun sendMailIfDue() {
        if (!store.mailDeliveryEnabled || !SmtpConfig.from(store).isConfigured) return
        if (!MailDeliveryPolicy.isDue(store.lastSendTime, store.mailIntervalMinutes)) return
        store.appendLog("周期发送邮件", "拍照后检测到已到发送周期，前台服务直接发送")
        withContext(Dispatchers.IO) {
            MailQueue(this@CameraCaptureForegroundService).flushPending(
                action = "周期发送邮件",
                enforceInterval = true
            )
        }
        WorkScheduler.scheduleNextMailAlarm(this)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WindowMonitor:CameraCapture"
        ).apply {
            setReferenceCounted(false)
            acquire(WakeLockTimeoutMillis)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ChannelId,
            "Camera capture",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, ChannelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(text)
        .setOngoing(true)
        .build()

    companion object {
        private const val Tag = "CameraService"
        private const val ChannelId = "camera_capture"
        private const val NotificationId = 1001
        private const val MaxStoredPhotos = 500
        private const val MaxCaptureAttempts = 3
        private const val CaptureRetryDelayMillis = 2_000L
        private const val PhotoReadAttempts = 8
        private const val PhotoReadRetryDelayMillis = 750L
        private const val MonitorLoopPauseMillis = 60_000L
        private const val WakeLockTimeoutMillis = 10 * 60 * 1000L
        private const val ActionStartMonitoring = "com.baaltommysu.windowmonitor.START_MONITORING"
        private const val ActionStopMonitoring = "com.baaltommysu.windowmonitor.STOP_MONITORING"
        private const val ActionCaptureOnce = "com.baaltommysu.windowmonitor.CAPTURE_ONCE"
        private const val ExtraForceCapture = "force_capture"

        fun startMonitoring(context: Context) {
            start(context, ActionStartMonitoring)
        }

        fun stopMonitoring(context: Context) {
            PreferenceStore(context).monitoringEnabled = false
            context.stopService(Intent(context, CameraCaptureForegroundService::class.java))
        }

        fun captureOnce(context: Context) {
            start(context, ActionCaptureOnce) {
                putExtra(ExtraForceCapture, true)
            }
        }

        fun captureFromAlarm(context: Context) {
            start(context, ActionCaptureOnce) {
                putExtra(ExtraForceCapture, false)
            }
        }

        private fun start(
            context: Context,
            actionName: String,
            configureIntent: Intent.() -> Unit = {}
        ) {
            val intent = Intent(context, CameraCaptureForegroundService::class.java).apply {
                action = actionName
                configureIntent()
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: RuntimeException) {
                handleStartFailure(context, actionName, error)
            }
        }

        private fun handleStartFailure(context: Context, actionName: String, error: RuntimeException) {
            AppLogger.e(Tag, "could not start camera foreground service", error)
            val reason = error.message ?: error.javaClass.simpleName
            val store = PreferenceStore(context)
            store.markFailure(reason)
            val actionLabel = if (actionName == ActionStartMonitoring) "监控" else "拍照"
            store.appendLog(actionLabel, "失败，原因=系统限制后台相机服务启动：$reason")
            WorkScheduler.scheduleNextCaptureAlarm(context)
        }
    }
}
