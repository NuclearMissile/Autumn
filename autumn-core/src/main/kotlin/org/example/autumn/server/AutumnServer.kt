package org.example.autumn.server

import org.example.autumn.resolver.PropertyResolver
import org.example.autumn.resolver.ServerConfig
import org.example.autumn.server.component.servlet.DefaultServlet
import org.example.autumn.server.connector.HttpConnector
import org.example.autumn.utils.ClassPathUtils
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AutumnServer {
    companion object {
        private val logger = LoggerFactory.getLogger(Companion::class.java)

        // cli entry point
        @JvmStatic
        fun main(args: Array<String>) {
            val config = ServerConfig.load()
            val executor = ThreadPoolExecutor(
                5, config.getRequiredProperty("server.thread-pool-size", Int::class.java),
                10L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()
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

        // embedded server entry point
        fun start(config: PropertyResolver, webRoot: String, annoClasses: List<Class<*>>) {
            logger.info(ClassPathUtils.readString("/banner.txt"))

            // start info:
            val startTime = System.currentTimeMillis()
            val javaVersion = Runtime.version().feature()
            val pid = ManagementFactory.getRuntimeMXBean().pid
            val user = System.getProperty("user.name")
            val pwd = Paths.get("").toAbsolutePath().toString()
            logger.info(
                "Starting using Java {} with PID {} (started by {} in {})", javaVersion, pid, user, pwd
            )

            val executor = if (config.getRequiredProperty("server.enable-virtual-thread", Boolean::class.java))
                Executors.newVirtualThreadPerTaskExecutor() else ThreadPoolExecutor(
                5, config.getRequiredProperty("server.thread-pool-size", Int::class.java),
                10L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()
            )
            try {
                HttpConnector(config, Thread.currentThread().contextClassLoader, webRoot, executor, annoClasses).use {
                    it.start()
                    // started info:
                    val endTime = System.currentTimeMillis()
                    val appTime = "%.3f".format((endTime - startTime) / 1000.0)
                    val jvmTime = "%.3f".format(ManagementFactory.getRuntimeMXBean().uptime / 1000.0)
                    logger.info("Started in {} s (process running for {} s)", appTime, jvmTime)
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
}