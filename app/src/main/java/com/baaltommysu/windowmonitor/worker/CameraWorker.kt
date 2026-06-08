package com.baaltommysu.windowmonitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baaltommysu.windowmonitor.service.CameraCaptureForegroundService
import com.baaltommysu.windowmonitor.util.AppLogger
import com.baaltommysu.windowmonitor.util.PreferenceStore
import java.time.Duration
import java.time.Instant

class CameraWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val store = PreferenceStore(applicationContext)
            if (!store.monitoringEnabled) return Result.success()
            if (capturedRecently(store)) {
                AppLogger.d(Tag, "skip periodic camera worker because a recent capture exists")
                return Result.success()
            }
            CameraCaptureForegroundService.captureOnce(applicationContext)
            Result.success()
        } catch (error: Exception) {
            AppLogger.e(Tag, "could not start camera service", error)
            PreferenceStore(applicationContext).markFailure(
                error.message ?: "Could not start camera service"
            )
            Result.retry()
        }
    }

    private fun capturedRecently(store: PreferenceStore): Boolean {
        val lastPhotoTime = store.lastPhotoTime
        if (lastPhotoTime.isBlank()) return false
        val lastPhotoInstant = runCatching { Instant.parse(lastPhotoTime) }.getOrNull() ?: return false
        val elapsedMinutes = Duration.between(lastPhotoInstant, Instant.now()).toMinutes()
        val threshold = (store.captureIntervalMinutes.coerceAtLeast(15) * 0.8).toLong()
        return elapsedMinutes < threshold
    }

    companion object {
        private const val Tag = "CameraWorker"
    }
}
