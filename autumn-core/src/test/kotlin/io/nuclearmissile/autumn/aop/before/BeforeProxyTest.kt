package io.nuclearmissile.autumn.aop.before

import io.nuclearmissile.autumn.context.AnnotationApplicationContext
import io.nuclearmissile.autumn.utils.ConfigProperties
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class BeforeProxyTest {
    @Test
    fun testBeforeProxy() {
        AnnotationApplicationContext(
            BeforeAopConfiguration::class.java, ConfigProperties(emptyMap())
        ).use { ctx ->
            val proxy: BusinessBean = ctx.getUniqueBean(BusinessBean::class.java)
            // should print log:
            assertEquals("Hello, Bob.", proxy.hello("Bob"))
            assertEquals("Morning, Alice.", proxy.morning("Alice"))
        }
    }
}