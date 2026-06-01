package com.baaltommysu.windowmonitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baaltommysu.windowmonitor.service.CameraCaptureForegroundService
import com.baaltommysu.windowmonitor.util.AppLogger
import com.baaltommysu.windowmonitor.util.PreferenceStore

class CameraWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            CameraCaptureForegroundService.start(applicationContext)
            Result.success()
        } catch (error: Exception) {
            AppLogger.e(Tag, "could not start camera service", error)
            PreferenceStore(applicationContext).markFailure(
                error.message ?: "Could not start camera service"
            )
            Result.retry()
        }
    }

    companion object {
        private const val Tag = "CameraWorker"
    }
}
