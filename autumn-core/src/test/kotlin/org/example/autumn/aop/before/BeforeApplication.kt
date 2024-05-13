package org.example.autumn.aop.before

import org.example.autumn.annotation.*
import org.example.autumn.aop.AroundProxyBeanPostProcessor
import org.example.autumn.aop.BeforeInvocationHandlerAdapter
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

@Configuration
@ComponentScan
class BeforeApplication {
    @Bean
    fun createAroundProxyBeanPostProcessor(): AroundProxyBeanPostProcessor {
        return AroundProxyBeanPostProcessor()
    }
}

@Component
@Around("logInvocationHandler")
class BusinessBean {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun hello(name: String): String {
        logger.info("Hello, {}.", name)
        return "Hello, $name."
    }

    fun morning(name: String): String {
        logger.info("Morning, {}.", name)
        return "Morning, $name."
    }
}

@Component
class LogInvocationHandler : BeforeInvocationHandlerAdapter() {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun before(proxy: Any, method: Method, args: Array<Any?>?) {
        logger.info("[Before] {}()", method.name)
    }
}
