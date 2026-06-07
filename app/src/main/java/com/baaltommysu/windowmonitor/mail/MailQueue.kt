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

    fun flushPending(action: String = "发送邮件"): Boolean {
        val config = SmtpConfig.from(store)
        if (!config.isConfigured) {
            val reason = "SMTP is not configured"
            store.markFailure(reason)
            store.appendLog(action, "失败，原因=$reason")
            return false
        }

        val pendingPhotos = repository.listPendingPhotos().take(MaxAttachmentsPerMail)
        AppLogger.d(Tag, "flush pending count=${pendingPhotos.size}")
        if (pendingPhotos.isEmpty()) {
            store.appendLog(action, "跳过，没有待发送照片")
            return true
        }

        return try {
            store.appendLog(action, "开始，照片数量=${pendingPhotos.size}，文件=${pendingPhotos.joinToString { it.name }}")
            val response = sender.sendCameraReport(config, pendingPhotos)
            repository.trimCache(MaxStoredPhotos)
            store.lastSendTime = Instant.now().toString()
            AppLogger.d(
                Tag,
                "sent photos=${pendingPhotos.joinToString { it.name }} response=$response"
            )
            store.markSuccess()
            store.appendLog(action, "SMTP已接收/排队，返回=$response，照片已保留；这不代表Gmail已投递")
            true
        } catch (error: Exception) {
            AppLogger.e(Tag, "mail send failed photos=${pendingPhotos.joinToString { it.name }}", error)
            val reason = error.message ?: "Mail send failed"
            store.markFailure(reason)
            store.appendLog(action, "失败，原因=$reason")
            false
        }
    }

    companion object {
        private const val Tag = "MailQueue"
        private const val MaxAttachmentsPerMail = 6
        private const val MaxStoredPhotos = 500
    }
}
