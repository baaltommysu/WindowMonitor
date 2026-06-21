package com.baaltommysu.windowmonitor.storage

import android.media.ExifInterface

data class ExifTransform(
    val rotationDegrees: Float,
    val mirrorHorizontally: Boolean = false
) {
    val requiresTransform: Boolean
        get() = rotationDegrees != 0f || mirrorHorizontally
}

object ExifOrientationPolicy {
    fun transformFor(orientation: Int): ExifTransform {
        return when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifTransform(0f, mirrorHorizontally = true)
            ExifInterface.ORIENTATION_ROTATE_180 -> ExifTransform(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifTransform(180f, mirrorHorizontally = true)
            ExifInterface.ORIENTATION_TRANSPOSE -> ExifTransform(90f, mirrorHorizontally = true)
            ExifInterface.ORIENTATION_ROTATE_90 -> ExifTransform(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> ExifTransform(270f, mirrorHorizontally = true)
            ExifInterface.ORIENTATION_ROTATE_270 -> ExifTransform(270f)
            else -> ExifTransform(0f)
        }
    }
}
