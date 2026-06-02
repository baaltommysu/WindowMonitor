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

        var allSent = true
        val pendingPhotos = repository.listPendingPhotos()
        AppLogger.d(Tag, "flush pending count=${pendingPhotos.size}")
        for (photo in pendingPhotos) {
            try {
                store.lastSendTime = Instant.now().toString()
                val response = sender.sendCameraReport(config, photo)
                val deletedRows = repository.deletePhoto(photo)
                AppLogger.d(Tag, "sent photo=${photo.name} response=$response deleteRows=$deletedRows")
                store.markSuccess()
            } catch (error: Exception) {
                allSent = false
                AppLogger.e(Tag, "mail send failed photo=${photo.name}", error)
                store.markFailure(error.message ?: "Mail send failed")
                break
            }
        }
        return allSent
    }

    companion object {
        private const val Tag = "MailQueue"
    }
}
