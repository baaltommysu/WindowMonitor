package com.baaltommysu.windowmonitor.storage

object StorageCleanupPolicy {
    const val MinFreePercent = 5

    fun shouldDeleteOldestPhotos(
        freeBytes: Long,
        totalBytes: Long,
        minFreePercent: Int = MinFreePercent
    ): Boolean {
        if (totalBytes <= 0L || freeBytes < 0L) return false
        return freeBytes * 100L <= totalBytes * minFreePercent.toLong()
    }
}
