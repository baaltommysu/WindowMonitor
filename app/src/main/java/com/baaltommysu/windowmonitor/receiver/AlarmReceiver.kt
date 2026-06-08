package com.baaltommysu.windowmonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.baaltommysu.windowmonitor.service.CameraCaptureForegroundService
import com.baaltommysu.windowmonitor.worker.WorkScheduler

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WorkScheduler.ActionCaptureAlarm -> {
                WorkScheduler.scheduleNextCaptureAlarm(context)
                CameraCaptureForegroundService.captureOnce(context)
            }
            WorkScheduler.ActionMailAlarm -> {
                WorkScheduler.scheduleNextMailAlarm(context)
                CameraCaptureForegroundService.sendMailOnce(context)
            }
        }
    }
}
