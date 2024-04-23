package org.example.autumn.server.component

import jakarta.servlet.http.HttpSession
import jakarta.servlet.http.HttpSessionEvent
import org.example.autumn.utils.DateUtils
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class SessionManager(
    private val servletContext: ServletContextImpl, private val sessionTimeout: Int
) : Runnable {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<String, HttpSessionImpl>()

    init {
        Thread(this, "session-cleanup").apply { setDaemon(true); start() }
    }

    fun getSession(sessionId: String): HttpSession {
        var session = sessions[sessionId]
        if (session == null) {
            session = HttpSessionImpl(servletContext, sessionId, sessionTimeout)
            sessions[sessionId] = session
            servletContext.invokeHttpSessionCreated(HttpSessionEvent(session))
        } else {
            session.lastAccessedAt = System.currentTimeMillis()
        }
        return session
    }

    fun removeSession(session: HttpSession) {
        sessions.remove(session.id!!)
        servletContext.invokeHttpSessionDestroyed(HttpSessionEvent(session))
    }

    override fun run() {
        // cleanup timeout session
        while (true) {
            try {
                Thread.sleep(60000L)
            } catch (e: InterruptedException) {
                break
            }
            val now = System.currentTimeMillis()
            sessions.forEach { (sessionId, session) ->
                if (session.lastAccessedAt + session.maxInactiveInterval * 1000L < now) {
                    logger.atDebug().log(
                        "remove expired session: {}, last access time: {}", sessionId,
                        DateUtils.formatDateTimeGMT(session.lastAccessedAt)
                    )
                    session.invalidate()
                }
            }
        }
    }
}