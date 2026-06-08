package com.baaltommysu.windowmonitor.mail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MailDeliveryPolicyTest {
    @Test
    fun allowsSendWhenNoLastSendTimeExists() {
        assertTrue(
            MailDeliveryPolicy.isDue(
                lastSendTime = "",
                intervalMinutes = 120,
                now = Instant.parse("2026-06-08T08:00:00Z")
            )
        )
    }

    @Test
    fun skipsSendBeforeInterval() {
        assertFalse(
            MailDeliveryPolicy.isDue(
                lastSendTime = "2026-06-08T07:00:00Z",
                intervalMinutes = 120,
                now = Instant.parse("2026-06-08T08:00:00Z")
            )
        )
    }

    @Test
    fun allowsSendAfterInterval() {
        assertTrue(
            MailDeliveryPolicy.isDue(
                lastSendTime = "2026-06-08T06:00:00Z",
                intervalMinutes = 120,
                now = Instant.parse("2026-06-08T08:00:00Z")
            )
        )
    }
}
