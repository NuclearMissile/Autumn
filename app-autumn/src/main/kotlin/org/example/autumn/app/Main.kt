package org.example.autumn.app

import jakarta.servlet.annotation.WebListener
import org.example.autumn.annotation.ComponentScan
import org.example.autumn.annotation.Configuration
import org.example.autumn.annotation.Import
import org.example.autumn.jdbc.JdbcConfiguration
import org.example.autumn.resolver.Config
import org.example.autumn.server.AutumnServer
import org.example.autumn.servlet.ContextLoadListener
import org.example.autumn.servlet.WebMvcConfiguration

@ComponentScan
@Configuration
@Import(WebMvcConfiguration::class, JdbcConfiguration::class)
class AutumnAppConfiguration

@WebListener
class AppContextLoadListener : ContextLoadListener()

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        AutumnServer.start(
            "src/main/webapp", Config.load(), javaClass.classLoader, listOf(AppContextLoadListener::class.java)
        )
    }
}