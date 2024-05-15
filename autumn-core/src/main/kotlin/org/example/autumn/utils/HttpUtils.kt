package org.example.autumn.utils

import com.sun.net.httpserver.Headers
import jakarta.servlet.http.Cookie
import org.example.autumn.DEFAULT_LOCALE
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.*
import java.util.regex.Pattern

object HttpUtils {
    private val QUERY_SPLIT = Pattern.compile("&")

    fun String.escapeHtml(): String {
        return this.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    fun parseAcceptLanguages(acceptLanguage: String): List<Locale> {
        // parse Accept-Language: zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7
        val ret = mutableListOf<Locale>()
        for (lang in acceptLanguage.split(",")) {
            val n = lang.indexOf(';')
            val name = if (n < 0) lang else lang.substring(0, n)
            val m = name.indexOf('-')
            if (m < 0) {
                ret.add(Locale.of(name))
            } else {
                ret.add(Locale.of(name.substring(0, m), name.substring(m + 1)))
            }
        }
        return ret.ifEmpty { listOf(DEFAULT_LOCALE) }
    }

    fun parseQuery(query: String, charset: Charset = Charsets.UTF_8): Map<String, MutableList<String>> {
        if (query.isEmpty()) return emptyMap()
        val ret = mutableMapOf<String, MutableList<String>>()
        for (q in query.split(QUERY_SPLIT)) {
            val n = q.indexOf('=')
            if (n >= 1) {
                val key = q.substring(0, n)
                val value = q.substring(n + 1)
                var exist = ret[key]
                if (exist == null) {
                    exist = ArrayList(4)
                    ret[key] = exist
                }
                exist.add(URLDecoder.decode(value, charset))
            }
        }
        return ret
    }

    fun getHeader(headers: Headers, name: String): String? {
        val values = headers[name]
        return if (values.isNullOrEmpty()) null else values.first()
    }

    fun parseCookies(cookieValue: String?): Array<Cookie>? {
        if (cookieValue.isNullOrBlank()) return null
        val cookies = cookieValue.trim().trim(';').split(";")
        return cookies.map {
            val cookie = it.trim()
            val pos = cookie.indexOf('=')
            val name = if (pos >= 0) cookie.substring(0, pos) else cookie
            val value = if (pos >= 0) cookie.substring(pos + 1) else ""
            Cookie(name, value)
        }.toTypedArray()
    }
}