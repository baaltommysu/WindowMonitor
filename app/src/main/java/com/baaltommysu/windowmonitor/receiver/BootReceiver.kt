package com.baaltommysu.windowmonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.baaltommysu.windowmonitor.util.PreferenceStore
import com.baaltommysu.windowmonitor.worker.WorkScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = PreferenceStore(context)
        if (!store.monitoringEnabled && !store.mailDeliveryEnabled) return

        WorkScheduler.cancelAbandonedWork(context)
        if (store.monitoringEnabled) {
            WorkScheduler.enablePeriodicCapture(context)
        }
        if (store.mailDeliveryEnabled &&
            store.smtpHost.isNotBlank() &&
            store.smtpPort > 0 &&
            store.smtpUsername.isNotBlank() &&
            store.smtpPassword.isNotBlank() &&
            store.mailFrom.isNotBlank() &&
            store.mailTo.isNotBlank()
        ) {
            WorkScheduler.enablePeriodicMail(context)
        }
    }
}
