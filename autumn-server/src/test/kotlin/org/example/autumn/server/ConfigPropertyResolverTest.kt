package org.example.autumn.server

import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals


class ConfigPropertyResolverTest {
    @Test
    fun testConfigLoad() {
        val cpr = ConfigPropertyResolver.load()
        assertEquals("Autumn Webapp", cpr.getRequiredProperty("server.web-app.name"))

        cpr.setProperty("server.web-app.name", "dummy")
        assertEquals("dummy", cpr.getRequiredProperty("server.web-app.name"))
    }

    @Test
    fun requiredProperty() {
        val props = Properties()
        props.setProperty("server.web-app.name", "Autumn Webapp")
        props.setProperty("session-cookie-name", "JSESSIONID")

        val cpr = ConfigPropertyResolver(props)
        assertThrows<IllegalArgumentException> {
            cpr.getRequiredProperty("not.exist")
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun propertyHolder() {
        val home = System.getenv("HOME")
        println("env HOME=$home")

        val props = Properties()
        props.setProperty("server.web-app.name", "Autumn Webapp")

        val cpr = ConfigPropertyResolver(props)
        assertEquals("Autumn Webapp", cpr.getProperty("\${server.web-app.name}"))
        assertThrows<IllegalArgumentException> {
            cpr.getProperty("\${app.version}")
        }
        assertEquals("v1.0", cpr.getProperty("\${app.version:v1.0}"))
        assertEquals(1, cpr.getProperty("\${app.version:1}", Int::class.java))
        assertThrows<IllegalArgumentException> {
            cpr.getProperty("\${app.version:x}", Int::class.java)
        }

        assertEquals(home, cpr.getProperty("\${app.path:\${HOME}}"))
        assertEquals(home, cpr.getProperty("\${app.path:\${app.home:\${HOME}}}"))
        assertEquals("/not-exist", cpr.getProperty("\${app.path:\${app.home:\${ENV_NOT_EXIST:/not-exist}}}"))
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun propertyHolderOnWin() {
        val os = System.getenv("OS")
        println("env OS=$os")
        val cpr = ConfigPropertyResolver(Properties())
        assertEquals("Windows_NT", cpr.getProperty("\${app.os:\${OS}}"))
    }
}