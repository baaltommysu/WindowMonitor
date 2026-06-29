package com.baaltommysu.windowmonitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.baaltommysu.windowmonitor.R
import com.baaltommysu.windowmonitor.mail.MailQueue
import com.baaltommysu.windowmonitor.util.AppLogger
import com.baaltommysu.windowmonitor.util.PreferenceStore
import com.baaltommysu.windowmonitor.worker.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MailDeliveryForegroundService : LifecycleService() {
    private val store by lazy { PreferenceStore(this) }
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        store.appendMailAudit(
            source = "alarm-service",
            event = "service_start",
            detail = "startId=$startId"
        )
        if (!promoteToForeground()) {
            return Service.START_NOT_STICKY
        }

        lifecycleScope.launch {
            acquireWakeLock()
            try {
                withContext(Dispatchers.IO) {
                    MailQueue(this@MailDeliveryForegroundService).flushPending(
                        action = "周期发送邮件",
                        enforceInterval = true,
                        source = "alarm-service"
                    )
                }
            } finally {
                WorkScheduler.scheduleNextMailAlarm(this@MailDeliveryForegroundService)
                releaseWakeLock()
                stopSelf(startId)
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun promoteToForeground(): Boolean {
        return try {
            startForeground(NotificationId, buildNotification("Mail delivery is active"))
            true
        } catch (error: SecurityException) {
            handleForegroundStartFailure(error)
            false
        } catch (error: RuntimeException) {
            handleForegroundStartFailure(error)
            false
        }
    }

    private fun handleForegroundStartFailure(error: RuntimeException) {
        AppLogger.e(Tag, "mail foreground start failed", error)
        val reason = error.message ?: error.javaClass.simpleName
        store.markFailure(reason)
        store.appendLog("周期发送邮件", "失败，原因=系统限制邮件前台服务启动：$reason")
        store.appendMailAudit(
            source = "alarm-service",
            event = "foreground_start_failed",
            detail = "reason=$reason"
        )
        WorkScheduler.scheduleNextMailAlarm(this)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WindowMonitor:MailDelivery"
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
            "Mail delivery",
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
        private const val Tag = "MailService"
        private const val ChannelId = "mail_delivery"
        private const val NotificationId = 1002
        private const val WakeLockTimeoutMillis = 10 * 60 * 1000L

        fun sendMailOnce(context: Context) {
            val intent = Intent(context, MailDeliveryForegroundService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: RuntimeException) {
                handleStartFailure(context, error)
            }
        }

        private fun handleStartFailure(context: Context, error: RuntimeException) {
            AppLogger.e(Tag, "could not start mail foreground service", error)
            val reason = error.message ?: error.javaClass.simpleName
            val store = PreferenceStore(context)
            store.markFailure(reason)
            store.appendLog("周期发送邮件", "失败，原因=系统限制邮件前台服务启动：$reason")
            store.appendMailAudit(
                source = "alarm-service",
                event = "foreground_start_failed",
                detail = "reason=$reason"
            )
            WorkScheduler.scheduleNextMailAlarm(context)
        }
    }
}
