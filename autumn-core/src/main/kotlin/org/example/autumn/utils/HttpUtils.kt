package org.example.autumn.utils

import jakarta.servlet.http.Cookie
import org.example.autumn.DEFAULT_LOCALE
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.*

object HttpUtils {
    fun String.escapeHtml(): String {
        return this.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    fun normalizePath(path: String): String {
        val stack = ArrayDeque<String>()
        for (part in path.split('/')) {
            if (part.isEmpty() || part == ".") {
                continue
            } else if (part == ".." && stack.isNotEmpty()) {
                stack.pop()
            } else {
                stack.push(part)
            }
        }
        return stack.reversed().joinToString("/", prefix = "/")
    }

    fun parseAcceptLanguages(acceptLanguage: String): List<Locale> {
        // parse Accept-Language: zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7
        val ret = mutableListOf<Locale>()
        for (lang in acceptLanguage.split(",")) {
            val n = lang.indexOf(';')
            val name = if (n < 0) lang else lang.substring(0, n)
            val m = name.indexOf('-')
            if (m < 0) {
                if (name.isBlank()) continue
                ret.add(Locale.of(name))
            } else {
                if (name.substring(0, m).isBlank()) continue
                ret.add(Locale.of(name.substring(0, m), name.substring(m + 1)))
            }
        }
        return ret.ifEmpty { listOf(DEFAULT_LOCALE) }
    }

    fun parseQuery(query: String, charset: Charset = Charsets.UTF_8): Map<String, MutableList<String>> {
        if (query.isEmpty()) return emptyMap()
        val decodedQuery = URLDecoder.decode(query.trim(), charset)
        val ret = mutableMapOf<String, MutableList<String>>()
        for (q in decodedQuery.split('&')) {
            val n = q.indexOf('=')
            if (n >= 1) {
                val key = q.substring(0, n)
                val values = q.substring(n + 1).split(",")
                val exist = ret.getOrPut(key) { ArrayList(4) }
                exist.addAll(values)
            }
        }
        return ret
    }

    fun parseCookies(cookieValue: String?): Array<Cookie>? {
        val trimmed = cookieValue?.trim()?.trim(';')
        if (trimmed.isNullOrBlank()) return null
        val ret = mutableListOf<Cookie>()
        for (c in trimmed.split(";")) {
            val cookie = c.trim()
            val pos = cookie.indexOf('=')
            val name = if (pos >= 0) cookie.substring(0, pos) else cookie
            if (name.isEmpty()) continue
            val value = if (pos >= 0) cookie.substring(pos + 1) else ""
            ret.add(Cookie(name, value))
        }
        return if (ret.isEmpty()) null else ret.toTypedArray()
    }
}