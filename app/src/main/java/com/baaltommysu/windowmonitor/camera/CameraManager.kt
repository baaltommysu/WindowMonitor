package com.baaltommysu.windowmonitor.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.baaltommysu.windowmonitor.storage.CapturedPhoto
import com.baaltommysu.windowmonitor.storage.PhotoTarget
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraManager(private val context: Context) {
    suspend fun capturePhoto(owner: LifecycleOwner, target: PhotoTarget): CapturedPhoto {
        val cameraProvider = context.awaitCameraProvider()
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            owner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            imageCapture
        )

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
                        cameraProvider.unbindAll()
                        val uri = requireNotNull(outputFileResults.savedUri) {
                            "CameraX did not return saved image Uri"
                        }
                        continuation.resume(CapturedPhoto(uri = uri, name = target.name))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        cameraProvider.unbindAll()
                        continuation.resumeWithException(exception)
                    }
                }
            )
        }
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
}
