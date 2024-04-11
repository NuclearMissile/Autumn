package org.example.autumn.aop.after

import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.resolver.PropertyResolver
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class AfterProxyTest {
    @Test
    fun testAfterProxy() {
        AnnotationConfigApplicationContext(AfterApplication::class.java, PropertyResolver(Properties())).use { ctx ->
            val proxy: GreetingBean = ctx.getBean(GreetingBean::class.java)
            // should change return value:
            assertEquals("Hello, Bob!", proxy.hello("Bob"))
            assertEquals("Morning, Alice!", proxy.morning("Alice"))
        }
    }
}
