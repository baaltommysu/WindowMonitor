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
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val LegacyCameraWorkName = "periodic_camera_capture"
    private const val ImmediateCameraWorkName = "immediate_camera_capture"
    private const val HeartbeatWorkName = "daily_heartbeat"
    private const val CommandPollingWorkName = "command_polling"

    fun enablePeriodicCapture(context: Context, intervalSeconds: Long = 30) {
        cancelLegacyCameraWork(context)
        CameraCaptureForegroundService.startMonitoring(context)
    }

    fun disablePeriodicCapture(context: Context) {
        cancelLegacyCameraWork(context)
        CameraCaptureForegroundService.stopMonitoring(context)
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

    fun cancelLegacyCameraWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LegacyCameraWorkName)
        WorkManager.getInstance(context).cancelUniqueWork(ImmediateCameraWorkName)
    }
}
