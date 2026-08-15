package com.baaltommysu.windowmonitor.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment

data class DeviceSnapshot(
    val batteryPercent: Int,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long
)

object DeviceStatus {
    fun read(context: Context): DeviceSnapshot {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        return DeviceSnapshot(
            batteryPercent = percent,
            storageFreeBytes = Environment.getDataDirectory().freeSpace,
            storageTotalBytes = Environment.getDataDirectory().totalSpace
        )
    }
}
