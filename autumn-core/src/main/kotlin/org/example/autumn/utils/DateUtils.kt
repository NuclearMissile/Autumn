package org.example.autumn.utils

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object DateUtils {
    private val GMT: ZoneId = ZoneId.of("Z")

    fun parseDateTimeGMT(s: String): Long {
        val zdt = ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME)
        return zdt.toInstant().toEpochMilli()
    }

    fun formatDateTimeGMT(ts: Long): String {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), GMT)
        return zdt.format(DateTimeFormatter.RFC_1123_DATE_TIME)
    }
}
