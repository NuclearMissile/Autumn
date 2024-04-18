package org.example.autumn.utils

import jakarta.servlet.ServletException
import org.example.autumn.servlet.DispatcherServlet.Companion.compilePath
import org.junit.jupiter.api.Assertions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathUtilsTest {
    @Test
    fun testValidPath() {
        val p0 = compilePath("/user/{userId}/{orderId}")
        val m0 = p0.matcher("/user/0/11")
        assertTrue(m0.matches())
        assertEquals("0", m0.group("userId"))
        assertEquals("11", m0.group(2))

        val m00 = p0.matcher("/user/123/a/456")
        assertFalse(m00.matches())

        val p1 = compilePath("/test/{a123dsdas}")
        val m1 = p1.matcher("/test/aaabbbccc")
        assertTrue(m1.matches())
        assertEquals("aaabbbccc", m1.group("a123dsdas"))
    }

    @Test
    fun testInvalidPath() {
        Assertions.assertThrows(ServletException::class.java) { compilePath("/empty/{}") }
        Assertions.assertThrows(ServletException::class.java) { compilePath("/start-with-digit/{123}") }
        Assertions.assertThrows(ServletException::class.java) { compilePath("/invalid-name/{abc-def}") }
        Assertions.assertThrows(ServletException::class.java) { compilePath("/missing-left/a}") }
        Assertions.assertThrows(ServletException::class.java) { compilePath("/missing-right/a}") }
    }
}