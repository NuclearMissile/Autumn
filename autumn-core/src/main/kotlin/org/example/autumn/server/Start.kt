package org.example.autumn.server

import org.example.autumn.resolver.ServerConfig
import org.example.autumn.server.component.servlet.DefaultServlet
import org.example.autumn.server.connector.HttpConnector
import org.slf4j.LoggerFactory
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object Start {
    private val logger = LoggerFactory.getLogger(javaClass)

    @JvmStatic
    fun main(args: Array<String>) {
        val config = ServerConfig.load()
        val executor = ThreadPoolExecutor(
            0,
            config.getRequiredProperty("server.thread-pool-size", Int::class.java),
            0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()
        )
        try {
            HttpConnector(
                config, Thread.currentThread().contextClassLoader, "/",
                executor, listOf(DefaultServlet::class.java)
            ).use {
                while (true) {
                    try {
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e.message, e)
        }
    }
}