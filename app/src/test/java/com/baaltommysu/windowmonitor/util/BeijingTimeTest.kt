package com.baaltommysu.windowmonitor.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class BeijingTimeTest {
    @Test
    fun formatsInstantInBeijingTime() {
        val instant = Instant.parse("2026-06-29T01:00:00Z")

        assertEquals("2026-06-29 09:00:00", BeijingTime.format(instant))
        assertEquals("20260629_090000", BeijingTime.formatFileName(instant))
        assertEquals("2026-06-29 09:00:00 +08:00", BeijingTime.formatAudit(instant))
    }

    @Test
    fun formatsRfc1123WithBeijingOffset() {
        val instant = Instant.parse("2026-06-29T01:00:00Z")

        assertEquals("Mon, 29 Jun 2026 09:00:00 +0800", BeijingTime.formatRfc1123(instant))
    }
}
