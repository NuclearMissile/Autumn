package com.example.autumn.utils

import com.example.autumn.utils.YamlUtils.loadYamlAsPlainMap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YamlUtilsTest {
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
    }
}