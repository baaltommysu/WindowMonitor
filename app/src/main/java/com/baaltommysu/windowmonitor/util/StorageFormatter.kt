package com.baaltommysu.windowmonitor.util

import kotlin.math.roundToInt

object StorageFormatter {
    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "Unknown"
        val gigabytes = bytes / Gigabyte
        val megabytes = (bytes % Gigabyte) / Megabyte
        val kilobytes = (bytes % Megabyte) / Kilobyte
        return "${gigabytes}GB ${megabytes}MB ${kilobytes}KB"
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

    private const val Kilobyte = 1_000L
    private const val Megabyte = 1_000_000L
    private const val Gigabyte = 1_000_000_000L
}
