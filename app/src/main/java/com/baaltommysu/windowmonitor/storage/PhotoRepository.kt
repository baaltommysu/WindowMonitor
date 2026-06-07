package com.baaltommysu.windowmonitor.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val WatermarkPaddingPx = 24f
private const val WatermarkTextSizePx = 42f

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

class PhotoRepository(private val context: Context) {
    private val resolver = context.contentResolver

    fun createPhotoTarget(): PhotoTarget {
        val capturedAt = Instant.now()
        val timestamp = LocalDateTime.ofInstant(capturedAt, ZoneId.systemDefault()).format(FileNameFormatter)
        val name = "photo_$timestamp.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$DirectoryName")
            put(MediaStore.Images.Media.DATE_TAKEN, capturedAt.toEpochMilli())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        return PhotoTarget(
            collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues = values,
            name = name,
            capturedAt = capturedAt
        )
    }

    fun addTimestampOverlay(photo: CapturedPhoto, capturedAt: Instant) {
        val original = resolver.openInputStream(photo.uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("Could not decode captured photo")
        val bitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        if (bitmap !== original) original.recycle()

        val timestamp = WatermarkFormatter.format(capturedAt.atZone(ZoneId.systemDefault()))
        Canvas(bitmap).drawTimestamp(timestamp)
        resolver.openOutputStream(photo.uri, "wt")?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        } ?: throw IllegalStateException("Could not write timestamped photo")
        bitmap.recycle()
    }

    fun markPhotoReady(photo: CapturedPhoto) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        resolver.update(photo.uri, values, null, null)
    }

    fun deletePhoto(photo: StoredPhoto): Int {
        return resolver.delete(photo.uri, null, null)
    }

    fun openPhoto(photo: StoredPhoto) = resolver.openInputStream(photo.uri)

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
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH}=?"
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

    fun trimCache(maxFiles: Int = 1000) {
        val files = listPendingPhotosOldestFirst()
        if (files.size <= maxFiles) return
        files.take(files.size - maxFiles).forEach { deletePhoto(it) }
    }

    companion object {
        private const val DirectoryName = "WindowMonitor"
        private val FileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        private val WatermarkFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

private fun Canvas.drawTimestamp(timestamp: String) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = WatermarkTextSizePx
        style = Paint.Style.FILL
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }
    val bounds = Rect()
    textPaint.getTextBounds(timestamp, 0, timestamp.length, bounds)
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0)
        style = Paint.Style.FILL
    }
    val left = WatermarkPaddingPx
    val top = height - bounds.height() - WatermarkPaddingPx * 2
    val right = left + bounds.width() + WatermarkPaddingPx
    val bottom = height - WatermarkPaddingPx
    drawRect(left - WatermarkPaddingPx / 2, top, right, bottom, backgroundPaint)
    drawText(timestamp, left, bottom - WatermarkPaddingPx / 2, textPaint)
}
