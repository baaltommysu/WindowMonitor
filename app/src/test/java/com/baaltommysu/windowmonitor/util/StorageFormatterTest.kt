package com.baaltommysu.windowmonitor.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageFormatterTest {
    @Test
    fun formatsFreeSpaceWithCapacityAndPercent() {
        assertEquals(
            "100GB 0MB 0KB / 222GB 0MB 0KB (45% free)",
            StorageFormatter.formatFreeSpace(100_000_000_000L, 222_000_000_000L)
        )
    }

    @Test
    fun formatsSmallValuesWithoutDecimal() {
        assertEquals("0GB 0MB 0KB", StorageFormatter.formatBytes(512L))
        assertEquals("0GB 1MB 500KB", StorageFormatter.formatBytes(1_500_000L))
    }
}
