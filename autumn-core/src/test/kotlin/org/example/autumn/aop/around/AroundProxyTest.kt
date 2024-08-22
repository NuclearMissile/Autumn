package org.example.autumn.aop.around

import org.example.autumn.context.AnnotationApplicationContext
import org.example.autumn.utils.ConfigProperties
import kotlin.test.*

class AroundProxyTest {
    @Test
    fun testAroundProxy() {
        AnnotationApplicationContext(
            AroundAopConfiguration::class.java,
            ConfigProperties(mapOf("customer.name" to "Bob").toProperties())
        ).use { ctx ->
            val proxy = ctx.getUniqueBean(OriginBean::class.java)
            // OriginBean$ByteBuddy$8NoD1FcQ
            println(proxy.javaClass.name)

            // proxy class, not origin class:
            assertNotSame(OriginBean::class.java, proxy.javaClass)
            // proxy.name not injected:
            val nameFiled = OriginBean::class.java.getDeclaredField("name")
            nameFiled.isAccessible = true
            assertNull(nameFiled.get(proxy))

            assertEquals("Hello, Bob!", proxy.hello())
            assertEquals("Morning, Bob.", proxy.morning())

            // test injected proxy:
            val other = ctx.getUniqueBean(OtherBean::class.java)
            assertSame(proxy, other.proxied)
            assertEquals("Hello, Bob!", other.proxied.hello())
        }
    }
}