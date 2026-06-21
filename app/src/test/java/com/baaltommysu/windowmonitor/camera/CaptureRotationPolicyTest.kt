package com.baaltommysu.windowmonitor.camera

import android.view.OrientationEventListener
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureRotationPolicyTest {
    @Test
    fun mapsLandscapeClockwiseDevicePoseToClockwiseTargetRotation() {
        assertEquals(Surface.ROTATION_90, CaptureRotationPolicy.targetRotationFor(270))
    }

    @Test
    fun mapsAllCardinalDevicePoses() {
        assertEquals(Surface.ROTATION_0, CaptureRotationPolicy.targetRotationFor(0))
        assertEquals(Surface.ROTATION_270, CaptureRotationPolicy.targetRotationFor(90))
        assertEquals(Surface.ROTATION_180, CaptureRotationPolicy.targetRotationFor(180))
    }

    @Test
    fun ignoresUnknownSensorOrientation() {
        assertNull(
            CaptureRotationPolicy.targetRotationFor(OrientationEventListener.ORIENTATION_UNKNOWN)
        )
    }
}
