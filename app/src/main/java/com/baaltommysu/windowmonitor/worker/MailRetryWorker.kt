package com.baaltommysu.windowmonitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baaltommysu.windowmonitor.mail.MailQueue
import com.baaltommysu.windowmonitor.storage.PhotoRepository
import com.baaltommysu.windowmonitor.util.PreferenceStore

class MailRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = PreferenceStore(applicationContext)
        val hasPhotos = PhotoRepository(applicationContext).listPendingPhotosOldestFirst().isNotEmpty()
        if (!hasPhotos && store.monitoringEnabled && runAttemptCount < MaxEmptyRetries) {
            store.appendLog("周期发送邮件", "暂时没有待发送照片，稍后重试，第${runAttemptCount + 1}次")
            return Result.retry()
        }

        val sentOrSkipped = MailQueue(applicationContext).flushPending("周期发送邮件", enforceInterval = true)
        WorkScheduler.scheduleNextMailAlarm(applicationContext)
        return if (sentOrSkipped) {
            Result.success()
        } else if (store.lastFailureReason.contains("too many message send today", ignoreCase = true)) {
            Result.failure()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val MaxEmptyRetries = 3
    }
}
