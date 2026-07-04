package com.baaltommysu.windowmonitor.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoQualityTest {
    @Test
    fun rejectsTinyBlackPhoto() {
        val quality = PhotoQuality(
            sizeBytes = 10_000L,
            width = 1920,
            height = 1080,
            meanBrightness = 2,
            darkRatioPercent = 96,
            brightRatioPercent = 0
        )

        assertFalse(quality.isUsable)
    }

    @Test
    fun acceptsFullSizeNightPhoto() {
        val quality = PhotoQuality(
            sizeBytes = 7_000_000L,
            width = 4080,
            height = 3060,
            meanBrightness = 9,
            darkRatioPercent = 93,
            brightRatioPercent = 0
        )

        assertTrue(quality.isUsable)
    }

    @Test
    fun rejectsOverExposedPhoto() {
        val quality = PhotoQuality(
            sizeBytes = 500_000L,
            width = 1920,
            height = 1080,
            meanBrightness = 248,
            darkRatioPercent = 0,
            brightRatioPercent = 82
        )

        assertFalse(quality.isUsable)
    }

    @Test
    fun rejectsPhotoWithTooSmallDimensions() {
        val quality = PhotoQuality(
            sizeBytes = 500_000L,
            width = 320,
            height = 240,
            meanBrightness = 120,
            darkRatioPercent = 4,
            brightRatioPercent = 8
        )

        assertFalse(quality.isUsable)
    }

    @Test
    fun acceptsNormalPhoto() {
        val quality = PhotoQuality(
            sizeBytes = 500_000L,
            width = 1920,
            height = 1080,
            meanBrightness = 120,
            darkRatioPercent = 4,
            brightRatioPercent = 8
        )

        assertTrue(quality.isUsable)
    }
}
