package com.baaltommysu.windowmonitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baaltommysu.windowmonitor.mail.MailQueue

class MailRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return if (MailQueue(applicationContext).flushPending()) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
