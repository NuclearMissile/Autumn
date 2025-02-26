package io.nuclearmissile.autumn.orm

import jakarta.persistence.AttributeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId


class EnumConverter<T : Enum<T>>(private val enumType: Class<T>) : AttributeConverter<Enum<T>, String> {
    override fun convertToDatabaseColumn(attribute: Enum<T>): String {
        return attribute.name
    }

    override fun convertToEntityAttribute(dbData: String): Enum<T> {
        return java.lang.Enum.valueOf(enumType, dbData)
    }
}

class LocalDateTimeConverter : AttributeConverter<LocalDateTime, java.util.Date> {
    companion object {
        private val SYSTEM_ZONE_ID = ZoneId.systemDefault()
    }

    override fun convertToDatabaseColumn(attritube: LocalDateTime): java.util.Date {
        return java.util.Date(attritube.atZone(SYSTEM_ZONE_ID).toEpochSecond() * 1000)
    }

    override fun convertToEntityAttribute(dbDate: java.util.Date): LocalDateTime {
        return Instant.ofEpochMilli(dbDate.time).atZone(SYSTEM_ZONE_ID).toLocalDateTime()
    }
}

class LocalDateConverter : AttributeConverter<LocalDate, java.sql.Date> {
    companion object {
        private val SYSTEM_ZONE_ID = ZoneId.systemDefault()
    }

    override fun convertToDatabaseColumn(attritube: LocalDate): java.sql.Date {
        return java.sql.Date(attritube.atStartOfDay(SYSTEM_ZONE_ID).toEpochSecond() * 1000)
    }

    override fun convertToEntityAttribute(dbData: java.sql.Date): LocalDate {
        return Instant.ofEpochMilli(dbData.time).atZone(SYSTEM_ZONE_ID).toLocalDate()
    }
}