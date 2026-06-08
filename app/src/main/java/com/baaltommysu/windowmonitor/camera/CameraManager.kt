package com.baaltommysu.windowmonitor.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.baaltommysu.windowmonitor.storage.CapturedPhoto
import com.baaltommysu.windowmonitor.storage.PhotoTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraManager(private val context: Context) {
    suspend fun capturePhoto(owner: LifecycleOwner, target: PhotoTarget): CapturedPhoto {
        val cameraProvider = context.awaitCameraProvider()
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
        val warmedFrames = AtomicInteger(0)
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(ContextCompat.getMainExecutor(context)) { image ->
                    warmedFrames.incrementAndGet()
                    image.close()
                }
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                owner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                imageCapture,
                analysis
            )
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
        private const val WarmupFrameCount = 12
        private const val FramePollMillis = 80L
        private const val FrameWarmupTimeoutMillis = 4_000L
        private const val ExposureSettleMillis = 1_200L
    }
}
