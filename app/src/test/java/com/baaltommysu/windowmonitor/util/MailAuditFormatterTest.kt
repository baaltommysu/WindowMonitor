package com.baaltommysu.windowmonitor.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MailAuditFormatterTest {
    @Test
    fun includesActualAndPlannedTimes() {
        val line = MailAuditFormatter.format(
            actualTime = Instant.parse("2026-06-29T01:00:00Z"),
            source = "alarm",
            event = "scheduled",
            plannedTime = Instant.parse("2026-06-29T03:00:00Z"),
            detail = "delayMillis=7200000"
        )

        assertTrue(line.contains("actual=2026-06-29T01:00:00Z"))
        assertTrue(line.contains("source=alarm"))
        assertTrue(line.contains("event=scheduled"))
        assertTrue(line.contains("planned=2026-06-29T03:00:00Z"))
        assertTrue(line.contains("detail=delayMillis=7200000"))
    }

    @Test
    fun keepsDetailsSingleLineAndSeparatorSafe() {
        val line = MailAuditFormatter.format(
            actualTime = Instant.parse("2026-06-29T01:00:00Z"),
            source = "work manager",
            event = "smtp accepted",
            detail = "response=250 ok\nqueue|id"
        )

        assertTrue(line.contains("source=work-manager"))
        assertTrue(line.contains("event=smtp-accepted"))
        assertTrue(line.contains("response=250 ok queue/id"))
        assertFalse(line.contains("\n"))
    }
}
