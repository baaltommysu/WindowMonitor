package com.baaltommysu.windowmonitor.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.time.Instant
import com.baaltommysu.windowmonitor.util.BeijingTime

data class PhotoTarget(
    val collectionUri: Uri,
    val contentValues: ContentValues,
    val name: String,
    val capturedAt: Instant
)

data class CapturedPhoto(
    val uri: Uri,
    val name: String
)

data class StoredPhoto(
    val uri: Uri,
    val name: String,
    val lastModifiedMillis: Long,
    val sizeBytes: Long
)

data class PhotoQuality(
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val meanBrightness: Int,
    val darkRatioPercent: Int,
    val brightRatioPercent: Int
) {
    val isUsable: Boolean
        get() = sizeBytes >= MinSizeBytes &&
            minOf(width, height) >= MinSidePx &&
            brightRatioPercent < MaxBrightRatioPercent &&
            meanBrightness <= MaxMeanBrightness

    val summary: String
        get() = "size=${sizeBytes}B,dim=${width}x$height,mean=$meanBrightness,dark=${darkRatioPercent}%,bright=${brightRatioPercent}%"

    companion object {
        private const val MinSizeBytes = 60_000L
        private const val MinSidePx = 480
        private const val MaxBrightRatioPercent = 70
        private const val MaxMeanBrightness = 242
    }
}

class PhotoRepository(private val context: Context) {
    private val resolver = context.contentResolver

