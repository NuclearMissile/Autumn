package org.example.autumn.server.component.mapping

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue


class UrlMappingTest {
    @Test
    fun testMatch() {
        val all = UrlMapping("/*")
        assertTrue(all.matches("/"))
        assertTrue(all.matches("/a"))
        assertTrue(all.matches("/abc/"))
        assertTrue(all.matches("/abc/x.y.z"))
        assertTrue(all.matches("/a-b-c"))
        assertTrue(all.matches("/a/b/c.php"))

        val m = UrlMapping("/hello")
        assertTrue(m.matches("/hello"))
        assertFalse(m.matches("/hello/1"))

        val prefix = UrlMapping("/hello/*")
        assertTrue(prefix.matches("/hello/"))
        assertTrue(prefix.matches("/hello/1"))
        assertTrue(prefix.matches("/hello/a%20c"))
        assertTrue(prefix.matches("/hello/world/"))
        assertTrue(prefix.matches("/hello/world/123"))
        assertFalse(prefix.matches("/hello"))
        assertFalse(prefix.matches("/Hello/"))
        assertFalse(prefix.matches("/Hello"))

        val suffix = UrlMapping("*.php")
        assertTrue(suffix.matches("/hello.php"))
        assertTrue(suffix.matches("/hello/.php"))
        assertTrue(suffix.matches("/hello/%25.php"))
        assertTrue(suffix.matches("/hello/world/123.php"))
        assertFalse(suffix.matches("/hello-php"))
        assertFalse(suffix.matches("/hello.php1"))
        assertFalse(suffix.matches("/php"))

        val p1 = UrlMapping("/hello/world/*")
        val p2 = UrlMapping("/hello/*")
        val p3 = UrlMapping("/world/*")
        val p4 = UrlMapping("*.asp")
        val p5 = UrlMapping("*.php")
        val p6 = UrlMapping("/")
        val arr = arrayOf(p6, p5, p4, p3, p2, p1).sorted()
        assertSame(p1, arr[0])
        assertSame(p2, arr[1])
        assertSame(p3, arr[2])
        assertSame(p4, arr[3])
        assertSame(p5, arr[4])
        assertSame(p6, arr[5])
    }
}