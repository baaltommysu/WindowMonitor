package com.baaltommysu.windowmonitor.mail

import java.time.Duration
import java.time.Instant

object MailDeliveryPolicy {
    fun isDue(lastSendTime: String, intervalMinutes: Int, now: Instant = Instant.now()): Boolean {
        if (lastSendTime.isBlank()) return true
        val lastSendInstant = runCatching { Instant.parse(lastSendTime) }.getOrNull() ?: return true
        val elapsedMinutes = Duration.between(lastSendInstant, now).toMinutes()
        return elapsedMinutes >= intervalMinutes.coerceAtLeast(15)
    }

    fun millisUntilDue(lastSendTime: String, intervalMinutes: Int, now: Instant = Instant.now()): Long {
        if (lastSendTime.isBlank()) return 0L
        val lastSendInstant = runCatching { Instant.parse(lastSendTime) }.getOrNull() ?: return 0L
        val dueAt = lastSendInstant.plus(Duration.ofMinutes(intervalMinutes.coerceAtLeast(15).toLong()))
        return Duration.between(now, dueAt).toMillis().coerceAtLeast(0L)
    }
}
