package org.example.autumn.aop.before

import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.resolver.Config
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class BeforeProxyTest {
    @Test
    fun testBeforeProxy() {
        AnnotationConfigApplicationContext(
            BeforeAopConfiguration::class.java, Config(Properties())
        ).use { ctx ->
            val proxy: BusinessBean = ctx.getBean(BusinessBean::class.java)
            // should print log:
            assertEquals("Hello, Bob.", proxy.hello("Bob"))
            assertEquals("Morning, Alice.", proxy.morning("Alice"))
        }
    }
}