package com.baaltommysu.windowmonitor.storage

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PhotoRepository(context: Context) {
    private val photosDir = File(context.filesDir, "photos").apply { mkdirs() }

    fun createPhotoFile(): File {
        val timestamp = LocalDateTime.now().format(FileNameFormatter)
        return File(photosDir, "photo_$timestamp.jpg")
    }

    fun listPendingPhotos(): List<File> {
        return photosDir
            .listFiles { file -> file.isFile && file.extension.equals("jpg", ignoreCase = true) }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
    }

    fun trimCache(maxFiles: Int = 1000) {
        val files = listPendingPhotos()
        if (files.size <= maxFiles) return
        files.take(files.size - maxFiles).forEach { it.delete() }
    }

    companion object {
        private val FileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }
}
