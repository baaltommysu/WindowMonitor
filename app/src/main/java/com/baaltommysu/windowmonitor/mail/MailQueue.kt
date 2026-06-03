package com.baaltommysu.windowmonitor.mail

import android.content.Context
import com.baaltommysu.windowmonitor.storage.PhotoRepository
import com.baaltommysu.windowmonitor.util.AppLogger
import com.baaltommysu.windowmonitor.util.PreferenceStore
import java.time.Instant

class MailQueue(private val context: Context) {
    private val store = PreferenceStore(context)
    private val repository = PhotoRepository(context)
    private val sender = SmtpSender(context)

    fun flushPending(): Boolean {
        val config = SmtpConfig.from(store)
        if (!config.isConfigured) {
            store.markFailure("SMTP is not configured")
            return false
        }

        val pendingPhotos = repository.listPendingPhotosOldestFirst().take(MaxAttachmentsPerMail)
        AppLogger.d(Tag, "flush pending count=${pendingPhotos.size}")
        if (pendingPhotos.isEmpty()) return true

        return try {
            store.lastSendTime = Instant.now().toString()
            val response = sender.sendCameraReport(config, pendingPhotos)
            val deletedRows = pendingPhotos.sumOf { repository.deletePhoto(it) }
            AppLogger.d(
                Tag,
                "sent photos=${pendingPhotos.joinToString { it.name }} response=$response deleteRows=$deletedRows"
            )
            store.markSuccess()
            true
        } catch (error: Exception) {
            AppLogger.e(Tag, "mail send failed photos=${pendingPhotos.joinToString { it.name }}", error)
            store.markFailure(error.message ?: "Mail send failed")
            false
        }
    }

    companion object {
        private const val Tag = "MailQueue"
        private const val MaxAttachmentsPerMail = 4
    }
}
