package com.baaltommysu.windowmonitor.worker

import java.time.Duration
import java.time.Instant

object CaptureSchedulePolicy {
    fun isDue(
        lastPhotoTime: String,
        intervalMinutes: Int,
        now: Instant = Instant.now()
    ): Boolean {
        return millisUntilDue(lastPhotoTime, intervalMinutes, now) == 0L
    }

    fun millisUntilDue(
        lastPhotoTime: String,
        intervalMinutes: Int,
        now: Instant = Instant.now()
    ): Long {
        if (lastPhotoTime.isBlank()) return 0L
        val lastPhotoInstant = runCatching { Instant.parse(lastPhotoTime) }.getOrNull()
            ?: return 0L
        val intervalMillis = Duration.ofMinutes(intervalMinutes.coerceAtLeast(1).toLong()).toMillis()
        val elapsedMillis = Duration.between(lastPhotoInstant, now).toMillis()
        return (intervalMillis - elapsedMillis).coerceAtLeast(0L)
    }
}
