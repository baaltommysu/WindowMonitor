package com.baaltommysu.windowmonitor.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCleanupPolicyTest {
    @Test
    fun keepsPhotosWhenMoreThanFivePercentStorageRemains() {
        assertFalse(
            StorageCleanupPolicy.shouldDeleteOldestPhotos(
                freeBytes = 6,
                totalBytes = 100
            )
        )
    }

    @Test
    fun deletesOldestPhotosAtFivePercentStorageRemaining() {
        assertTrue(
            StorageCleanupPolicy.shouldDeleteOldestPhotos(
                freeBytes = 5,
                totalBytes = 100
            )
        )
    }

    @Test
    fun keepsPhotosWhenStorageSizeCannotBeRead() {
        assertFalse(
            StorageCleanupPolicy.shouldDeleteOldestPhotos(
                freeBytes = 0,
                totalBytes = 0
            )
        )
    }
}
