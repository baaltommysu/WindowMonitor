package com.baaltommysu.windowmonitor.util

import java.time.Instant

object MailAuditFormatter {
    private const val MaxTokenLength = 64
    private const val MaxDetailLength = 600

    fun format(
        actualTime: Instant,
        source: String,
        event: String,
        plannedTime: Instant? = null,
        detail: String = ""
    ): String {
        return buildList {
            add("actual=${BeijingTime.formatAudit(actualTime)}")
            add("source=${source.toToken()}")
            add("event=${event.toToken()}")
            plannedTime?.let { add("planned=${BeijingTime.formatAudit(it)}") }
            detail.toDetail().takeIf { it.isNotBlank() }?.let { add("detail=$it") }
        }.joinToString(" | ")
    }

    private fun String.toToken(): String {
        return trim()
            .ifBlank { "unknown" }
            .replace(Regex("\\s+"), "-")
            .replace("|", "/")
            .take(MaxTokenLength)
    }

    private fun String.toDetail(): String {
        return replace('\r', ' ')
            .replace('\n', ' ')
            .replace("|", "/")
            .trim()
            .take(MaxDetailLength)
    }
}
