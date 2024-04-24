package org.example.autumn.server.support

import org.example.autumn.server.component.support.UrlMapping
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class UrlMappingTest {
    @Test
    fun testMatch() {
        val all = UrlMapping("/*")
        assertFalse(all.matches("/"))
        assertTrue(all.matches("/a"))
        assertTrue(all.matches("/abc/"))
        assertTrue(all.matches("/abc/x.y.z"))
        assertTrue(all.matches("/a-b-c"))
        assertTrue(all.matches("/a/b/c.php"))

        val m = UrlMapping("/hello")
        assertTrue(m.matches("/hello"))
        assertFalse(m.matches("/hello/1"))

        val prefix = UrlMapping("/hello/*")
        assertFalse(prefix.matches("/hello/"))
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
    }
}