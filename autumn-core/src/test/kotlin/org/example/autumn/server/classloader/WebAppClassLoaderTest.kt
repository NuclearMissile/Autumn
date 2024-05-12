package org.example.autumn.server.classloader

import org.example.autumn.utils.ClassUtils.extractTarget
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import kotlin.test.*

class WebAppClassLoaderTest {
    private val classLoader = run {
        val classPath = Path.of("src", "test", "resources", "test-classpath", "WEB-INF", "classes")
        val libPath = Path.of("src", "test", "resources", "test-classpath", "WEB-INF", "lib")
        WebAppClassLoader(classPath, libPath)
    }

    @Test
    fun testUrls() {
        val urls = classLoader.urLs.sortedBy { it.toString() }
        assertEquals(22, urls.size)
        assertTrue(urls[0].toString().endsWith("/test-classpath/WEB-INF/classes/"))
        assertTrue(urls[1].toString().endsWith("/test-classpath/WEB-INF/lib/HikariCP-5.1.0.jar"))
        assertTrue(urls[2].toString().endsWith("/test-classpath/WEB-INF/lib/annotations-13.0.jar"))
        assertTrue(urls[3].toString().endsWith("/test-classpath/WEB-INF/lib/autumn-core-1.0.0.jar"))
    }

    @Test
    fun testLoadFromClassesPath() {
        val mvcClassName = "org.example.autumn.hello.MvcController"
        val mvcClass = classLoader.loadClass(mvcClassName)
        assertEquals(mvcClassName, mvcClass.name)
        assertSame(classLoader, mvcClass.classLoader)

        val restClassName = "org.example.autumn.hello.RestApiController"
        val restClass = classLoader.loadClass(restClassName)
        val restInstance = restClass.getConstructor().newInstance()
        val errorMethod = restClass.declaredMethods.first { it.name == "error" }
        try {
            errorMethod.invoke(restInstance)
        } catch (e: InvocationTargetException) {
            val target = e.extractTarget()
            assertIs<AssertionError>(target)
            assertEquals("test error", target.message)
        }

        val servletClass = Class.forName(
            "jakarta.servlet.http.HttpServlet", true, Thread.currentThread().contextClassLoader
        )
        assertNotSame(classLoader, servletClass.classLoader)

        val dummyClassName = "org.example.autumn.hello.Dummy"
        assertThrows<ClassNotFoundException> { classLoader.loadClass(dummyClassName) }
    }

    @Test
    fun testLoadFromLibPaths() {
        val className = "org.example.autumn.exception.AutumnException"
        val clazz = classLoader.loadClass(className)
        assertEquals(className, clazz.name)

        val dummyClassName = "org.example.autumn.exception.DummyException"
        assertThrows<ClassNotFoundException> { classLoader.loadClass(dummyClassName) }
    }

    @Test
    fun testWalkClassesPath() {
        val resources = mutableListOf<String>()
        classLoader.walkClassesPath({ resources.add(it.name) })
        assertTrue(resources.contains("application.yml"))
        assertTrue(resources.contains("org/example/autumn/hello/ApiErrorFilterReg\$ApiErrorFilter.class"))
        assertTrue(resources.contains("org/example/autumn/hello/ApiErrorFilterReg.class"))
        assertTrue(resources.contains("org/example/autumn/hello/Main.class"))
    }

    @Test
    fun testLibPath() {
        val resources = mutableListOf<String>()
        classLoader.walkLibPaths { resources.add(it.name) }
        assertTrue(resources.contains("org/intellij/lang/annotations/Flow.class"))
        assertTrue(resources.contains("org/example/autumn/jdbc/BeanRowMapper.class"))
        assertTrue(resources.contains("kotlin/collections/AbstractCollection.class"))
    }
}