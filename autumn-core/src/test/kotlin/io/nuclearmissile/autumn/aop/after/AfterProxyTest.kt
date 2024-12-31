package io.nuclearmissile.autumn.aop.after

import io.nuclearmissile.autumn.context.AnnotationApplicationContext
import io.nuclearmissile.autumn.utils.ConfigProperties
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class AfterProxyTest {
    @Test
    fun testAfterProxy() {
        AnnotationApplicationContext(
            AfterAopConfiguration::class.java, ConfigProperties(Properties())
        ).use { ctx ->
            val proxy: GreetingBean = ctx.getUniqueBean(GreetingBean::class.java)
            // should change return value:
            assertEquals("Hello, Bob!", proxy.hello("Bob"))
            assertEquals("Morning, Alice!", proxy.morning("Alice"))
        }
    }
}
