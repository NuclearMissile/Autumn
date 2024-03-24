package com.example.autumn.utils

import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy
import net.bytebuddy.implementation.InvocationHandlerAdapter
import net.bytebuddy.matcher.ElementMatchers
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationHandler

object AopProxyUtils {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val byteBuddy = ByteBuddy()
    @OptIn(ExperimentalStdlibApi::class)
    fun <T> createProxy(bean: T, handler: InvocationHandler): T {
        val targetClass = bean!!.javaClass
        logger.atDebug().log("create proxy for bean {} @{}", targetClass.name, bean.hashCode().toHexString())
        val proxyClass = byteBuddy.subclass(targetClass, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR)
            .method(ElementMatchers.isPublic())
            .intercept(InvocationHandlerAdapter.of { _, method, args ->
                handler.invoke(bean, method, args)
            }).make().load(targetClass.classLoader).loaded
        return proxyClass.getConstructor().newInstance() as T
    }
}
