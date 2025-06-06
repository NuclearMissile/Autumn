package io.nuclearmissile.autumn.utils

import io.nuclearmissile.autumn.utils.IOUtils.toUnixString
import io.nuclearmissile.autumn.utils.YamlUtils.loadYamlAsPlainMap
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IOUtilsTest {
    @Test
    fun toUnixString() {
        assertEquals("/a/b/c/d", Paths.get("\\a\\b\\c\\d").toUnixString())
        assertEquals("C:/a/b/c/d", Paths.get("C:\\a\\b\\c\\d").toUnixString())
        assertEquals("a/b", Paths.get("a\\b").toUnixString())
        assertEquals("a/b", Paths.get("a\\b\\").toUnixString())
        assertEquals("a", Paths.get("a").toUnixString())
    }

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