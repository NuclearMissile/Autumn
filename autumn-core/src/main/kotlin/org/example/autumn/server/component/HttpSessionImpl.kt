package org.example.autumn.server.component

import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpSession
import jakarta.servlet.http.HttpSessionBindingEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class HttpSessionImpl(
    private val servletContext: ServletContextImpl,
    private var sessionId: String?,
    private var sessionTimeout: Int,
) : HttpSession {
    private val creationTime = System.currentTimeMillis()
    private val attributes = ConcurrentHashMap<String, Any>()

    var lastAccessedAt = creationTime

    override fun getCreationTime(): Long {
        return creationTime
    }

    override fun getId(): String? {
        return sessionId
    }

    override fun getLastAccessedTime(): Long {
        return lastAccessedAt
    }

    override fun getServletContext(): ServletContext {
        return servletContext
    }

    override fun setMaxInactiveInterval(interval: Int) {
        sessionTimeout = interval
    }

    override fun getMaxInactiveInterval(): Int {
        return sessionTimeout
    }

    override fun getAttribute(name: String): Any? {
        require(sessionId != null) {
            throw IllegalStateException("session is already invalided")
        }
        return attributes[name]
    }

    override fun getAttributeNames(): Enumeration<String> {
        require(sessionId != null) {
            throw IllegalStateException("session is already invalided")
        }
        return Collections.enumeration(attributes.keys)
    }

    override fun setAttribute(name: String, value: Any?) {
        require(sessionId != null) {
            throw IllegalStateException("session is already invalided")
        }
        val oldValue = attributes[name]
        if (value == null) {
            if (oldValue != null) {
                attributes.remove(name)
                servletContext.invokeHttpSessionAttributeRemoved(HttpSessionBindingEvent(this, name, oldValue))
            }
        } else {
            attributes[name] = value
            if (oldValue == null)
                servletContext.invokeHttpSessionAttributeAdded(HttpSessionBindingEvent(this, name, value))
            else
                servletContext.invokeHttpSessionAttributeReplaced(HttpSessionBindingEvent(this, name, oldValue))
        }
    }

    override fun removeAttribute(name: String) {
        require(sessionId != null) {
            throw IllegalStateException("session is already invalided")
        }
        val oldValue = attributes[name]
        if (oldValue != null) {
            attributes.remove(name)
            servletContext.invokeHttpSessionAttributeRemoved(
                HttpSessionBindingEvent(this, name, oldValue)
            )
        }
    }

    override fun invalidate() {
        require(sessionId != null) {
            throw IllegalStateException("session is already invalided")
        }
        servletContext.sessionManager.removeSession(this)
        sessionId = null
    }

    override fun isNew(): Boolean {
        return creationTime == lastAccessedAt
    }

    override fun toString(): String {
        return "HttpSessionImpl(sessionId=$sessionId)"
    }
}