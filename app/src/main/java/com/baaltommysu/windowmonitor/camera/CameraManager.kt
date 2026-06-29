package com.baaltommysu.windowmonitor.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.SensorManager
import android.os.Build
import android.util.Range
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.baaltommysu.windowmonitor.storage.CapturedPhoto
import com.baaltommysu.windowmonitor.storage.PhotoTarget
import com.baaltommysu.windowmonitor.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraManager(private val context: Context) {
    suspend fun capturePhoto(owner: LifecycleOwner, target: PhotoTarget): CapturedPhoto {
        val cameraProvider = context.awaitCameraProvider()
        val targetRotation = context.awaitTargetRotation()
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetRotation(targetRotation)
            .build()
        imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
        val warmedFrames = AtomicInteger(0)
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(targetRotation)
            .build()
            .apply {
                setAnalyzer(ContextCompat.getMainExecutor(context)) { image ->
                    warmedFrames.incrementAndGet()
                    image.close()
                }
            }

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                owner,
                widestBackCameraSelector(),
                imageCapture,
                analysis
            )
            camera.applyPreferredWideZoom()
            waitForWarmFrames(warmedFrames)

            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                target.collectionUri,
                target.contentValues
            ).build()
            return suspendCoroutine { continuation ->
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            val uri = requireNotNull(outputFileResults.savedUri) {
                                "CameraX did not return saved image Uri"
                            }
                            continuation.resume(CapturedPhoto(uri = uri, name = target.name))
                        }

                        override fun onError(exception: ImageCaptureException) {
                            continuation.resumeWithException(exception)
                        }
                    }
                )
            }
        } finally {
            cameraProvider.unbindAll()
        }
    }

    private suspend fun waitForWarmFrames(warmedFrames: AtomicInteger) {
        withTimeoutOrNull(FrameWarmupTimeoutMillis) {
            while (warmedFrames.get() < WarmupFrameCount) {
                delay(FramePollMillis)
            }
            true
        }
        delay(ExposureSettleMillis)
    }

    private fun widestBackCameraSelector(): CameraSelector {
        return CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .addCameraFilter { cameraInfos ->
                val candidates = cameraInfos.mapNotNull { cameraInfo ->
                    cameraInfo.toLensCandidateOrNull()?.let { candidate ->
                        candidate to cameraInfo
                    }
                }
                val selected = CameraLensSelection.chooseWidestBackCamera(candidates.map { it.first })
                val selectedInfo = selected?.let { candidate ->
                    candidates.firstOrNull { it.first.cameraId == candidate.cameraId }?.second
                }
                if (selected == null || selectedInfo == null) {
                    AppLogger.d(Tag, "wide camera selection fallback: no usable back camera metadata")
                    cameraInfos
                } else {
                    AppLogger.d(
                        Tag,
                        "selected back camera id=${selected.cameraId} " +
                            "focal=${selected.focalLengthsMm.joinToString()} " +
                            "minZoom=${selected.minZoomRatio ?: 1f} " +
                            "effectiveWideFocal=${selected.effectiveWideFocalLengthMm}"
                    )
                    listOf(selectedInfo)
                }
            }
            .build()
    }

    private fun Camera.applyPreferredWideZoom() {
        val candidate = cameraInfo.toLensCandidateOrNull() ?: return
        val zoomRatio = CameraLensSelection.preferredZoomRatio(candidate) ?: return
        val zoomRequest = cameraControl.setZoomRatio(zoomRatio)
        zoomRequest.addListener(
            {
                runCatching { zoomRequest.get() }
                    .onSuccess {
                        AppLogger.d(Tag, "applied wide zoom ratio=$zoomRatio camera=${candidate.cameraId}")
                    }
                    .onFailure { error ->
                        AppLogger.e(Tag, "wide zoom request failed camera=${candidate.cameraId}", error)
                    }
            },
            ContextCompat.getMainExecutor(context)
        )
        AppLogger.d(Tag, "requested wide zoom ratio=$zoomRatio camera=${candidate.cameraId}")
    }

    @androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun CameraInfo.toLensCandidateOrNull(): CameraLensCandidate? {
        return runCatching {
            val camera2Info = Camera2CameraInfo.from(this)
            val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
            val focalLengths = camera2Info
                .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.toList()
                .orEmpty()
            val minZoomRatio = camera2Info.zoomRatioRangeOrNull()?.lower
            CameraLensCandidate(
                cameraId = camera2Info.cameraId,
                isBackFacing = lensFacing == CameraCharacteristics.LENS_FACING_BACK,
                focalLengthsMm = focalLengths,
                minZoomRatio = minZoomRatio,
            )
        }.onFailure { error ->
            AppLogger.e(Tag, "could not read camera metadata", error)
        }.getOrNull()
    }

    @androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun Camera2CameraInfo.zoomRatioRangeOrNull(): Range<Float>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getCameraCharacteristic(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else {
            null
        }
    }

    private suspend fun Context.awaitTargetRotation(): Int {
        val displayRotation = getSystemService(WindowManager::class.java)
            ?.defaultDisplay
            ?.rotation
            ?: Surface.ROTATION_0
        return withTimeoutOrNull(OrientationReadTimeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : OrientationEventListener(this@awaitTargetRotation, SensorManager.SENSOR_DELAY_NORMAL) {
                    override fun onOrientationChanged(orientation: Int) {
                        val targetRotation = CaptureRotationPolicy.targetRotationFor(orientation) ?: return
                        if (continuation.isActive) {
                            disable()
                            continuation.resume(targetRotation)
                        }
                    }
                }
                continuation.invokeOnCancellation { listener.disable() }
                if (listener.canDetectOrientation()) {
                    listener.enable()
                } else {
                    continuation.resume(displayRotation)
                }
            }
        } ?: displayRotation
    }

    private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider {
        return suspendCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(this)
            future.addListener(
                {
                    try {
                        continuation.resume(future.get())
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                },
                ContextCompat.getMainExecutor(this)
            )
        }
    }

    companion object {
        private const val Tag = "CameraManager"
        private const val WarmupFrameCount = 12
        private const val FramePollMillis = 80L
        private const val FrameWarmupTimeoutMillis = 4_000L
        private const val ExposureSettleMillis = 1_200L
        private const val OrientationReadTimeoutMillis = 750L
    }
}
