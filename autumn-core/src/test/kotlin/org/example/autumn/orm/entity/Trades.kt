package org.example.autumn.orm.entity

import jakarta.persistence.*
import org.example.autumn.orm.EntityMixin
import org.example.autumn.orm.EntityMixin.Companion.PRECISION
import org.example.autumn.orm.EntityMixin.Companion.SCALE
import org.example.autumn.orm.EntityMixin.Companion.VAR_CHAR_10000
import org.example.autumn.orm.EntityMixin.Companion.VAR_CHAR_50
import org.example.autumn.orm.EntityMixin.Companion.VAR_ENUM
import java.math.BigDecimal

@Entity
@Table(name = "events", uniqueConstraints = [UniqueConstraint(name = "UNI_PREV_ID", columnNames = ["previousId"])])
class EventEntity(
    /**
     * Primary key: assigned.
     */
    @Id
    @Column(nullable = false, updatable = false)
    var sequenceId: Long,
    /**
     * Keep previous id. The previous id of first event is 0.
     */
    @Column(nullable = false, updatable = false)
    var previousId: Long,
    /**
     * JSON-encoded event data.
     */
    @Column(nullable = false, updatable = false, length = VAR_CHAR_10000)
    var data: String,
    @Column(nullable = false, updatable = false)
    var createdAt: Long,
) : EntityMixin {
    override fun toString(): String {
        return "EventEntity(sequenceId=$sequenceId, previousId=$previousId, data='$data', createdAt=$createdAt)"
    }
}

@Entity
@Table(
    name = "match_details",
    uniqueConstraints = [UniqueConstraint(name = "UNI_OID_COID", columnNames = ["orderId", "counterOrderId"])],
    indexes = [Index(name = "IDX_OID_CT", columnList = "orderId,createdAt")]
)
class MatchDetailEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    var id: Long,
    /**
     * SequenceId of the event which triggered this match detail.
     */
    @Column(nullable = false, updatable = false)
    var sequenceId: Long,
    @Column(nullable = false, updatable = false)
    var orderId: Long,
    @Column(nullable = false, updatable = false)
    /**
     * Counter order's user id.
     */
    var counterOrderId: Long,
    @Column(nullable = false, updatable = false)
    var userId: Long,
    @Column(nullable = false, updatable = false)
    var counterUserId: Long,
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var type: Int,
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var direction: Int,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var price: BigDecimal,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var quantity: BigDecimal,
    @Column(nullable = false, updatable = false)
    var createdAt: Long,
) : EntityMixin, Comparable<MatchDetailEntity> {
    /**
     * Compares this object with the specified object for order. Returns zero if this object is equal
     * to the specified [other] object, a negative number if it's less than [other], or a positive number
     * if it's greater than [other].
     */
    override fun compareTo(other: MatchDetailEntity): Int {
        val cmp = orderId.compareTo(other.orderId)
        return if (cmp != 0) cmp else counterOrderId.compareTo(other.counterOrderId)
    }
}

@Entity
@Table(name = "unique_events")
class UniqueEventEntity(
    @Id
    @Column(nullable = false, updatable = false, length = VAR_CHAR_50)
    var id: String,
    /**
     * Which event associated.
     */
    @Column(nullable = false, updatable = false)
    var sequenceId: Long,
    /**
     * Created time (milliseconds). Set after sequenced.
     */
    @Column(nullable = false, updatable = false)
    var createdAt: Long,
) : EntityMixin {
    override fun toString(): String {
        return "UniqueEventEntity(id='$id', sequenceId=$sequenceId, createdAt=$createdAt)"
    }
}

@Entity
@Table(name = "orders")
class OrderEntity(
    @Id
    @Column(nullable = false, updatable = false)
    var id: Long,
    @Column(nullable = false, updatable = false)
    var sequenceId: Long,
    @Column(nullable = false, updatable = false)
    var userId: Long,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var price: BigDecimal,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var quantity: BigDecimal,
    @Column(nullable = false, updatable = false)
    var createdAt: Long,

    @Column(nullable = false, updatable = false)
    var updatedAt: Long,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var unfilledQuantity: BigDecimal,
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var direction: Int,

    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var status: Int,
) : EntityMixin, Comparable<OrderEntity> {
    fun copy(): OrderEntity {
        synchronized(this) {
            return OrderEntity(
                id, sequenceId, userId, price, quantity, createdAt, updatedAt, unfilledQuantity,
                direction, status
            )
        }
    }

    fun update(unfilledQuantity: BigDecimal, status: Int, updatedAt: Long) {
        synchronized(this) {
            this.unfilledQuantity = unfilledQuantity
            this.status = status
            this.updatedAt = updatedAt
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other is OrderEntity) {
            return this.id == other.id
        }
        return false
    }

    override fun hashCode(): Int {
        return this.id.hashCode()
    }

    override fun compareTo(other: OrderEntity): Int {
        return compareValues(this.id, other.id)
    }

    override fun toString(): String {
        return "OrderEntity(id=$id, sequenceId=$sequenceId, userId=$userId, price=$price, quantity=$quantity, " +
                "createdAt=$createdAt, updatedAt=$updatedAt, unfilledQuantity=$unfilledQuantity, " +
                "direction=$direction, status=$status)"
    }
}

@Entity
@Table(
    name = "clearings",
    uniqueConstraints = [UniqueConstraint(
        name = "UNI_SEQ_ORD_CORD",
        columnNames = ["sequenceId", "orderId", "counterOrderId"]
    )]
)
class ClearingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    var id: Long,
    @Column(nullable = false, updatable = false)
    var sequenceId: Long,
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var orderId: Long,
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var counterOrderId: Long,
    @Column(nullable = false, updatable = false)
    var userId: Long,
    @Column(nullable = false, updatable = false)
    var counterUserId: Long,
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var direction: Int,
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var clearingType: Int,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var matchPrice: BigDecimal,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var matchQuantity: BigDecimal,
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var orderStatusAfterClearing: Int,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var orderUnfilledQuantityAfterClearing: BigDecimal,
    @Column(nullable = false, updatable = false)
    var createdAt: Long
) : EntityMixin {
    override fun toString(): String {
        return "ClearingEntity(id=$id, sequenceId=$sequenceId, orderId=$orderId, counterOrderId=$counterOrderId, " +
                "userId=$userId, counterUserId=$counterUserId, direction=$direction, clearingType=$clearingType, " +
                "matchPrice=$matchPrice, matchQuantity=$matchQuantity, orderStatusAfterClearing=$orderStatusAfterClearing, " +
                "orderUnfilledQuantityAfterClearing=$orderUnfilledQuantityAfterClearing, createdAt=$createdAt)"
    }
}

@Entity
@Table(name = "transfer_logs")
class TransferLogEntity(
    @Id
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var transferId: String,
    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    var asset: Int,
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    var amount: BigDecimal,
    @Column(nullable = false, updatable = false)
    var userId: Long,
    @Column(nullable = false, updatable = false)
    var createdAt: Long,
    @Column(nullable = false, length = VAR_ENUM)
    var type: String,
    @Column(nullable = false, length = VAR_ENUM)
    var status: String,
) : EntityMixin
