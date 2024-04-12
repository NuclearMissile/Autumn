package org.example.autumn.app

import org.example.autumn.annotation.ComponentScan
import org.example.autumn.annotation.Configuration
import org.example.autumn.annotation.Import
import org.example.autumn.boot.AutumnApplication
import org.example.autumn.jdbc.JdbcConfiguration
import org.example.autumn.servlet.WebMvcConfiguration

@ComponentScan
@Configuration
@Import(WebMvcConfiguration::class, JdbcConfiguration::class)
class AutumnAppConfiguration

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        AutumnApplication.run(
            "src/main/webapp", "target/classes", "", AutumnAppConfiguration::class.java, *args
        )
    }
}