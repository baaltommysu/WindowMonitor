package com.baaltommysu.windowmonitor.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageFormatterTest {
    @Test
    fun formatsFreeSpaceWithCapacityAndPercent() {
        assertEquals(
            "100.0 GB / 222.0 GB (45% free)",
            StorageFormatter.formatFreeSpace(100_000_000_000L, 222_000_000_000L)
        )
    }

    @Test
    fun formatsSmallValuesWithoutDecimal() {
        assertEquals("512 B", StorageFormatter.formatBytes(512L))
        assertEquals("1.5 MB", StorageFormatter.formatBytes(1_500_000L))
    }
}
