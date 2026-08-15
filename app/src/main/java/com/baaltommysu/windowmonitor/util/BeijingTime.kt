package com.baaltommysu.windowmonitor.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object BeijingTime {
    val Zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private val DisplayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val FileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val AuditFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")
    private val Rfc1123Formatter = DateTimeFormatter.RFC_1123_DATE_TIME

    fun format(instant: Instant): String {
        return DisplayFormatter.format(instant.atZone(Zone))
    }

    fun formatFileName(instant: Instant): String {
        return FileNameFormatter.format(instant.atZone(Zone))
    }

    fun formatAudit(instant: Instant): String {
        return AuditFormatter.format(instant.atZone(Zone))
    }

    fun formatRfc1123(instant: Instant): String {
        return Rfc1123Formatter.format(instant.atZone(Zone))
    }
}
