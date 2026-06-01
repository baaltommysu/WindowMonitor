package com.baaltommysu.windowmonitor.mail

import com.baaltommysu.windowmonitor.util.PreferenceStore

data class SmtpConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val from: String,
    val to: String
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() &&
            port > 0 &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            from.isNotBlank() &&
            to.isNotBlank()

    companion object {
        fun from(store: PreferenceStore): SmtpConfig {
            return SmtpConfig(
                host = store.smtpHost,
                port = store.smtpPort,
                username = store.smtpUsername,
                password = store.smtpPassword,
                from = store.mailFrom,
                to = store.mailTo
            )
        }
    }
}
