package org.example.autumn.server.component

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.example.autumn.utils.IProperties
import org.example.autumn.server.connector.HttpExchangeResponse
import org.example.autumn.utils.DateUtils
import java.io.PrintWriter
import java.nio.charset.Charset
import java.util.*

class HttpServletResponseImpl(
    config: IProperties,
    servletContext: ServletContextImpl,
    private val exchangeResp: HttpExchangeResponse,
) : HttpServletResponse {
    private val headers = exchangeResp.getResponseHeaders()
    private val cookies = mutableListOf<Cookie>()

    private var status = 200
    private var bufferSize = 1024
    private var contentType = config.getString("server.mime-default")
    private var charset = Charset.forName(servletContext.responseCharacterEncoding)
    private var contentLength = 0L
    private var locale = Locale.getDefault()
    private var isCommitted = false
    private var outputStream: ServletOutputStream? = null
    private var printWriter: PrintWriter? = null

    private fun commitHeaders(length: Long) {
        exchangeResp.sendResponseHeaders(status, length)
        isCommitted = true
    }

    fun cleanup() {
        if (!isCommitted) {
            commitHeaders(-1)
        }
        outputStream?.close()
        printWriter?.close()
    }

    override fun getCharacterEncoding(): String {
        return charset.name()
    }

    override fun getContentType(): String? {
        return contentType
    }

    override fun getOutputStream(): ServletOutputStream {
        if (printWriter != null) throw IllegalStateException("cannot open output stream after writer opened")
        if (outputStream == null) {
            commitHeaders(0)
            outputStream = ServletOutputStreamImpl(exchangeResp.getResponseBody())
        }
        return outputStream!!
    }

    override fun getWriter(): PrintWriter {
        if (outputStream != null) throw IllegalStateException("cannot open writer after output stream opened")
        if (printWriter == null) {
            commitHeaders(0)
            printWriter = PrintWriter(exchangeResp.getResponseBody(), true, charset)
        }
        return printWriter!!
    }

    override fun setCharacterEncoding(charset: String) {
        this.charset = Charset.forName(charset)
    }

    override fun setContentLength(len: Int) {
        contentLength = len.toLong()
    }

    override fun setContentLengthLong(len: Long) {
        contentLength = len
    }

    override fun setContentType(type: String?) {
        contentType = type
        if (type == null) {
            headers.remove("Content-Type")
        } else {
            if (type.startsWith("text/")) {
                setHeader("Content-Type", "$type; charset=$characterEncoding")
            } else {
                setHeader("Content-Type", type)
            }
        }
    }

    override fun setBufferSize(size: Int) {
        require((outputStream ?: printWriter) != null) {
            throw IllegalStateException("output stream or print writer opened")
        }
        require(size >= 0) {
            throw IllegalArgumentException("invalid size: $size")
        }
        bufferSize = size
    }

    override fun getBufferSize(): Int {
        return bufferSize
    }

    override fun flushBuffer() {
        require((outputStream ?: printWriter) == null) {
            throw IllegalStateException("output stream or print writer not open")
        }
        outputStream?.flush()
        printWriter?.flush()
    }

    override fun resetBuffer() {
        require(!isCommitted) {
            throw IllegalStateException("cannot resetBuffer after committed")
        }
    }

    override fun isCommitted(): Boolean {
        return isCommitted
    }

    override fun reset() {
        require(!isCommitted) {
            throw IllegalStateException("cannot reset after committed")
        }
        status = 200
        outputStream = null
        printWriter = null
        headers.clear()
    }

    override fun setLocale(loc: Locale) {
        require(!isCommitted) {
            throw IllegalStateException("cannot setLocale after committed")
        }
        locale = loc
    }

    override fun getLocale(): Locale {
        return locale
    }

    override fun addCookie(cookie: Cookie) {
        require(!isCommitted) {
            throw IllegalStateException("cannot addCookie after committed")
        }
        cookies.add(cookie)
    }

    override fun containsHeader(name: String): Boolean {
        return headers.contains(name)
    }

    override fun encodeURL(url: String): String {
        return url
    }

    override fun encodeRedirectURL(url: String): String {
        return url
    }

    override fun sendError(sc: Int, msg: String) {
        require(!isCommitted) {
            throw IllegalStateException("cannot sendError after committed")
        }
        status = sc
        writer.apply { write(msg) }.flush()
    }

    override fun sendError(sc: Int) {
        require(!isCommitted) {
            throw IllegalStateException("cannot sendError after committed")
        }
        status = sc
        commitHeaders(-1)
    }

    override fun sendRedirect(location: String) {
        require(!isCommitted) {
            throw IllegalStateException("cannot sendRedirect after committed")
        }
        status = 302
        headers["Location"] = location
        commitHeaders(-1)
    }

    override fun sendRedirect(location: String, sc: Int, clearBuffer: Boolean) {
        require(!isCommitted) {
            throw IllegalStateException("cannot sendRedirect after committed")
        }
        status = sc
        headers["Location"] = location
        commitHeaders(-1)
    }

    override fun setDateHeader(name: String, date: Long) {
        require(!isCommitted) {
            throw IllegalStateException("cannot setDateHeader after committed")
        }
        headers[name] = DateUtils.formatDateTimeGMT(date)
    }

    override fun addDateHeader(name: String, date: Long) {
        require(!isCommitted) {
            throw IllegalStateException("cannot addDateHeader after committed")
        }
        headers.add(name, DateUtils.formatDateTimeGMT(date))
    }

    override fun setHeader(name: String, value: String) {
        require(!isCommitted) {
            throw IllegalStateException("cannot setHeader after committed")
        }
        headers[name] = value
    }

    override fun addHeader(name: String, value: String) {
        require(!isCommitted) {
            throw IllegalStateException("cannot addHeader after committed")
        }
        headers.add(name, value)
    }

    override fun setIntHeader(name: String, value: Int) {
        require(!isCommitted) {
            throw IllegalStateException("cannot setIntHeader after committed")
        }
        headers[name] = value.toString()
    }

    override fun addIntHeader(name: String, value: Int) {
        require(!isCommitted) {
            throw IllegalStateException("cannot addIntHeader after committed")
        }
        headers.add(name, value.toString())
    }

    override fun setStatus(sc: Int) {
        require(!isCommitted) {
            throw IllegalStateException("cannot setStatus after committed")
        }
        status = sc
    }

    override fun getStatus(): Int {
        return status
    }

    override fun getHeader(name: String): String? {
        return headers.getFirst(name)
    }

    override fun getHeaders(name: String): Collection<String> {
        return headers[name] ?: emptyList()
    }

    override fun getHeaderNames(): Collection<String> {
        return headers.keys
    }
}