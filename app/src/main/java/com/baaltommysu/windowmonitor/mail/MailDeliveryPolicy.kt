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
}
