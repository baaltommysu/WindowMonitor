package com.baaltommysu.windowmonitor.storage

import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifOrientationPolicyTest {
    @Test
    fun rotatesUpsideDownImagesByOneHundredEightyDegrees() {
        val transform = ExifOrientationPolicy.transformFor(ExifInterface.ORIENTATION_ROTATE_180)

        assertEquals(180f, transform.rotationDegrees)
        assertFalse(transform.mirrorHorizontally)
    }

    @Test
    fun rotatesPortraitExifOrientations() {
        assertEquals(
            90f,
            ExifOrientationPolicy.transformFor(ExifInterface.ORIENTATION_ROTATE_90).rotationDegrees
        )
        assertEquals(
            270f,
            ExifOrientationPolicy.transformFor(ExifInterface.ORIENTATION_ROTATE_270).rotationDegrees
        )
    }

    @Test
    fun leavesNormalOrientationUnchanged() {
        val transform = ExifOrientationPolicy.transformFor(ExifInterface.ORIENTATION_NORMAL)

        assertFalse(transform.requiresTransform)
        assertTrue(transform.rotationDegrees == 0f)
    }
}
