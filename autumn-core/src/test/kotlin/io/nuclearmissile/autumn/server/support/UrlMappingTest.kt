package io.nuclearmissile.autumn.server.support

import io.nuclearmissile.autumn.server.component.support.UrlMapping
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlMappingTest {
    @Test
    fun testMatch() {
        val all = UrlMapping("/*")
        assertTrue(all.match("/"))
        assertTrue(all.match("/a"))
        assertTrue(all.match("/abc/"))
        assertTrue(all.match("/abc/x.y.z"))
        assertTrue(all.match("/a-b-c"))
        assertTrue(all.match("/a/b/c.php"))

        val m = UrlMapping("/hello")
        assertTrue(m.match("/hello"))
        assertFalse(m.match("/hello/"))
        assertFalse(m.match("/hello/1"))

        val prefix = UrlMapping("/hello/*")
        assertTrue(prefix.match("/hello/"))
        assertTrue(prefix.match("/hello/1"))
        assertTrue(prefix.match("/hello/a%20c"))
        assertTrue(prefix.match("/hello/world/"))
        assertTrue(prefix.match("/hello/world/123"))
        assertFalse(prefix.match("/hello"))
        assertFalse(prefix.match("/Hello/"))
        assertFalse(prefix.match("/Hello"))

        val suffix = UrlMapping("*.php")
        assertTrue(suffix.match("/hello.php"))
        assertTrue(suffix.match("/hello/.php"))
        assertTrue(suffix.match("/hello/%25.php"))
        assertTrue(suffix.match("/hello/world/123.php"))
        assertFalse(suffix.match("/hello-php"))
        assertFalse(suffix.match("/hello.php1"))
        assertFalse(suffix.match("/php"))
    }
}