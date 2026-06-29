package com.baaltommysu.windowmonitor.util

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("window_monitor_state", Context.MODE_PRIVATE)

    var lastPhotoTime: String
        get() = prefs.getString(Keys.LastPhotoTime, "") ?: ""
        set(value) = prefs.edit().putString(Keys.LastPhotoTime, value).apply()

    var lastSendTime: String
        get() = prefs.getString(Keys.LastSendTime, "") ?: ""
        set(value) = prefs.edit().putString(Keys.LastSendTime, value).apply()

    var lastSuccessTime: String
        get() = prefs.getString(Keys.LastSuccessTime, "") ?: ""
        set(value) = prefs.edit().putString(Keys.LastSuccessTime, value).apply()

    var lastFailureReason: String
        get() = prefs.getString(Keys.LastFailureReason, "") ?: ""
        set(value) = prefs.edit().putString(Keys.LastFailureReason, value).apply()

    var lastFailureTime: String
        get() = prefs.getString(Keys.LastFailureTime, "") ?: ""
        set(value) = prefs.edit().putString(Keys.LastFailureTime, value).apply()

    var operationLog: String
        get() = formatOperationLog(prefs.getString(Keys.OperationLog, "") ?: "")
        set(value) = prefs.edit().putString(Keys.OperationLog, value).apply()

    var mailAuditLog: String
        get() = prefs.getString(Keys.MailAuditLog, "") ?: ""
        set(value) = prefs.edit().putString(Keys.MailAuditLog, value).apply()

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean(Keys.MonitoringEnabled, false)
        set(value) = prefs.edit().putBoolean(Keys.MonitoringEnabled, value).apply()

    var mailDeliveryEnabled: Boolean
        get() = prefs.getBoolean(Keys.MailDeliveryEnabled, false)
        set(value) = prefs.edit().putBoolean(Keys.MailDeliveryEnabled, value).apply()

    var captureIntervalMinutes: Int
        get() = prefs.getInt(Keys.CaptureIntervalMinutes, 30)
        set(value) = prefs.edit().putInt(Keys.CaptureIntervalMinutes, value.coerceAtLeast(1)).apply()

    var mailIntervalMinutes: Int
        get() = prefs.getInt(Keys.MailIntervalMinutes, 120)
        set(value) = prefs.edit().putInt(Keys.MailIntervalMinutes, value.coerceAtLeast(15)).apply()

    var smtpHost: String
        get() = prefs.getString(Keys.SmtpHost, "smtp.sina.com") ?: "smtp.sina.com"
        set(value) = prefs.edit().putString(Keys.SmtpHost, value).apply()

    var smtpPort: Int
        get() = prefs.getInt(Keys.SmtpPort, 465)
        set(value) = prefs.edit().putInt(Keys.SmtpPort, value).apply()

    var smtpUsername: String
        get() = prefs.getString(Keys.SmtpUsername, "") ?: ""
        set(value) = prefs.edit().putString(Keys.SmtpUsername, value).apply()

    var smtpPassword: String
        get() = prefs.getString(Keys.SmtpPassword, "") ?: ""
        set(value) = prefs.edit().putString(Keys.SmtpPassword, value).apply()

    var mailFrom: String
        get() = prefs.getString(Keys.MailFrom, smtpUsername) ?: smtpUsername
        set(value) = prefs.edit().putString(Keys.MailFrom, value).apply()

    var mailTo: String
        get() = prefs.getString(Keys.MailTo, "") ?: ""
        set(value) = prefs.edit().putString(Keys.MailTo, value).apply()

    fun markFailure(reason: String) {
        lastFailureTime = Instant.now().toString()
        lastFailureReason = reason
    }

    fun markSuccess() {
        lastSuccessTime = Instant.now().toString()
        lastFailureReason = ""
    }

    fun appendLog(action: String, result: String) {
        val entry = "${formatForDisplay(Instant.now())} | $action | $result"
        val currentLog = prefs.getString(Keys.OperationLog, "") ?: ""
        operationLog = (listOf(entry) + formatOperationLog(currentLog).lines().filter { it.isNotBlank() })
            .take(MaxLogLines)
            .joinToString("\n")
    }

    fun appendMailAudit(
        source: String,
        event: String,
        detail: String = "",
        plannedTime: Instant? = null,
        actualTime: Instant = Instant.now()
    ) {
        val entry = MailAuditFormatter.format(
            actualTime = actualTime,
            source = source,
            event = event,
            plannedTime = plannedTime,
            detail = detail
        )
        val currentLog = prefs.getString(Keys.MailAuditLog, "") ?: ""
        mailAuditLog = (listOf(entry) + currentLog.lines().filter { it.isNotBlank() })
            .take(MaxMailAuditLines)
            .joinToString("\n")
    }

    private fun formatOperationLog(log: String): String {
        return log.lines().joinToString("\n") { line ->
            val separator = " | "
            val index = line.indexOf(separator)
            if (index <= 0) return@joinToString line
            val timestamp = line.substring(0, index)
            val formatted = runCatching { formatForDisplay(Instant.parse(timestamp)) }.getOrNull()
            if (formatted == null) line else formatted + line.substring(index)
        }
    }

    private fun formatForDisplay(instant: Instant): String {
        return DisplayFormatter.format(instant.atZone(ZoneId.systemDefault()))
    }

    private object Keys {
        const val LastPhotoTime = "last_photo_time"
        const val LastSendTime = "last_send_time"
        const val LastSuccessTime = "last_success_time"
        const val LastFailureReason = "last_failure_reason"
        const val LastFailureTime = "last_failure_time"
        const val OperationLog = "operation_log"
        const val MailAuditLog = "mail_audit_log"
        const val MonitoringEnabled = "monitoring_enabled"
        const val MailDeliveryEnabled = "mail_delivery_enabled"
        const val CaptureIntervalMinutes = "capture_interval_minutes"
        const val MailIntervalMinutes = "mail_interval_minutes"
        const val SmtpHost = "smtp_host"
        const val SmtpPort = "smtp_port"
        const val SmtpUsername = "smtp_username"
        const val SmtpPassword = "smtp_password"
        const val MailFrom = "mail_from"
        const val MailTo = "mail_to"
    }

    companion object {
        private const val MaxLogLines = 80
        private const val MaxMailAuditLines = 360
        private val DisplayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
