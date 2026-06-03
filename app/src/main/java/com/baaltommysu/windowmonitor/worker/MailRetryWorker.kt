package com.baaltommysu.windowmonitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baaltommysu.windowmonitor.mail.MailQueue
import com.baaltommysu.windowmonitor.util.PreferenceStore

class MailRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = PreferenceStore(applicationContext)
        return if (MailQueue(applicationContext).flushPending("周期发送邮件")) {
            Result.success()
        } else if (store.lastFailureReason.contains("too many message send today", ignoreCase = true)) {
            Result.failure()
        } else {
            Result.retry()
        }
    }
}
