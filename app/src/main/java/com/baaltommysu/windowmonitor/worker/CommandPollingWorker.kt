package com.baaltommysu.windowmonitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baaltommysu.windowmonitor.remote.CommandApi
import com.baaltommysu.windowmonitor.util.PreferenceStore

class CommandPollingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val command = CommandApi(applicationContext).fetchCommand()
        if (command.takePhotoNow) {
            WorkScheduler.captureNow(applicationContext)
        }
        command.intervalMinutes?.let {
            PreferenceStore(applicationContext).monitoringEnabled = true
            WorkScheduler.enablePeriodicCapture(applicationContext, it)
            if (PreferenceStore(applicationContext).mailDeliveryEnabled) {
                WorkScheduler.enablePeriodicMail(applicationContext)
            }
        }
        return Result.success()
    }
}
