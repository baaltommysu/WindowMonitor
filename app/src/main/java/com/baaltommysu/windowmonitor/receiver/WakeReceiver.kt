package com.baaltommysu.windowmonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.baaltommysu.windowmonitor.service.CameraCaptureForegroundService
import com.baaltommysu.windowmonitor.util.PreferenceStore
import com.baaltommysu.windowmonitor.worker.WorkScheduler

class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WorkScheduler.ActionWake) return

        val store = PreferenceStore(context)
        if (store.monitoringEnabled) {
            WorkScheduler.scheduleNextCaptureAlarm(context)
            CameraCaptureForegroundService.startMonitoring(context)
        }
        if (store.mailDeliveryEnabled) {
            WorkScheduler.scheduleNextMailAlarm(context)
        }
        store.appendLog("看门狗", "收到远程唤醒，已重排闹钟并确保前台服务运行")
    }
}
