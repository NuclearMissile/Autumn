package org.example.autumn.orm.entity

import jakarta.persistence.*
import org.example.autumn.orm.EntityMixin
import org.example.autumn.orm.EntityMixin.Companion.PRECISION
import org.example.autumn.orm.EntityMixin.Companion.SCALE
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Entity
@Table(
    name = "ticks",
    uniqueConstraints = [UniqueConstraint(name = "UNI_T_M", columnNames = ["takerOrderId", "makerOrderId"])],
    indexes = [Index(name = "IDX_CAT", columnList = "createdAt")]
)
class TickEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    var id: Long,
    @Column(nullable = false, updatable = false)
    var sequenceId: Long,
    @Column(nullable = false, updatable = false)
    var takerOrderId: Long,
    @Column(nullable = false, updatable = false)
    var makerOrderId: Long,
    /**
     * Bit for taker direction: 1=LONG, 0=SHORT.
     */
    @Column(nullable = false, updatable = false)
    var takerDirection: Boolean,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var price: BigDecimal,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var quantity: BigDecimal,
    /**
     * Created time (milliseconds).
     */
    @Column(nullable = false, updatable = false)
    var createdAt: Long,
) : EntityMixin {
    fun toJson(): String {
        return "[$createdAt,${if (takerDirection) 1 else 0},$price,$quantity]"
    }
}

@MappedSuperclass
abstract class BarEntity(
    @Id
    @Column(nullable = false, updatable = false)
    var startTime: Long,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var openPrice: BigDecimal,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var highPrice: BigDecimal,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var lowPrice: BigDecimal,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var closePrice: BigDecimal,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var quantity: BigDecimal,
) : EntityMixin {
    fun toString(zoneId: ZoneId): String {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTime), zoneId)
        val time = FORMATTERS.getValue(javaClass.name).format(zdt)
        return "BarEntity(startTime=$time, openPrice=$openPrice, highPrice=$highPrice, lowPrice=$lowPrice, " +
                "closePrice=$closePrice, quantity=$quantity)"
    }

    override fun toString(): String {
        return toString(ZoneId.systemDefault())
    }

    companion object {
        private val FORMATTERS = mutableMapOf(
            "SecBarEntity" to DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US),
            "MinBarEntity" to DateTimeFormatter.ofPattern("dd HH:mm", Locale.US),
            "HourBarEntity" to DateTimeFormatter.ofPattern("MM-dd HH", Locale.US),
            "DayBarEntity" to DateTimeFormatter.ofPattern("yy-MM-dd", Locale.US),
        )
    }
}

@Entity
@Table(name = "sec_bars")
class SecBarEntity(
    startTime: Long, openPrice: BigDecimal, highPrice: BigDecimal, lowPrice: BigDecimal,
    closePrice: BigDecimal, quantity: BigDecimal
) : BarEntity(startTime, openPrice, highPrice, lowPrice, closePrice, quantity)

@Entity
@Table(name = "min_bars")
class MinBarEntity(
    startTime: Long, openPrice: BigDecimal, highPrice: BigDecimal, lowPrice: BigDecimal,
    closePrice: BigDecimal, quantity: BigDecimal
) : BarEntity(startTime, openPrice, highPrice, lowPrice, closePrice, quantity)

@Entity
@Table(name = "hour_bars")
class HourBarEntity(
    startTime: Long, openPrice: BigDecimal, highPrice: BigDecimal, lowPrice: BigDecimal,
    closePrice: BigDecimal, quantity: BigDecimal
) : BarEntity(startTime, openPrice, highPrice, lowPrice, closePrice, quantity)

@Entity
@Table(name = "day_bars")
class DayBarEntity(
    startTime: Long, openPrice: BigDecimal, highPrice: BigDecimal, lowPrice: BigDecimal,
    closePrice: BigDecimal, quantity: BigDecimal
) : BarEntity(startTime, openPrice, highPrice, lowPrice, closePrice, quantity)