package org.example.autumn.utils

import org.example.autumn.utils.YamlUtils.loadYamlAsPlainMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IOUtilsTest {
    @Test
    fun testLoadYaml() {
        val configs = loadYamlAsPlainMap("/test.yml", true)
        configs.keys.forEach {
            println("$it: ${configs[it]} (${configs[it]!!.javaClass})")
        }

        assertEquals("Autumn Framework", configs["app.title"])
        assertEquals("1.0.0", configs["app.version"])
        assertNull(configs["dummy"])
        assertEquals("\${AUTO_COMMIT:false}", configs["autumn.datasource.auto-commit"])
        assertEquals("level-4", configs["other.deep.deep.level"])
        assertEquals("0x1a2b3c", configs["other.hex-data"])
        assertEquals("0x1a2b3c", configs["other.hex-string"])
        assertEquals(listOf("Apple", "Orange", "Pear"), configs["other.list"])
        assertEquals(emptyList<String>(), configs["other.list2"])
    }
}