package org.example.autumn.server.connector

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpsServer
import org.example.autumn.resolver.PropertyResolver
import org.example.autumn.server.component.HttpServletRequestImpl
import org.example.autumn.server.component.HttpServletResponseImpl
import org.example.autumn.server.component.ServletContextImpl
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.Executor

class HttpConnector(
    private val config: PropertyResolver, private val classLoader: ClassLoader,
    webRoot: String, executor: Executor, scannedClasses: List<Class<*>>
) : HttpHandler, AutoCloseable {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val servletContext: ServletContextImpl
    private val httpServer: HttpsServer
    private val stopDelayInSeconds = 5

    init {
        val host = config.getRequiredProperty("sever.host")
        val port = config.getRequiredProperty("server.port", Int::class.java)
        val backlog = config.getRequiredProperty("server.backlog", Int::class.java)

        // init servlet context:
        Thread.currentThread().contextClassLoader = classLoader
        servletContext = ServletContextImpl(classLoader, config, webRoot)
        servletContext.init(scannedClasses)
        Thread.currentThread().contextClassLoader = null

        // start http server
        httpServer = HttpsServer.create(InetSocketAddress(host, port), backlog, "/", this)
        httpServer.executor = executor
        httpServer.start()
        logger.info("http server started at http://{}:{}...", host, port)
    }


    override fun handle(exchange: HttpExchange) {
        val adapter = HttpExchangeAdapter(exchange)
        val resp = HttpServletResponseImpl(config, adapter)
        val req = HttpServletRequestImpl(config, servletContext, adapter, resp)
        try {
            Thread.currentThread().contextClassLoader = classLoader
            servletContext.process(req, resp)
        } catch (e: Exception) {
            logger.error(e.message, e)
        } finally {
            Thread.currentThread().contextClassLoader = null
            resp.cleanup()
        }
    }

    override fun close() {
        servletContext.close()
        httpServer.stop(stopDelayInSeconds)
    }
}
