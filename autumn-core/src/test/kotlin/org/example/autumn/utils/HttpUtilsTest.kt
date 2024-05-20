package org.example.autumn.utils

import org.example.autumn.utils.HttpUtils.escapeHtml
import org.example.autumn.utils.HttpUtils.parseAcceptLanguages
import org.example.autumn.utils.HttpUtils.parseCookies
import org.example.autumn.utils.HttpUtils.parseQuery
import java.net.URLEncoder
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpUtilsTest {
    @Test
    fun testEscapeHtml() {
        val html = "<h1>Hello & \"World!\"</h1>"
        val escaped = html.escapeHtml()
        assertEquals("&lt;h1&gt;Hello &amp; &quot;World!&quot;&lt;/h1&gt;", escaped)
    }

    @Test
    fun testParseAcceptLanguages() {
        val locales = parseAcceptLanguages("zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
        assertEquals("[zh_CN, zh, en_US, en]", locales.toString())
        val defaultLocals = parseAcceptLanguages("")
        assertEquals("[${Locale.getDefault()}]", defaultLocals.toString())
    }

    @Test
    fun testParseQuery() {
        val queryString = "k1=v1&k2=v2&k2=v3&k3=v3,v4,v5&k2=v6&k3=v7"
        val queryStringEncoded = URLEncoder.encode(queryString, Charsets.UTF_8)
        val query = parseQuery(queryString)
        assertEquals("{k1=[v1], k2=[v2, v3, v6], k3=[v3, v4, v5, v7]}", query.toString())
        val query2 = parseQuery(queryStringEncoded)
        assertEquals("{k1=[v1], k2=[v2, v3, v6], k3=[v3, v4, v5, v7]}", query2.toString())

        assertEquals("{}", parseQuery("").toString())
        assertEquals("{}", parseQuery("&").toString())
        assertEquals("{}", parseQuery("=").toString())
        assertEquals("{}", parseQuery("&=").toString())
    }

    @Test
    fun testParseCookies() {
        val cookies = "   yummy_cookie=choco; tasty_cookie=strawberry; a;   "
        assertEquals(
            "yummy_cookie:choco, tasty_cookie:strawberry, a:", parseCookies(cookies)!!.joinToString { "${it.name}:${it.value}" }
        )
        assertNull(parseCookies(""))
        assertNull(parseCookies(";"))
        assertNull(parseCookies("="))
        assertEquals("a:", parseCookies("a")!!.joinToString { "${it.name}:${it.value}" })
    }
}