    fun createPhotoTarget(): PhotoTarget {
        val capturedAt = Instant.now()
        val timestamp = BeijingTime.formatFileName(capturedAt)
        val name = "photo_$timestamp.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$DirectoryName")
            put(MediaStore.Images.Media.DATE_TAKEN, capturedAt.toEpochMilli())
        }
        return PhotoTarget(
            collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues = values,
            name = name,
            capturedAt = capturedAt
        )
    }

    fun addTimestampOverlay(photo: CapturedPhoto, capturedAt: Instant) {
        val uri = resolvePhotoUri(photo)
        val original = openPhotoInputStream(uri, photo.name)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("Could not decode captured photo")
        val oriented = original.orientForExif(readExifOrientation(uri, photo.name))
        if (oriented !== original) original.recycle()
        val bitmap = oriented.copy(Bitmap.Config.ARGB_8888, true)
        if (bitmap !== oriented) oriented.recycle()

        val timestamp = BeijingTime.format(capturedAt)
        Canvas(bitmap).drawTimestamp(timestamp)
        openPhotoOutputStream(uri, photo.name)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        } ?: throw IllegalStateException("Could not write timestamped photo")
        bitmap.recycle()
    }

    fun analyzePhoto(photo: CapturedPhoto): PhotoQuality {
        val uri = resolvePhotoUri(photo)
        val sizeBytes = readPhotoSize(uri, photo.name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = openPhotoInputStream(uri, photo.name)
            ?: throw IllegalStateException("Could not open captured photo")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Could not read captured photo dimensions" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateQualitySampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = openPhotoInputStream(uri, photo.name)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalStateException("Could not decode captured photo")
        return try {
            bitmap.measureQuality(
                sizeBytes = sizeBytes,
                originalWidth = bounds.outWidth,
                originalHeight = bounds.outHeight
            )
        } finally {
            bitmap.recycle()
        }
    }

    fun markPhotoReady(photo: CapturedPhoto) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val uri = resolvePhotoUri(photo)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        resolver.update(uri, values, null, null)
    }

    fun deletePhoto(photo: StoredPhoto): Int {
        return resolver.delete(photo.uri, null, null)
    }

    fun deletePhoto(photo: CapturedPhoto): Int {
        return resolver.delete(photo.uri, null, null)
    }

    fun openPhoto(photo: StoredPhoto) = openPhotoInputStream(photo.uri, photo.name)

    fun listPendingPhotos(): List<StoredPhoto> {
        return listPendingPhotos("${MediaStore.Images.Media.DATE_MODIFIED} DESC")
    }

    fun listPendingPhotosOldestFirst(): List<StoredPhoto> {
        return listPendingPhotos("${MediaStore.Images.Media.DATE_MODIFIED} ASC")
    }

    private fun listPendingPhotos(sortOrder: String): List<StoredPhoto> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE
        )
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH}=? AND ${MediaStore.Images.Media.IS_PENDING}=0"
        } else {
            "${MediaStore.Images.Media.RELATIVE_PATH}=?"
        }
        val args = arrayOf("${Environment.DIRECTORY_PICTURES}/$DirectoryName/")

        return resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    add(
                        StoredPhoto(
                            uri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            ),
                            name = cursor.getString(nameColumn),
                            lastModifiedMillis = cursor.getLong(modifiedColumn) * 1000,
                            sizeBytes = cursor.getLong(sizeColumn)
                        )
                    )
                }
            }
        }.orEmpty()
    }

    fun openPhotoInputStream(uri: Uri): InputStream? {
        return openPhotoInputStream(uri, name = null)
    }

    private fun openPhotoInputStream(uri: Uri, name: String?): InputStream? {
        return runCatching {
            resolver.openFileDescriptor(uri, "r")?.let(ParcelFileDescriptor::AutoCloseInputStream)
        }.getOrNull()
            ?: openPhotoFileInputStream(name)
    }

    private fun openPhotoOutputStream(uri: Uri, name: String): java.io.OutputStream? {
        return resolver.openOutputStream(uri, "wt") ?: openPhotoFileOutputStream(name)
    }

    private fun openPhotoFileInputStream(name: String?): InputStream? {
        val file = name?.let(::photoFile) ?: return null
        return if (file.exists() && file.length() > 0) FileInputStream(file) else null
    }

    private fun openPhotoFileOutputStream(name: String): FileOutputStream? {
        val file = photoFile(name)
        file.parentFile?.mkdirs()
        return FileOutputStream(file, false)
    }

    private fun resolvePhotoUri(photo: CapturedPhoto): Uri {
        if (canOpen(photo.uri)) return photo.uri
        return findUriByName(photo.name) ?: photo.uri
    }

    private fun canOpen(uri: Uri): Boolean {
        return runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    private fun findUriByName(name: String): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH}=? AND ${MediaStore.Images.Media.DISPLAY_NAME}=?"
        val args = arrayOf("${Environment.DIRECTORY_PICTURES}/$DirectoryName/", name)
        return resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn))
        }
    }

    private fun readPhotoSize(uri: Uri, name: String): Long {
        val projection = arrayOf(MediaStore.Images.Media.SIZE)
        val queriedSize = resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            cursor.getLong(sizeColumn)
        }
        if (queriedSize != null && queriedSize > 0) return queriedSize
        val fileSize = photoFile(name).takeIf { it.exists() }?.length()
        if (fileSize != null && fileSize > 0) return fileSize
        return runCatching { resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L }
            .getOrDefault(0L)
    }

    private fun readExifOrientation(uri: Uri, name: String): Int {
        val contentOrientation = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                ExifInterface(descriptor.fileDescriptor).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull()
        if (contentOrientation != null) return contentOrientation

        return runCatching {
            ExifInterface(photoFile(name).absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    fun deleteOldestPhotosIfStorageLow(): Int {
        if (!isStorageLow()) return 0

        var deletedCount = 0
        for (photo in listPendingPhotosOldestFirst()) {
            if (!isStorageLow()) break
            if (deletePhoto(photo) > 0) {
                deletedCount += 1
            }
        }
        return deletedCount
    }

    companion object {
        private const val DirectoryName = "WindowMonitor"
    }
}

private fun photoFile(name: String): File {
    return File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "WindowMonitor/$name"
    )
}

private fun isStorageLow(): Boolean {
    val storageRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    return StorageCleanupPolicy.shouldDeleteOldestPhotos(
        freeBytes = storageRoot.freeSpace,
        totalBytes = storageRoot.totalSpace
    )
}

private fun calculateQualitySampleSize(width: Int, height: Int): Int {
    var sampleSize = 1
    var sampledWidth = width
    var sampledHeight = height
    while (sampledWidth / 2 >= 320 && sampledHeight / 2 >= 320) {
        sampleSize *= 2
        sampledWidth /= 2
        sampledHeight /= 2
    }
    return sampleSize
}

private fun Bitmap.measureQuality(
    sizeBytes: Long,
    originalWidth: Int,
    originalHeight: Int
): PhotoQuality {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    var total = 0L
    var dark = 0
    var bright = 0
    pixels.forEach { color ->
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val luminance = ((red * 299) + (green * 587) + (blue * 114)) / 1000
        total += luminance
        if (luminance <= 12) dark += 1
        if (luminance >= 244) bright += 1
    }
    val count = pixels.size.coerceAtLeast(1)
    return PhotoQuality(
        sizeBytes = sizeBytes,
        width = originalWidth,
        height = originalHeight,
        meanBrightness = (total / count).toInt(),
        darkRatioPercent = dark * 100 / count,
        brightRatioPercent = bright * 100 / count
    )
}

private fun Bitmap.orientForExif(orientation: Int): Bitmap {
    val transform = ExifOrientationPolicy.transformFor(orientation)
    if (!transform.requiresTransform) return this

    val matrix = Matrix().apply {
        setRotate(transform.rotationDegrees)
        if (transform.mirrorHorizontally) {
            postScale(-1f, 1f)
        }
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Canvas.drawTimestamp(timestamp: String) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = WatermarkStyle.TextSizePx
        style = Paint.Style.FILL
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }
    val bounds = Rect()
    textPaint.getTextBounds(timestamp, 0, timestamp.length, bounds)
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0)
        style = Paint.Style.FILL
    }
    val left = WatermarkStyle.PaddingPx
    val top = height - bounds.height() - WatermarkStyle.PaddingPx * 2
    val right = left + bounds.width() + WatermarkStyle.PaddingPx
    val bottom = height - WatermarkStyle.PaddingPx
    drawRect(left - WatermarkStyle.PaddingPx / 2, top, right, bottom, backgroundPaint)
    drawText(timestamp, left, bottom - WatermarkStyle.PaddingPx / 2, textPaint)
}
