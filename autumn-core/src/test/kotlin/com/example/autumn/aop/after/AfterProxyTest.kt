package com.example.autumn.aop.after

import com.example.autumn.context.AnnotationConfigApplicationContext
import com.example.autumn.io.PropertyResolver
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
