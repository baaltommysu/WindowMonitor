package com.baaltommysu.windowmonitor.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val CameraWorkName = "periodic_camera_capture"
    private const val ImmediateCameraWorkName = "immediate_camera_capture"
    private const val HeartbeatWorkName = "daily_heartbeat"
    private const val CommandPollingWorkName = "command_polling"

    fun enablePeriodicCapture(context: Context, intervalMinutes: Long = 30) {
        val request = PeriodicWorkRequestBuilder<CameraWorker>(
            intervalMinutes.coerceAtLeast(15),
            TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CameraWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun disablePeriodicCapture(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CameraWorkName)
    }

    fun captureNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<CameraWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ImmediateCameraWorkName,
            ExistingWorkPolicy.REPLACE,
            request
        )
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
}
