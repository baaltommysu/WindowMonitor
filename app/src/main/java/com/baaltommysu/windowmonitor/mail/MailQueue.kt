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

        val pendingPhotos = repository.listPendingPhotosOldestFirst().take(MaxAttachmentsPerMail)
        AppLogger.d(Tag, "flush pending count=${pendingPhotos.size}")
        if (pendingPhotos.isEmpty()) {
            store.appendLog(action, "跳过，没有待发送照片")
            return true
        }

        return try {
            store.lastSendTime = Instant.now().toString()
            store.appendLog(action, "开始，照片数量=${pendingPhotos.size}，文件=${pendingPhotos.joinToString { it.name }}")
            val response = sender.sendCameraReport(config, pendingPhotos)
            val deletedRows = pendingPhotos.sumOf { repository.deletePhoto(it) }
            AppLogger.d(
                Tag,
                "sent photos=${pendingPhotos.joinToString { it.name }} response=$response deleteRows=$deletedRows"
            )
            store.markSuccess()
            store.appendLog(action, "成功，SMTP返回=$response，已删除照片数=$deletedRows")
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
        private const val MaxAttachmentsPerMail = 4
    }
}
