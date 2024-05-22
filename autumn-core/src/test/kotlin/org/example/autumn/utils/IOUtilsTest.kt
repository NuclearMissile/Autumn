package org.example.autumn.utils

import org.example.autumn.utils.YamlUtils.loadYamlAsPlainMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IOUtilsTest {
    @Test
    fun testLoadYaml() {
        val c0 = loadYamlAsPlainMap("/test.yml", true)
        assertEquals("Autumn Framework", c0["app.title"])
        assertEquals("1.0.0", c0["app.version"])
        assertNull(c0["dummy"])
        assertEquals("\${AUTO_COMMIT:false}", c0["autumn.datasource.auto-commit"])
        assertEquals("level-4", c0["other.deep.deep.level"])
        assertEquals("0x1a2b3c", c0["other.hex-data"])
        assertEquals("0x1a2b3c", c0["other.hex-string"])
        assertEquals("Apple,Orange,Pear", c0["other.list"])
        assertEquals("", c0["other.list2"])

        val c1 = loadYamlAsPlainMap("test.yml", false)
        assertEquals("Autumn Framework", c1["app.title"])
        assertEquals("1.0.0", c1["app.version"])
        assertNull(c1["dummy"])
        assertEquals("\${AUTO_COMMIT:false}", c1["autumn.datasource.auto-commit"])
        assertEquals("level-4", c1["other.deep.deep.level"])
        assertEquals("0x1a2b3c", c1["other.hex-data"])
        assertEquals("0x1a2b3c", c1["other.hex-string"])
        assertEquals("Apple,Orange,Pear", c1["other.list"])
        assertEquals("", c1["other.list2"])
    }
}