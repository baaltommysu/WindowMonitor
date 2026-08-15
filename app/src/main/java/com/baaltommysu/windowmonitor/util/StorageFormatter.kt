package com.baaltommysu.windowmonitor.util

import java.util.Locale
import kotlin.math.roundToInt

object StorageFormatter {
    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "Unknown"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1000.0 && unitIndex < units.lastIndex) {
            value /= 1000.0
            unitIndex += 1
        }
        return if (unitIndex == 0) {
            "$bytes B"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[unitIndex])
        }
    }

    fun formatFreeSpace(freeBytes: Long, totalBytes: Long): String {
        val freeText = formatBytes(freeBytes)
        val totalText = formatBytes(totalBytes)
        if (totalBytes <= 0L || freeBytes < 0L) return "$freeText / $totalText"
        val percent = (freeBytes.toDouble() * 100.0 / totalBytes.toDouble())
            .roundToInt()
            .coerceIn(0, 100)
        return "$freeText / $totalText (${percent}% free)"
    }
}
