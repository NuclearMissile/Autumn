package org.example.autumn.server

import org.example.autumn.server.connector.HttpConnector
import org.slf4j.LoggerFactory

object Main {
    private val logger = LoggerFactory.getLogger(javaClass)

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            HttpConnector().use {
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
        logger.info("Autumn server shutdown.")
    }
}