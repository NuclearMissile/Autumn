package org.example.autumn.aop.around

import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.resolver.Config
import kotlin.test.*

class AroundProxyTest {
    @Test
    fun testAroundProxy() {
        AnnotationConfigApplicationContext(
            AroundAopConfiguration::class.java,
            Config(mapOf("customer.name" to "Bob").toProperties())
        ).use { ctx ->
            val proxy = ctx.getBean(OriginBean::class.java)
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
            val other = ctx.getBean(OtherBean::class.java)
            assertSame(proxy, other.proxied)
            assertEquals("Hello, Bob!", other.proxied.hello())
        }
    }
}