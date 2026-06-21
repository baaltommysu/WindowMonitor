package com.baaltommysu.windowmonitor.camera

import android.view.OrientationEventListener
import android.view.Surface

/** Maps a physical-device orientation to the corresponding CameraX target rotation. */
object CaptureRotationPolicy {
    fun targetRotationFor(orientationDegrees: Int): Int? {
        if (orientationDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) return null

        return when (orientationDegrees) {
            in 45..134 -> Surface.ROTATION_270
            in 135..224 -> Surface.ROTATION_180
            in 225..314 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }
    }
}
