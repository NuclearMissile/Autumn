package org.example.autumn.utils

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME

object DateUtils {
    private val GMT: ZoneId = ZoneId.of("Z")

    fun parseDateTimeGMT(s: String): Long {
        return ZonedDateTime.parse(s, RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }

    fun formatDateTimeGMT(ts: Long): String {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), GMT).format(RFC_1123_DATE_TIME)
    }
}
