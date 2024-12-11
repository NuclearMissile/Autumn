package org.example.autumn.server.component.support

import jakarta.servlet.Filter
import jakarta.servlet.Servlet

open class UrlMapping(private val url: String) {
    private val pattern: Regex = run {
        val sb = StringBuilder(url.length + 16)
        sb.append('^')
        url.forEach {
            when (it) {
                '*' -> sb.append(".*")
                in 'a'..'z', in 'A'..'Z', in '0'..'9' -> sb.append(it)
                else -> sb.append("\\").append(it)
            }
        }
        sb.append('$')
        Regex(sb.toString())
    }

    fun match(uri: String): Boolean {
        return pattern.matches(uri)
    }
}

class FilterMapping(val filter: Filter, url: String) : UrlMapping(url)

class ServletMapping(val servlet: Servlet, url: String) : UrlMapping(url)
