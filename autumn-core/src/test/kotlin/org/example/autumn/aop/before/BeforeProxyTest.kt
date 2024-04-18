package org.example.autumn.aop.before

import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.resolver.ConfigPropertyResolver
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class BeforeProxyTest {
    @Test
    fun testBeforeProxy() {
        AnnotationConfigApplicationContext(
            BeforeApplication::class.java, ConfigPropertyResolver(Properties())
        ).use { ctx ->
            val proxy: BusinessBean = ctx.getBean(BusinessBean::class.java)
            // should print log:
            assertEquals("Hello, Bob.", proxy.hello("Bob"))
            assertEquals("Morning, Alice.", proxy.morning("Alice"))
        }
    }
}