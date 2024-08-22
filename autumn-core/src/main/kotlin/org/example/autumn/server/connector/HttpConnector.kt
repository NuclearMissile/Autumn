package org.example.autumn.server.connector

import com.sun.net.httpserver.HttpServer
import jakarta.servlet.ServletContainerInitializer
import org.example.autumn.DEFAULT_ERROR_MSG
import org.example.autumn.utils.IProperties
import org.example.autumn.utils.getRequired
import org.example.autumn.server.component.HttpServletRequestImpl
import org.example.autumn.server.component.HttpServletResponseImpl
import org.example.autumn.server.component.ServletContextImpl
import org.example.autumn.utils.ClassUtils.withClassLoader
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.Executor

class HttpConnector(
    private val config: IProperties, private val classLoader: ClassLoader,
    private val webRoot: String, private val executor: Executor, private val scannedClasses: List<Class<*>>,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val initializers = mutableMapOf<ServletContainerInitializer, Set<Class<*>>>()
    private lateinit var servletContext: ServletContextImpl
    private lateinit var httpServer: HttpServer

    fun start() {
        val host = config.getRequiredString("server.host")
        val port = config.getRequired<Int>("server.port")
        val backlog = config.getRequired<Int>("server.backlog")

        // init servlet context:
        withClassLoader(classLoader) {
            servletContext = ServletContextImpl(classLoader, config, webRoot)
            servletContext.setAttribute("autumn_config", config)
            initializers.forEach { (key, value) -> key.onStartup(value, servletContext) }
            servletContext.init(scannedClasses)
        }

        // start http server
        httpServer = HttpServer.create(InetSocketAddress(host, port), backlog)
        httpServer.createContext("/") { exchange ->
            val adapter = HttpExchangeAdapter(exchange)
            val resp = HttpServletResponseImpl(config, servletContext, adapter)
            val req = HttpServletRequestImpl(config, servletContext, adapter, resp)
            withClassLoader(classLoader) {
                try {
                    servletContext.process(req, resp)
                } catch (e: Throwable) {
                    // fallback error handling
                    logger.error("unhandled exception caught:", e)
                    if (!resp.isCommitted)
                        resp.sendError(500, DEFAULT_ERROR_MSG[500]!!)
                } finally {
                    resp.cleanup()
                }
            }
        }
        httpServer.executor = executor
        httpServer.start()
        logger.info("Autumn server started at http://{}:{}", host, port)
    }

    fun addServletContainerInitializer(sci: ServletContainerInitializer, classes: Set<Class<*>>) {
        initializers[sci] = classes
    }

    override fun close() {
        logger.info("Autumn server closing...")
        httpServer.stop(config.getRequired("server.stop-delay"))
        servletContext.close()
        logger.info("Autumn server closed.")
    }
}
