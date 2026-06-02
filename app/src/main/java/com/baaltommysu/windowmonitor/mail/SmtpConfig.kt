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
            val host = normalizeHost(store.smtpHost)
            return SmtpConfig(
                host = host,
                port = normalizePort(host, store.smtpPort),
                username = store.smtpUsername,
                password = store.smtpPassword,
                from = store.mailFrom,
                to = store.mailTo
            )
        }

        private fun normalizeHost(host: String): String {
            return if (host.equals("smtp.sina.com.cn", ignoreCase = true)) {
                "smtp.sina.com"
            } else {
                host
            }
        }

        private fun normalizePort(host: String, port: Int): Int {
            return if (host.contains("sina.com", ignoreCase = true) && port == 587) {
                465
            } else {
                port
            }
        }
    }
}
