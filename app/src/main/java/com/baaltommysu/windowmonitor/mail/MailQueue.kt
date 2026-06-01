package com.baaltommysu.windowmonitor.mail

import android.content.Context
import com.baaltommysu.windowmonitor.storage.PhotoRepository
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
        repository.listPendingPhotos().forEach { photo ->
            try {
                store.lastSendTime = Instant.now().toString()
                sender.sendCameraReport(config, photo)
                repository.deletePhoto(photo)
                store.markSuccess()
            } catch (error: Exception) {
                allSent = false
                store.markFailure(error.message ?: "Mail send failed")
                return@forEach
            }
        }
        return allSent
    }
}
