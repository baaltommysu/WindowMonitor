package com.baaltommysu.windowmonitor.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.baaltommysu.windowmonitor.mail.MailDeliveryPolicy
import com.baaltommysu.windowmonitor.receiver.AlarmReceiver
import com.baaltommysu.windowmonitor.service.CameraCaptureForegroundService
import com.baaltommysu.windowmonitor.util.PreferenceStore
import java.util.concurrent.TimeUnit

object WorkScheduler {
    const val ActionCaptureAlarm = "com.baaltommysu.windowmonitor.ALARM_CAPTURE"
    const val ActionMailAlarm = "com.baaltommysu.windowmonitor.ALARM_MAIL"

    private const val PeriodicCameraWorkName = "periodic_camera_capture"
    private const val ImmediateCameraWorkName = "immediate_camera_capture"
    private const val HeartbeatWorkName = "daily_heartbeat"
    private const val CommandPollingWorkName = "command_polling"
    private const val PeriodicMailWorkName = "periodic_mail_delivery"
    private const val ImmediateMailWorkName = "immediate_mail_delivery"
    private const val CaptureAlarmRequestCode = 1001
    private const val MailAlarmRequestCode = 1002
    private const val MinAlarmDelayMillis = 60_000L

    fun enablePeriodicCapture(context: Context, intervalMinutes: Long? = null) {
        val store = PreferenceStore(context)
        intervalMinutes?.let { store.captureIntervalMinutes = it.toInt() }
        val request = PeriodicWorkRequestBuilder<CameraWorker>(
            store.captureIntervalMinutes.coerceAtLeast(15).toLong(),
            TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PeriodicCameraWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        scheduleNextCaptureAlarm(context)
        if (store.mailDeliveryEnabled) {
            enablePeriodicMail(context)
        }
    }

    fun disablePeriodicCapture(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PeriodicCameraWorkName)
        WorkManager.getInstance(context).cancelUniqueWork(ImmediateCameraWorkName)
        cancelCaptureAlarm(context)
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
        scheduleNextMailAlarm(context)
    }

    fun enablePeriodicMail(context: Context, intervalMinutes: Long? = null) {
        val store = PreferenceStore(context)
        intervalMinutes?.let { store.mailIntervalMinutes = it.toInt() }
        if (!store.mailDeliveryEnabled) {
            disablePeriodicMail(context)
            return
        }
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
        cancelMailAlarm(context)
    }

    fun cancelLegacyCameraWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ImmediateCameraWorkName)
        WorkManager.getInstance(context).cancelUniqueWork(HeartbeatWorkName)
    }

    fun scheduleNextCaptureAlarm(context: Context) {
        val store = PreferenceStore(context)
        if (!store.monitoringEnabled) {
            cancelCaptureAlarm(context)
            return
        }
        val delayMillis = TimeUnit.MINUTES.toMillis(store.captureIntervalMinutes.coerceAtLeast(15).toLong())
        scheduleAlarm(context, ActionCaptureAlarm, CaptureAlarmRequestCode, delayMillis)
        store.appendLog("定时拍照", "已安排下次唤醒，约${delayMillis / 60_000}分钟后")
        if (store.mailDeliveryEnabled) {
            scheduleNextMailAlarm(context)
        }
    }

    fun scheduleNextMailAlarm(context: Context) {
        val store = PreferenceStore(context)
        if (!store.mailDeliveryEnabled) {
            cancelMailAlarm(context)
            return
        }
        val delayMillis = MailDeliveryPolicy.millisUntilDue(
            lastSendTime = store.lastSendTime,
            intervalMinutes = store.mailIntervalMinutes
        ).coerceAtLeast(MinAlarmDelayMillis)
        scheduleAlarm(context, ActionMailAlarm, MailAlarmRequestCode, delayMillis)
        store.appendLog("定时邮件", "已安排下次唤醒，约${delayMillis / 60_000}分钟后")
    }

    private fun scheduleAlarm(context: Context, action: String, requestCode: Int, delayMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMillis,
            alarmIntent(context, action, requestCode)
        )
    }

    private fun cancelCaptureAlarm(context: Context) {
        cancelAlarm(context, ActionCaptureAlarm, CaptureAlarmRequestCode)
    }

    private fun cancelMailAlarm(context: Context) {
        cancelAlarm(context, ActionMailAlarm, MailAlarmRequestCode)
    }

    private fun cancelAlarm(context: Context, action: String, requestCode: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, action, requestCode))
    }

    private fun alarmIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
