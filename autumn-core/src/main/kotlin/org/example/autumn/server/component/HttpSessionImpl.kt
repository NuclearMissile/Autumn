package org.example.autumn.server.component

import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpSession
import java.util.*

class HttpSessionImpl : HttpSession {
    override fun getCreationTime(): Long {
        TODO("Not yet implemented")
    }

    override fun getId(): String {
        TODO("Not yet implemented")
    }

    override fun getLastAccessedTime(): Long {
        TODO("Not yet implemented")
    }

    override fun getServletContext(): ServletContext {
        TODO("Not yet implemented")
    }

    override fun setMaxInactiveInterval(interval: Int) {
        TODO("Not yet implemented")
    }

    override fun getMaxInactiveInterval(): Int {
        TODO("Not yet implemented")
    }

    override fun getAttribute(name: String?): Any {
        TODO("Not yet implemented")
    }

    override fun getAttributeNames(): Enumeration<String> {
        TODO("Not yet implemented")
    }

    override fun setAttribute(name: String?, value: Any?) {
        TODO("Not yet implemented")
    }

    override fun removeAttribute(name: String?) {
        TODO("Not yet implemented")
    }

    override fun invalidate() {
        TODO("Not yet implemented")
    }

    override fun isNew(): Boolean {
        TODO("Not yet implemented")
    }
}