package org.example.autumn.server.component

import jakarta.servlet.*
import jakarta.servlet.http.*
import org.example.autumn.DEFAULT_LOCALE
import org.example.autumn.resolver.PropertyResolver
import org.example.autumn.server.component.support.HttpReqParams
import org.example.autumn.server.connector.HttpExchangeRequest
import org.example.autumn.utils.DateUtils
import org.example.autumn.utils.HttpUtils
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.security.Principal
import java.time.format.DateTimeParseException
import java.util.*

class HttpServletRequestImpl(
    private val config: PropertyResolver,
    private val servletContext: ServletContextImpl,
    private val exchangeReq: HttpExchangeRequest,
    private val resp: HttpServletResponse,
) : HttpServletRequest {
    private val method = exchangeReq.getRequestMethod()
    private val headers = exchangeReq.getRequestHeaders()
    private val attributes = mutableMapOf<String, Any>()
    private val requestId = UUID.randomUUID().toString()
    private val contentLength = if (listOf("POST", "PUT", "DELETE", "PATCH").contains(method))
        getIntHeader("Content-Length") else 0

    private var inputStream: ServletInputStream? = null
    private var reader: BufferedReader? = null
    private var charset = Charset.forName(servletContext.requestCharacterEncoding)
    private val params = HttpReqParams(exchangeReq, charset)

    override fun getAttribute(name: String): Any? {
        return attributes[name]
    }

    override fun getAttributeNames(): Enumeration<String> {
        return Collections.enumeration(attributes.keys)
    }

    override fun getCharacterEncoding(): String {
        return charset.name()
    }

    override fun setCharacterEncoding(env: String) {
        charset = Charset.forName(env)
        params.setCharset(charset)
    }

    override fun getContentLength(): Int {
        return contentLength
    }

    override fun getContentLengthLong(): Long {
        return contentLength.toLong()
    }

    override fun getContentType(): String? {
        return getHeader("Content-Type")
    }

    override fun getInputStream(): ServletInputStream {
        if (reader != null) throw IllegalStateException("cannot getInputStream with reader opened.")
        if (inputStream == null) {
            inputStream = ServletInputStreamImpl(exchangeReq.getRequestBody())
        }
        return inputStream!!
    }

    override fun getParameter(name: String): String? {
        return params.getParameter(name)
    }

    override fun getParameterNames(): Enumeration<String> {
        return params.getParameterNames()
    }

    override fun getParameterValues(name: String): Array<String>? {
        return params.getParameterValues(name)
    }

    override fun getParameterMap(): Map<String, Array<String>> {
        return params.getParameterMap()
    }

    override fun getProtocol(): String {
        return "HTTP/1.1"
    }

    override fun getScheme(): String {
        var scheme = "http"
        val forwarded = config.getString("server.forwarded-headers.forwarded-proto")
        if (!forwarded.isNullOrEmpty()) {
            val forwardedHeader = getHeader(forwarded)
            if (forwardedHeader != null) {
                scheme = forwardedHeader
            }
        }
        return scheme
    }

    override fun getServerName(): String {
        var serverName = getHeader("Host")
        val forwarded = config.getString("server.forwarded-headers.forwarded-host")
        if (!forwarded.isNullOrEmpty()) {
            val forwardedHeader = getHeader(forwarded)
            if (forwardedHeader != null) {
                serverName = forwardedHeader
            }
        }
        return serverName ?: exchangeReq.getLocalAddress().hostString
    }

    override fun getServerPort(): Int {
        return exchangeReq.getLocalAddress().port
    }

    override fun getReader(): BufferedReader {
        if (inputStream != null) throw IllegalStateException("cannot getReader with input stream opened.")
        if (reader == null) {
            reader = BufferedReader(InputStreamReader(ByteArrayInputStream(exchangeReq.getRequestBody()), charset))
        }
        return reader!!
    }

    override fun getRemoteAddr(): String {
        var addr: String? = null
        val forwarded = config.getString("server.forwarded-headers.forwarded-for")
        if (!forwarded.isNullOrEmpty()) {
            val forwardedHeader = getHeader(forwarded)
            if (forwardedHeader != null) {
                val n = forwardedHeader.indexOf(',')
                addr = if (n < 0) forwardedHeader else forwardedHeader.substring(n)
            }
        }
        return addr ?: exchangeReq.getRemoteAddress().hostString
    }

    override fun getRemoteHost(): String {
        return remoteAddr
    }

    override fun setAttribute(name: String, value: Any?) {
        val oldValue = attributes[name]
        if (value == null) {
            if (oldValue != null) {
                attributes.remove(name)
                servletContext.invokeServletRequestAttributeRemoved(
                    ServletRequestAttributeEvent(servletContext, this, name, oldValue)
                )
            }
        } else {
            attributes[name] = value
            if (oldValue == null)
                servletContext.invokeServletRequestAttributeAdded(
                    ServletRequestAttributeEvent(servletContext, this, name, value)
                )
            else
                servletContext.invokeServletRequestAttributeReplaced(
                    ServletRequestAttributeEvent(servletContext, this, name, oldValue)
                )
        }
    }

    override fun removeAttribute(name: String) {
        val oldValue = attributes[name]
        if (oldValue != null) {
            attributes.remove(name)
            servletContext.invokeServletRequestAttributeRemoved(
                ServletRequestAttributeEvent(servletContext, this, name, oldValue)
            )
        }
    }

    override fun getLocale(): Locale {
        val langs = getHeader("Accept-Language") ?: return DEFAULT_LOCALE
        return HttpUtils.parseAcceptLanguages(langs).first()
    }

    override fun getLocales(): Enumeration<Locale> {
        val langs = getHeader("Accept-Language")
            ?: return Collections.enumeration(listOf(DEFAULT_LOCALE))
        return Collections.enumeration(HttpUtils.parseAcceptLanguages(langs))
    }

    override fun isSecure(): Boolean {
        return "https" == scheme.lowercase()
    }

    override fun getRequestDispatcher(path: String): RequestDispatcher? {
        // not support
        return null
    }

    override fun getRemotePort(): Int {
        return exchangeReq.getRemoteAddress().port
    }

    override fun getLocalName(): String {
        return localAddr
    }

    override fun getLocalAddr(): String {
        return exchangeReq.getLocalAddress().hostString
    }

    override fun getLocalPort(): Int {
        return exchangeReq.getLocalAddress().port
    }

    override fun getServletContext(): ServletContext {
        return servletContext
    }

    override fun startAsync(): AsyncContext {
        throw UnsupportedOperationException("startAsync")
    }

    override fun startAsync(servletRequest: ServletRequest, servletResponse: ServletResponse): AsyncContext {
        throw UnsupportedOperationException("startAsync")
    }

    override fun isAsyncStarted(): Boolean {
        return false
    }

    override fun isAsyncSupported(): Boolean {
        return false
    }

    override fun getAsyncContext(): AsyncContext {
        throw UnsupportedOperationException("getAsyncContext")
    }

    override fun getDispatcherType(): DispatcherType {
        return DispatcherType.REQUEST
    }

    override fun getRequestId(): String {
        return requestId
    }

    override fun getProtocolRequestId(): String {
        // always empty for HTTP/1.x
        return ""
    }

    override fun getServletConnection(): ServletConnection {
        throw UnsupportedOperationException("getServletConnection")
    }

    override fun getAuthType(): String? {
        // not support
        return null
    }

    override fun getCookies(): Array<Cookie>? {
        return HttpUtils.parseCookies(getHeader("Cookie"))
    }

    override fun getDateHeader(name: String): Long {
        val value = headers.getFirst(name) ?: return -1
        try {
            return DateUtils.parseDateTimeGMT(value)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Cannot parse date header: $value")
        }
    }

    override fun getHeader(name: String): String? {
        return headers.getFirst(name)
    }

    override fun getHeaders(name: String): Enumeration<String> {
        val hs = headers[name]
        return if (hs == null) Collections.emptyEnumeration() else Collections.enumeration(hs)
    }

    override fun getHeaderNames(): Enumeration<String> {
        return Collections.enumeration(headers.keys)
    }

    override fun getIntHeader(name: String): Int {
        return headers.getFirst(name)?.toInt() ?: -1
    }

    override fun getMethod(): String {
        return method
    }

    override fun getPathInfo(): String? {
        return null
    }

    override fun getPathTranslated(): String? {
        return servletContext.getRealPath(requestURI)
    }

    override fun getContextPath(): String {
        // only support root context path
        return ""
    }

    override fun getQueryString(): String? {
        return exchangeReq.getRequestURI().rawQuery
    }

    override fun getRemoteUser(): String? {
        // not support
        return null
    }

    override fun isUserInRole(role: String): Boolean {
        // not support
        return false
    }

    override fun getUserPrincipal(): Principal? {
        // not support
        return null
    }

    override fun getRequestedSessionId(): String? {
        return null
    }

    override fun getRequestURI(): String {
        return exchangeReq.getRequestURI().path
    }

    override fun getRequestURL(): StringBuffer {
        return StringBuffer(128).apply {
            append(scheme).append("://").append(serverName).append(':').append(serverPort).append(requestURI)
        }
    }

    override fun getServletPath(): String {
        return requestURI
    }

    override fun getSession(create: Boolean): HttpSession? {
        var sessionId: String? = null
        val cookies = cookies
        if (cookies != null) {
            for (cookie in cookies) {
                if (cookie.name == config.getRequiredString("server.web-app.session-cookie-name")) {
                    sessionId = cookie.value
                    break
                }
            }
        }
        if (sessionId == null) {
            if (!create) return null
            if (resp.isCommitted) {
                throw IllegalStateException("cannot create session for response committed ")
            }
            sessionId = UUID.randomUUID().toString()
            val cookieValue = config.getRequiredString("server.web-app.session-cookie-name") +
                "=$sessionId; Path=/; SameSite=Strict; HttpOnly"
            resp.addHeader("Set-Cookie", cookieValue)
        }

        return servletContext.sessionManager.getSession(sessionId)
    }

    override fun getSession(): HttpSession {
        return getSession(true)!!
    }

    override fun changeSessionId(): String {
        throw UnsupportedOperationException("changeSessionId")
    }

    override fun isRequestedSessionIdValid(): Boolean {
        return false
    }

    override fun isRequestedSessionIdFromCookie(): Boolean {
        return true
    }

    override fun isRequestedSessionIdFromURL(): Boolean {
        return false
    }

    override fun authenticate(response: HttpServletResponse): Boolean {
        // not support
        return false
    }

    override fun login(username: String, password: String) {
        // not support
    }

    override fun logout() {
        // not support
    }

    override fun getParts(): Collection<Part> {
        // not support
        return emptyList()
    }

    override fun getPart(name: String): Part? {
        // not support
        return null
    }

    override fun <T : HttpUpgradeHandler> upgrade(handlerClass: Class<T>): T? {
        // not support websocket
        return null
    }

    override fun toString(): String {
        return "HttpServletRequestImpl(method=$method, uri=$requestURI)"
    }
}