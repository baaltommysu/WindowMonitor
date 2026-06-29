package com.baaltommysu.windowmonitor.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraLensSelectionTest {
    @Test
    fun choosesBackCameraWithSmallestEffectiveFocalLength() {
        val selected = CameraLensSelection.chooseWidestBackCamera(
            listOf(
                candidate(cameraId = "0", focalLengthsMm = listOf(6.51f)),
                candidate(cameraId = "2", focalLengthsMm = listOf(2.59f)),
                candidate(cameraId = "3", focalLengthsMm = listOf(5.19f), minZoomRatio = 0.6f),
            )
        )

        assertEquals("2", selected?.cameraId)
    }

    @Test
    fun usesWideZoomWhenItCreatesWiderEffectiveFocalLength() {
        val selected = CameraLensSelection.chooseWidestBackCamera(
            listOf(
                candidate(cameraId = "0", focalLengthsMm = listOf(6.51f)),
                candidate(cameraId = "3", focalLengthsMm = listOf(5.19f), minZoomRatio = 0.6f),
            )
        )

        assertEquals("3", selected?.cameraId)
        assertEquals(0.6f, CameraLensSelection.preferredZoomRatio(selected!!))
    }

    @Test
    fun ignoresFrontFacingCameras() {
        val selected = CameraLensSelection.chooseWidestBackCamera(
            listOf(
                candidate(cameraId = "1", isBackFacing = false, focalLengthsMm = listOf(1.8f)),
                candidate(cameraId = "0", focalLengthsMm = listOf(6.51f)),
            )
        )

        assertEquals("0", selected?.cameraId)
    }

    @Test
    fun returnsNullWhenNoBackCameraHasUsableFocalLength() {
        val selected = CameraLensSelection.chooseWidestBackCamera(
            listOf(
                candidate(cameraId = "0", focalLengthsMm = emptyList()),
                candidate(cameraId = "1", isBackFacing = false, focalLengthsMm = listOf(1.8f)),
            )
        )

        assertNull(selected)
    }

    @Test
    fun doesNotRequestZoomForNormalMinimumZoom() {
        val candidate = candidate(cameraId = "0", focalLengthsMm = listOf(6.51f), minZoomRatio = 1f)

        assertNull(CameraLensSelection.preferredZoomRatio(candidate))
    }

    private fun candidate(
        cameraId: String,
        isBackFacing: Boolean = true,
        focalLengthsMm: List<Float>,
        minZoomRatio: Float? = null,
    ): CameraLensCandidate {
        return CameraLensCandidate(
            cameraId = cameraId,
            isBackFacing = isBackFacing,
            focalLengthsMm = focalLengthsMm,
            minZoomRatio = minZoomRatio,
        )
    }
}
