package com.baaltommysu.windowmonitor.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermarkStyleTest {
    @Test
    fun usesReadableDimensionsForFullResolutionPhotos() {
        assertEquals(72f, WatermarkStyle.TextSizePx)
        assertEquals(40f, WatermarkStyle.PaddingPx)
        assertTrue(WatermarkStyle.TextSizePx >= 64f)
    }
}
