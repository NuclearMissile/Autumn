package org.example.autumn.resolver

import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


class PropertyResolverTest {
    @Test
    fun testConfigLoad() {
        val config = Config.loadYaml("/config.yml")
        assertEquals("Autumn Webapp", config.getRequiredString("server.web-app.name"))
        config.set("server.web-app.name", "dummy")
        assertEquals("dummy", config.getRequiredString("server.web-app.name"))

        val test = Config.loadYaml("/test.yml")
        assertEquals("Apple,Orange,Pear", test.getRequiredString("other.list"))
    }

    @Test
    fun propertyValue() {
        val cpr = Config(
            mapOf(
                "app.title" to "Autumn Framework",
                "app.version" to "v1.0",
                "jdbc.url" to "jdbc:mysql://localhost:3306/simpsons",
                "jdbc.username" to "bart",
                "jdbc.password" to "51mp50n",
                "jdbc.pool-size" to "20",
                "jdbc.auto-commit" to "true",
                "scheduler.started-at" to "2023-03-29T21:45:01",
                "scheduler.backup-at" to "03:05:10",
                "scheduler.cleanup" to "P2DT8H21M",
            ).toProperties()
        )
        assertEquals("Autumn Framework", cpr.getString("app.title"))
        assertEquals("v1.0", cpr.getString("app.version"))
        assertEquals("v1.0", cpr.getString("app.version", "unknown"))

        assertNull(cpr.getString("dummy"))
        assertEquals("test_dummy", cpr.getString("dummy", "test_dummy"))
        cpr.set("dummy", "test_dummy_2")
        assertEquals("test_dummy_2", cpr.getRequiredString("dummy"))

        assertEquals(true, cpr.get("jdbc.auto-commit"))
        assertTrue(cpr.get("jdbc.auto-commit")!!)
        assertTrue(cpr.get("jdbc.detect-leak", true))

        assertEquals(20, cpr.get("jdbc.pool-size"))
        assertEquals(20, cpr.get("jdbc.pool-size", 999))
        assertEquals(5, cpr.get("jdbc.idle", 5))

        assertEquals(
            LocalDateTime.parse("2023-03-29T21:45:01"), cpr.get("scheduler.started-at")
        )
        assertEquals(
            LocalTime.parse("03:05:10"), cpr.get("scheduler.backup-at")
        )
        assertEquals(
            LocalTime.parse("23:59:59"),
            cpr.get("scheduler.restart-at", LocalTime.parse("23:59:59"))
        )
        assertEquals(
            Duration.ofMinutes(((2 * 24 + 8) * 60 + 21).toLong()), cpr.get("scheduler.cleanup")
        )
    }

    @Test
    fun requiredProperty() {
        val props = Properties()
        props.setProperty("app.title", "Autumn Framework")
        props.setProperty("app.version", "v1.0")

        val cpr = Config(props)
        assertThrows<IllegalArgumentException> {
            cpr.getRequiredString("not.exist")
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun propertyHolder() {
        val home = System.getenv("HOME")
        println("env HOME=$home")

        val props = Properties()
        props.setProperty("app.title", "Autumn Framework")

        val cpr = Config(props)
        assertEquals("Autumn Framework", cpr.getString("\${app.title}"))
        assertThrows<IllegalArgumentException> {
            cpr.getString("\${app.version}")
        }
        assertEquals("v1.0", cpr.getString("\${app.version:v1.0}"))
        assertEquals(1, cpr.get("\${app.version:1}"))
        assertThrows<IllegalArgumentException> { cpr.get<Int>("\${app.version:x}") }

        assertEquals(home, cpr.getString("\${app.path:\${HOME}}"))
        assertEquals(home, cpr.getString("\${app.path:\${app.home:\${HOME}}}"))
        assertEquals("/not-exist", cpr.getString("\${app.path:\${app.home:\${ENV_NOT_EXIST:/not-exist}}}"))
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun propertyHolderOnWin() {
        val os = System.getenv("OS")
        println("env OS=$os")
        val cpr = Config(Properties())
        assertEquals("Windows_NT", cpr.getString("\${app.os:\${OS}}"))
    }
}
