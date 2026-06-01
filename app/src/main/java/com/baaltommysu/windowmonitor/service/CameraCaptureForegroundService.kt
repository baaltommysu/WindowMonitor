package com.baaltommysu.windowmonitor.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

class CameraCaptureForegroundService : LifecycleService() {
    private val store by lazy { PreferenceStore(this) }
    private val repository by lazy { PhotoRepository(this) }
    private val cameraManager by lazy { CameraManager(this) }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NotificationId, buildNotification("Preparing camera capture"))
        captureAndSend(startId)
        return Service.START_NOT_STICKY
    }

    private fun captureAndSend(startId: Int) {
        lifecycleScope.launch {
            try {
                if (!hasCameraPermission()) {
                    throw IllegalStateException("Camera permission is not granted")
                }
                val photo = repository.createPhotoFile()
                cameraManager.capturePhoto(this@CameraCaptureForegroundService, photo)
                store.lastPhotoTime = Instant.now().toString()
                repository.trimCache()
                withContext(Dispatchers.IO) {
                    MailQueue(this@CameraCaptureForegroundService).flushPending()
                }
            } catch (error: Exception) {
                AppLogger.e(Tag, "capture failed", error)
                store.markFailure(error.message ?: "Capture failed")
            } finally {
                stopSelf(startId)
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
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

        fun start(context: Context) {
            val intent = Intent(context, CameraCaptureForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
