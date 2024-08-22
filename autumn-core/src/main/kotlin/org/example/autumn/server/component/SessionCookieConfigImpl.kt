package org.example.autumn.server.component

import jakarta.servlet.SessionCookieConfig
import org.example.autumn.utils.IProperties
import org.example.autumn.utils.getRequired

class SessionCookieConfigImpl(
    private val config: IProperties,
) : SessionCookieConfig {
    private val attributes = mutableMapOf<String, String>()
    private var maxAge = config.getRequired<Int>("server.web-app.session-timeout") * 60
    private var httpOnly = true
    private var secure = false
    private var domain: String? = null
    private var path: String? = null

    override fun setName(name: String) {
        config.set("server.web-app.session-cookie-name", name)
    }

    override fun getName(): String {
        return config.getRequiredString("server.web-app.session-cookie-name")
    }

    override fun setDomain(domain: String) {
        this.domain = domain
    }

    override fun getDomain(): String? {
        return domain
    }

    override fun setPath(path: String) {
        this.path = path
    }

    override fun getPath(): String? {
        return path
    }

    @Suppress("removal")
    override fun setComment(comment: String) {
    }

    @Suppress("removal")
    override fun getComment(): String? {
        return null
    }

    override fun setHttpOnly(httpOnly: Boolean) {
        this.httpOnly = httpOnly
    }

    override fun isHttpOnly(): Boolean {
        return httpOnly
    }

    override fun setSecure(secure: Boolean) {
        this.secure = secure
    }

    override fun isSecure(): Boolean {
        return secure
    }

    override fun setMaxAge(maxAge: Int) {
        this.maxAge = maxAge
    }

    override fun getMaxAge(): Int {
        return maxAge
    }

    override fun setAttribute(name: String, value: String) {
        attributes[name] = value
    }

    override fun getAttribute(name: String): String? {
        return attributes[name]
    }

    override fun getAttributes(): Map<String, String> {
        return attributes
    }
}