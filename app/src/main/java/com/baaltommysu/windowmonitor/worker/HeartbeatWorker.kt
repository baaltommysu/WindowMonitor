package com.baaltommysu.windowmonitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baaltommysu.windowmonitor.mail.SmtpConfig
import com.baaltommysu.windowmonitor.mail.SmtpSender
import com.baaltommysu.windowmonitor.util.AppLogger
import com.baaltommysu.windowmonitor.util.DeviceStatus
import com.baaltommysu.windowmonitor.util.PreferenceStore

class HeartbeatWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = PreferenceStore(applicationContext)
        val config = SmtpConfig.from(store)
        if (!config.isConfigured) {
            store.markFailure("SMTP is not configured")
            return Result.retry()
        }

        return try {
            val snapshot = DeviceStatus.read(applicationContext)
            val body = buildString {
                appendLine("Device Alive")
                appendLine()
                appendLine("Battery: ${snapshot.batteryPercent}%")
                appendLine("Storage Free: ${snapshot.storageFreeBytes} bytes")
                appendLine("Last Success: ${store.lastSuccessTime}")
            }
            SmtpSender(applicationContext).sendHeartbeat(config, body)
            store.markSuccess()
            Result.success()
        } catch (error: Exception) {
            AppLogger.e(Tag, "heartbeat mail failed", error)
            store.markFailure(error.message ?: "Heartbeat failed")
            if (error.isDailySendLimit()) return Result.failure()
            Result.retry()
        }
    }

    private fun Throwable.isDailySendLimit(): Boolean {
        return message?.contains("too many message send today", ignoreCase = true) == true
    }

    companion object {
        private const val Tag = "HeartbeatWorker"
    }
}
