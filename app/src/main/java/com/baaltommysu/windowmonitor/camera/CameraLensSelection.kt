package com.baaltommysu.windowmonitor.camera

data class CameraLensCandidate(
    val cameraId: String,
    val isBackFacing: Boolean,
    val focalLengthsMm: List<Float>,
    val minZoomRatio: Float?,
) {
    val smallestFocalLengthMm: Float? = focalLengthsMm
        .filter { it > 0f }
        .minOrNull()

    val effectiveWideFocalLengthMm: Float? = smallestFocalLengthMm?.let { focalLength ->
        val zoomRatio = minZoomRatio?.takeIf { it > 0f }?.coerceAtMost(1f) ?: 1f
        focalLength * zoomRatio
    }
}

object CameraLensSelection {
    fun chooseWidestBackCamera(candidates: List<CameraLensCandidate>): CameraLensCandidate? {
        return candidates
            .asSequence()
            .filter { it.isBackFacing }
            .filter { it.effectiveWideFocalLengthMm != null }
            .minWithOrNull(
                compareBy<CameraLensCandidate> { it.effectiveWideFocalLengthMm ?: Float.MAX_VALUE }
                    .thenBy { it.smallestFocalLengthMm ?: Float.MAX_VALUE }
                    .thenBy { it.minZoomRatio ?: 1f }
                    .thenBy { it.cameraId }
            )
    }

    fun preferredZoomRatio(candidate: CameraLensCandidate): Float? {
        return candidate.minZoomRatio?.takeIf { it > 0f && it < 1f }
    }
}
