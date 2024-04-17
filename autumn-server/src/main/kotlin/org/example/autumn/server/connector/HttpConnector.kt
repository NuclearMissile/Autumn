package org.example.autumn.server.connector

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.slf4j.LoggerFactory

class HttpConnector : HttpHandler, AutoCloseable {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun handle(exchange: HttpExchange) {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
