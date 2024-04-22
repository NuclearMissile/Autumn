package org.example.autumn.server.component.support

import com.sun.net.httpserver.Headers
import org.example.autumn.utils.DateUtils
import java.time.format.DateTimeParseException

class HttpHeaders(private val headers: Headers) {
    fun addDateHeader(name: String, date: Long) {
        val strDate = DateUtils.formatDateTimeGMT(date)
        addHeader(name, strDate)
    }

    fun addHeader(name: String, value: String) {
        headers.add(name, value)
    }

    fun addIntHeader(name: String, value: Int) {
        addHeader(name, value.toString())
    }

    fun containsHeader(name: String): Boolean {
        return !headers[name].isNullOrEmpty()
    }

    fun getDateHeader(name: String): Long {
        val value = getHeader(name) ?: return -1
        try {
            return DateUtils.parseDateTimeGMT(value)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Cannot parse date header: $value")
        }
    }

    fun getIntHeader(name: String): Int {
        return getHeader(name)?.toInt() ?: -1
    }

    fun getHeader(name: String): String? {
        val values = headers[name]
        return if (values.isNullOrEmpty()) null else values.first()
    }

    fun getHeaders(name: String): List<String>? {
        return headers[name]
    }

    fun getHeaderNames(): Set<String> {
        return headers.keys
    }

    fun setDateHeader(name: String, date: Long) {
        setHeader(name, DateUtils.formatDateTimeGMT(date))
    }

    fun setHeader(name: String, value: String) {
        headers[name] = value
    }

    fun setIntHeader(name: String, value: Int) {
        setHeader(name, value.toString())
    }

    fun clearHeaders() {
        headers.clear()
    }
}