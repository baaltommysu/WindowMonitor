package com.baaltommysu.windowmonitor.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.baaltommysu.windowmonitor.service.CameraCaptureForegroundService
import com.baaltommysu.windowmonitor.util.PreferenceStore
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val LegacyCameraWorkName = "periodic_camera_capture"
    private const val ImmediateCameraWorkName = "immediate_camera_capture"
    private const val HeartbeatWorkName = "daily_heartbeat"
    private const val CommandPollingWorkName = "command_polling"
    private const val PeriodicMailWorkName = "periodic_mail_delivery"
    private const val ImmediateMailWorkName = "immediate_mail_delivery"

    fun enablePeriodicCapture(context: Context, intervalMinutes: Long? = null) {
        intervalMinutes?.let { PreferenceStore(context).captureIntervalMinutes = it.toInt() }
        cancelLegacyCameraWork(context)
        CameraCaptureForegroundService.startMonitoring(context)
    }

    fun disablePeriodicCapture(context: Context) {
        cancelLegacyCameraWork(context)
        CameraCaptureForegroundService.stopMonitoring(context)
        disableHeartbeat(context)
        disableCommandPolling(context)
    }

    fun captureNow(context: Context) {
        CameraCaptureForegroundService.captureOnce(context)
    }

    fun enableHeartbeat(context: Context) {
        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HeartbeatWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun enablePeriodicMail(context: Context, intervalMinutes: Long? = null) {
        val store = PreferenceStore(context)
        intervalMinutes?.let { store.mailIntervalMinutes = it.toInt() }
        val request = PeriodicWorkRequestBuilder<MailRetryWorker>(
            store.mailIntervalMinutes.coerceAtLeast(15).toLong(),
            TimeUnit.MINUTES
        ).setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PeriodicMailWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun sendMailSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<MailRetryWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ImmediateMailWorkName,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun enableCommandPolling(context: Context, intervalMinutes: Long = 30) {
        val request = PeriodicWorkRequestBuilder<CommandPollingWorker>(
            intervalMinutes.coerceAtLeast(15),
            TimeUnit.MINUTES
        ).setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CommandPollingWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun disableHeartbeat(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(HeartbeatWorkName)
    }

    fun disableCommandPolling(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CommandPollingWorkName)
    }

    fun disablePeriodicMail(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PeriodicMailWorkName)
    }

    fun cancelLegacyCameraWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LegacyCameraWorkName)
        WorkManager.getInstance(context).cancelUniqueWork(ImmediateCameraWorkName)
        WorkManager.getInstance(context).cancelUniqueWork(HeartbeatWorkName)
    }
}
