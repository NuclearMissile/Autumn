package com.example.scan

import com.example.autumn.annotation.Autowired
import com.example.autumn.annotation.Component
import com.example.autumn.annotation.Value
import java.time.*

@Component
class ValueConverterBean @Autowired constructor(
    @Value("\${convert.short}")
    val injectedShortPrimitive: Short,
    @Value("\${convert.short}")
    val injectedShort: Short?,
    @Value("\${convert.integer}")
    val injectedIntPrimitive: Int,
    @Value("\${convert.integer}")
    val injectedInteger: Int?,
    @Value("\${convert.long}")
    val injectedLongPrimitive: Long,
    @Value("\${convert.long}")
    val injectedLong: Long?,
    @Value("\${convert.float}")
    val injectedFloatPrimitive: Float,
    @Value("\${convert.float}")
    val injectedFloat: Float?,
    @Value("\${convert.double}")
    val injectedDoublePrimitive: Double,
    @Value("\${convert.double}")
    val injectedDouble: Double?,
    @Value("\${convert.localdate}")
    val injectedLocalDate: LocalDate?,
    @Value("\${convert.localtime}")
    val injectedLocalTime: LocalTime?,
    @Value("\${convert.localdatetime}")
    val injectedLocalDateTime: LocalDateTime?,
    @Value("\${convert.zoneddatetime}")
    val injectedZonedDateTime: ZonedDateTime?,
    @Value("\${convert.duration}")
    val injectedDuration: Duration?,
    @Value("\${convert.zoneid}")
    val injectedZoneId: ZoneId?,
    val studentBean: StudentBean,
) {
    @Value("\${convert.boolean}")
    var injectedBooleanPrimitive: Boolean = true

    @Value("\${convert.boolean}")
    var injectedBoolean: Boolean? = null

    @Value("\${convert.byte}")
    var injectedBytePrimitive: Byte = 0

    @Value("\${convert.byte}")
    var injectedByte: Byte? = null

    @Autowired
    var teacherBean: TeacherBean? = null
}
