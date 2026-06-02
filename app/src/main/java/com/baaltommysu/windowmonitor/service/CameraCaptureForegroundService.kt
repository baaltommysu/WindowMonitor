package com.baaltommysu.windowmonitor.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.baaltommysu.windowmonitor.R
import com.baaltommysu.windowmonitor.camera.CameraManager
import com.baaltommysu.windowmonitor.mail.MailQueue
import com.baaltommysu.windowmonitor.storage.PhotoRepository
import com.baaltommysu.windowmonitor.util.AppLogger
import com.baaltommysu.windowmonitor.util.PreferenceStore
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
    private var monitorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NotificationId, buildNotification("Camera monitor is active"))

        when (intent?.action) {
            ActionStopMonitoring -> stopMonitoring()
            ActionCaptureOnce -> captureOnce(stopWhenDone = monitorJob == null, startId = startId)
            else -> startMonitoring()
        }

        return Service.START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startMonitoring() {
        store.monitoringEnabled = true
        acquireWakeLock()
        if (monitorJob?.isActive == true) return

        monitorJob = lifecycleScope.launch {
            while (isActive && store.monitoringEnabled) {
                runCaptureCycle()
                delay(CaptureIntervalMillis)
            }
            stopSelf()
        }
    }

    private fun stopMonitoring() {
        store.monitoringEnabled = false
        monitorJob?.cancel()
        monitorJob = null
        releaseWakeLock()
        stopSelf()
    }

    private fun captureOnce(stopWhenDone: Boolean, startId: Int) {
        lifecycleScope.launch {
            runCaptureCycle()
            if (stopWhenDone) {
                stopSelf(startId)
            }
        }
    }

    private suspend fun runCaptureCycle() {
        captureMutex.withLock {
            try {
                if (!hasCameraPermission()) {
                    throw IllegalStateException("Camera permission is not granted")
                }

                val photo = repository.createPhotoTarget()
                val capturedPhoto = cameraManager.capturePhoto(this@CameraCaptureForegroundService, photo)
                repository.markPhotoReady(capturedPhoto)
                store.lastPhotoTime = Instant.now().toString()
                repository.trimCache()

                withContext(Dispatchers.IO) {
                    MailQueue(this@CameraCaptureForegroundService).flushPending()
                }
                AppLogger.d(Tag, "capture saved to Pictures/WindowMonitor: ${capturedPhoto.name}")
            } catch (error: Exception) {
                AppLogger.e(Tag, "capture failed", error)
                store.markFailure(error.message ?: "Capture failed")
            }
        }
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
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
        private const val CaptureIntervalMillis = 30 * 60 * 1000L
        private const val ActionStartMonitoring = "com.baaltommysu.windowmonitor.START_MONITORING"
        private const val ActionStopMonitoring = "com.baaltommysu.windowmonitor.STOP_MONITORING"
        private const val ActionCaptureOnce = "com.baaltommysu.windowmonitor.CAPTURE_ONCE"

        fun startMonitoring(context: Context) {
            start(context, ActionStartMonitoring)
        }

        fun stopMonitoring(context: Context) {
            context.startService(Intent(context, CameraCaptureForegroundService::class.java).apply {
                action = ActionStopMonitoring
            })
        }

        fun captureOnce(context: Context) {
            start(context, ActionCaptureOnce)
        }

        private fun start(context: Context, actionName: String) {
            val intent = Intent(context, CameraCaptureForegroundService::class.java).apply {
                action = actionName
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
