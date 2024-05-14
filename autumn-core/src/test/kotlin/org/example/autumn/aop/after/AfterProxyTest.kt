package org.example.autumn.aop.after

import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.resolver.Config
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class AfterProxyTest {
    @Test
    fun testAfterProxy() {
        AnnotationConfigApplicationContext(
            AfterAopConfiguration::class.java, Config(Properties())
        ).use { ctx ->
            val proxy: GreetingBean = ctx.getBean(GreetingBean::class.java)
            // should change return value:
            assertEquals("Hello, Bob!", proxy.hello("Bob"))
            assertEquals("Morning, Alice!", proxy.morning("Alice"))
        }
    }
}
