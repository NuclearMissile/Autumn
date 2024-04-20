package org.example.autumn.server.component

class SessionManager(
    private val servletContext: ServletContextImpl, sessionTimeout: Int
) : Runnable {
    override fun run() {
        TODO("Not yet implemented")
    }
}