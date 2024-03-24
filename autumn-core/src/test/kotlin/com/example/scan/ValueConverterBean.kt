package com.example.scan

import com.example.autumn.annotation.Component
import com.example.autumn.annotation.Value
import java.time.*

@Component
class ValueConverterBean {
    @Value("\${convert.boolean}")
    var injectedBooleanPrimitive: Boolean = false

    @Value("\${convert.boolean}")
    var injectedBoolean: Boolean? = null

    @Value("\${convert.byte}")
    var injectedBytePrimitive: Byte = 0

    @Value("\${convert.byte}")
    var injectedByte: Byte? = null

    @Value("\${convert.short}")
    var injectedShortPrimitive: Short = 0

    @Value("\${convert.short}")
    var injectedShort: Short? = null

    @Value("\${convert.integer}")
    var injectedIntPrimitive: Int = 0

    @Value("\${convert.integer}")
    var injectedInteger: Int? = null

    @Value("\${convert.long}")
    var injectedLongPrimitive: Long = 0

    @Value("\${convert.long}")
    var injectedLong: Long? = null

    @Value("\${convert.float}")
    var injectedFloatPrimitive: Float = 0f

    @Value("\${convert.float}")
    var injectedFloat: Float? = null

    @Value("\${convert.double}")
    var injectedDoublePrimitive: Double = 0.0

    @Value("\${convert.double}")
    var injectedDouble: Double? = null

    @Value("\${convert.localdate}")
    var injectedLocalDate: LocalDate? = null

    @Value("\${convert.localtime}")
    var injectedLocalTime: LocalTime? = null

    @Value("\${convert.localdatetime}")
    var injectedLocalDateTime: LocalDateTime? = null

    @Value("\${convert.zoneddatetime}")
    var injectedZonedDateTime: ZonedDateTime? = null

    @Value("\${convert.duration}")
    var injectedDuration: Duration? = null

    @Value("\${convert.zoneid}")
    var injectedZoneId: ZoneId? = null
}
