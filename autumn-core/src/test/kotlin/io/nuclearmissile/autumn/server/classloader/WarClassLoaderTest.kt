package io.nuclearmissile.autumn.server.classloader

import io.nuclearmissile.autumn.utils.ClassUtils.extractTarget
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import kotlin.test.*

class WarClassLoaderTest {
    private val classLoader = run {
        val classPath = Path.of("src", "test", "resources", "test-classpath", "WEB-INF", "classes")
        val libPath = Path.of("src", "test", "resources", "test-classpath", "WEB-INF", "lib")
        WarClassLoader(classPath, libPath)
    }

    @Test
    fun testUrls() {
        val urls = classLoader.urLs.sortedBy { it.toString() }
        assertEquals(3, urls.size)
        assertTrue(urls[0].toString().endsWith("/test-classpath/WEB-INF/classes/"))
        assertTrue(urls[1].toString().endsWith("/test-classpath/WEB-INF/lib/autumn-core-1.1.jar"))
        assertTrue(urls[2].toString().endsWith("/test/resources/test-classpath/WEB-INF/lib/sqlite-jdbc-3.47.1.0.jar"))
    }

    @Test
    fun testLoadFromClassesPath() {
        val mvcClassName = "io.nuclearmissile.autumn.hello.HelloController"
        val mvcClass = classLoader.loadClass(mvcClassName)
        assertEquals(mvcClassName, mvcClass.name)
        assertSame(classLoader, mvcClass.classLoader)

        val restClassName = "io.nuclearmissile.autumn.hello.RestApiController"
        val restClass = classLoader.loadClass(restClassName)
        val restInstance = restClass.getConstructor().newInstance()
        val errorMethod = restClass.declaredMethods.first { it.name == "error" && it.parameterCount == 0 }
        try {
            errorMethod.invoke(restInstance)
        } catch (e: InvocationTargetException) {
            val target = e.extractTarget()
            assertIs<Exception>(target)
            assertEquals("api test error", target.message)
        }

        val servletClass = Class.forName(
            "jakarta.servlet.http.HttpServlet", true, Thread.currentThread().contextClassLoader
        )
        assertNotSame(classLoader, servletClass.classLoader)

        val dummyClassName = "io.nuclearmissile.autumn.hello.Dummy"
        assertThrows<ClassNotFoundException> { classLoader.loadClass(dummyClassName) }
    }

    @Test
    fun testLoadFromLibPaths() {
        val className = "io.nuclearmissile.autumn.exception.AutumnException"
        val clazz = classLoader.loadClass(className)
        assertEquals(className, clazz.name)

        val dummyClassName = "io.nuclearmissile.autumn.exception.DummyException"
        assertThrows<ClassNotFoundException> { classLoader.loadClass(dummyClassName) }
    }

    @Test
    fun testWalkPaths() {
        val resources = mutableListOf<String>()
        classLoader.walkPaths { resources.add(it.fqcn) }
        
        // class path
        assertTrue(resources.contains("config.yml"))
        assertTrue(resources.contains("io/nuclearmissile/autumn/hello/HelloConfig.class"))
        assertTrue(resources.contains("io/nuclearmissile/autumn/hello/Main.class"))
        assertTrue(resources.contains("io/nuclearmissile/autumn/hello/service/UserService.class"))
        
        // lib paths
        assertTrue(resources.contains("org/intellij/lang/annotations/Flow.class"))
        assertTrue(resources.contains("io/nuclearmissile/autumn/db/JdbcTemplate.class"))
        assertTrue(resources.contains("kotlin/collections/AbstractCollection.class"))
    }
}