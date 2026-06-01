package com.baaltommysu.windowmonitor.heartbeat

import android.content.Context
import com.baaltommysu.windowmonitor.worker.WorkScheduler

class HeartbeatManager(private val context: Context) {
    fun enableDailyHeartbeat() {
        WorkScheduler.enableHeartbeat(context)
    }
}
