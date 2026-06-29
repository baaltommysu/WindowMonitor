package com.baaltommysu.windowmonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.baaltommysu.windowmonitor.service.CameraCaptureForegroundService
import com.baaltommysu.windowmonitor.service.MailDeliveryForegroundService
import com.baaltommysu.windowmonitor.util.PreferenceStore
import com.baaltommysu.windowmonitor.worker.WorkScheduler

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WorkScheduler.ActionCaptureAlarm -> CameraCaptureForegroundService.captureFromAlarm(context)
            WorkScheduler.ActionMailAlarm -> {
                PreferenceStore(context).appendMailAudit(
                    source = "alarm",
                    event = "triggered",
                    detail = "action=${intent.action}"
                )
                MailDeliveryForegroundService.sendMailOnce(context)
            }
        }
    }
}
