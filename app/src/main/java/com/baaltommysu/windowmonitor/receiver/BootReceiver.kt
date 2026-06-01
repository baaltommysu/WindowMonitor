package com.baaltommysu.windowmonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.baaltommysu.windowmonitor.util.PreferenceStore
import com.baaltommysu.windowmonitor.worker.WorkScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!PreferenceStore(context).monitoringEnabled) return

        WorkScheduler.enablePeriodicCapture(context)
        WorkScheduler.enableHeartbeat(context)
        WorkScheduler.enableCommandPolling(context)
    }
}
