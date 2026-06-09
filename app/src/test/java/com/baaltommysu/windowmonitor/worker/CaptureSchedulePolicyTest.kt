package com.baaltommysu.windowmonitor.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CaptureSchedulePolicyTest {
    @Test
    fun allowsCaptureWhenNoLastPhotoTimeExists() {
        assertTrue(
            CaptureSchedulePolicy.isDue(
                lastPhotoTime = "",
                intervalMinutes = 30,
                now = Instant.parse("2026-06-09T12:00:00Z")
            )
        )
    }

    @Test
    fun computesRemainingDelayFromLastPhotoTime() {
        assertEquals(
            5 * 60 * 1000L,
            CaptureSchedulePolicy.millisUntilDue(
                lastPhotoTime = "2026-06-09T11:35:00Z",
                intervalMinutes = 30,
                now = Instant.parse("2026-06-09T12:00:00Z")
            )
        )
    }

    @Test
    fun marksCaptureDueAfterInterval() {
        assertFalse(
            CaptureSchedulePolicy.isDue(
                lastPhotoTime = "2026-06-09T11:45:00Z",
                intervalMinutes = 30,
                now = Instant.parse("2026-06-09T12:00:00Z")
            )
        )
        assertTrue(
            CaptureSchedulePolicy.isDue(
                lastPhotoTime = "2026-06-09T11:30:00Z",
                intervalMinutes = 30,
                now = Instant.parse("2026-06-09T12:00:00Z")
            )
        )
    }
}
