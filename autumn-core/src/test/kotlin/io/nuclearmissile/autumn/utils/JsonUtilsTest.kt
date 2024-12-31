package io.nuclearmissile.autumn.utils

import io.nuclearmissile.autumn.utils.JsonUtils.readJson
import io.nuclearmissile.autumn.utils.JsonUtils.toJson
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

data class OrderBookItemBean(
    val price: BigDecimal, var quantity: BigDecimal
) {
    fun addQuantity(toAdd: BigDecimal) {
        quantity += toAdd
    }
}

class JsonUtilsTest {
    @Test
    fun testReadJson() {
        assertEquals(null, "null".readJson<String>())
        assertEquals(null, "null".readJson<Map<*, *>>())
        assertEquals("", "\"\"".readJson())
        assertEquals("aabbcc", "\"aabbcc\"".readJson())
        assertEquals(true, "true".readJson())
        assertEquals(123, "123".readJson())
        assertEquals(emptyMap<Any, Any>(), "{}".readJson())
        assertEquals(emptyList<Any>(), "[]".readJson())
        assertEquals(
            OrderBookItemBean(BigDecimal(Double.MAX_VALUE), BigDecimal(1)),
            "{\"price\":${BigDecimal(Double.MAX_VALUE).toPlainString()},\"quantity\":1}".readJson()
        )
        assertEquals(Long.MAX_VALUE, Long.MAX_VALUE.toString().readJson<Long>())
        assertEquals(Long.MIN_VALUE, Long.MIN_VALUE.toString().readJson<Long>())
        assertEquals(Double.MAX_VALUE, Double.MAX_VALUE.toString().readJson<Double>())
        assertEquals(Double.MIN_VALUE, Double.MIN_VALUE.toString().readJson<Double>())
    }

    @Test
    fun testToJson() {
        assertEquals("null", null.toJson())
        assertEquals("1", 1.toJson())
        assertEquals(
            "{\"price\":${BigDecimal(Double.MAX_VALUE).toPlainString()},\"quantity\":1}",
            OrderBookItemBean(BigDecimal(Double.MAX_VALUE), BigDecimal(1)).toJson()
        )
        assertEquals("{}", emptyMap<Any, Any>().toJson())
        assertEquals("[]", emptyList<Any>().toJson())
        assertEquals("true", true.toJson())
        assertEquals("\"aabbcc\"", "aabbcc".toJson())
        assertEquals("${Long.MAX_VALUE}", Long.MAX_VALUE.toJson())
        assertEquals("${Long.MIN_VALUE}", Long.MIN_VALUE.toJson())
        assertEquals("${Double.MAX_VALUE}", Double.MAX_VALUE.toJson())
        assertEquals("${Double.MIN_VALUE}", Double.MIN_VALUE.toJson())
    }
}