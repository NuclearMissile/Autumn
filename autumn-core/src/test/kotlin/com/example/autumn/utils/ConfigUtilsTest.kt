package com.example.autumn.utils

import com.example.autumn.utils.ConfigUtils.loadProperties
import com.example.autumn.utils.ConfigUtils.loadYamlAsPlainMap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigUtilsTest {
    @Test
    fun testLoadYaml() {
        val configs = loadYamlAsPlainMap("/application.yml")
        configs.keys.forEach {
            println("$it: ${configs[it]} (${configs[it]!!.javaClass})")
        }

        assertEquals("Autumn Framework", configs["app.title"])
        assertEquals("1.0.0", configs["app.version"])
        assertNull(configs["app.author"])
        assertEquals("\${AUTO_COMMIT:false}", configs["autumn.datasource.auto-commit"])
        assertEquals("level-4", configs["other.deep.deep.level"])
        assertEquals("0x1a2b3c", configs["other.hex-data"])
        assertEquals("0x1a2b3c", configs["other.hex-string"])
        assertEquals(listOf("Apple", "Orange", "Pear"), configs["other.list"])
        assertEquals(emptyList<String>(), configs["other.list2"])
    }

    @Test
    fun testLoadProperties() {
        val configs = loadProperties("/application.properties")
        configs.keys.forEach {
            println("$it: ${configs[it]} (${configs[it]!!.javaClass})")
        }

        assertEquals("Autumn Framework", configs["app.title"])
        assertEquals("1.0.0", configs["app.version"])
        assertEquals("", configs["app.empty.property"])
    }
}