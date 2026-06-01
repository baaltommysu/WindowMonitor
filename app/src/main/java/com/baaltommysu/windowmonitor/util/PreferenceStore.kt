package com.baaltommysu.windowmonitor.util

import android.content.Context
import java.time.Instant

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

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean(Keys.MonitoringEnabled, false)
        set(value) = prefs.edit().putBoolean(Keys.MonitoringEnabled, value).apply()

    var smtpHost: String
        get() = prefs.getString(Keys.SmtpHost, "smtp.gmail.com") ?: "smtp.gmail.com"
        set(value) = prefs.edit().putString(Keys.SmtpHost, value).apply()

    var smtpPort: Int
        get() = prefs.getInt(Keys.SmtpPort, 587)
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
        lastFailureReason = reason
    }

    fun markSuccess() {
        lastSuccessTime = Instant.now().toString()
        lastFailureReason = ""
    }

    private object Keys {
        const val LastPhotoTime = "last_photo_time"
        const val LastSendTime = "last_send_time"
        const val LastSuccessTime = "last_success_time"
        const val LastFailureReason = "last_failure_reason"
        const val MonitoringEnabled = "monitoring_enabled"
        const val SmtpHost = "smtp_host"
        const val SmtpPort = "smtp_port"
        const val SmtpUsername = "smtp_username"
        const val SmtpPassword = "smtp_password"
        const val MailFrom = "mail_from"
        const val MailTo = "mail_to"
    }
}
