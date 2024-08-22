package org.example.autumn.aop.before

import org.example.autumn.context.AnnotationApplicationContext
import org.example.autumn.utils.ConfigProperties
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class BeforeProxyTest {
    @Test
    fun testBeforeProxy() {
        AnnotationApplicationContext(
            BeforeAopConfiguration::class.java, ConfigProperties(Properties())
        ).use { ctx ->
            val proxy: BusinessBean = ctx.getUniqueBean(BusinessBean::class.java)
            // should print log:
            assertEquals("Hello, Bob.", proxy.hello("Bob"))
            assertEquals("Morning, Alice.", proxy.morning("Alice"))
        }
    }
}