package org.example.autumn.aop.before

import org.example.autumn.annotation.*
import org.example.autumn.aop.AroundProxyBeanPostProcessor
import org.example.autumn.aop.InvocationAdapter
import org.example.autumn.aop.InvocationChain
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

@Configuration
@ComponentScan
class BeforeAopConfiguration {
    @Bean
    fun aroundProxyBeanPostProcessor(): AroundProxyBeanPostProcessor {
        return AroundProxyBeanPostProcessor()
    }
}

@Component
@Around("logInvocation")
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
class LogInvocation : InvocationAdapter {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun before(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?) {
        logger.info("[Before] {}()", method.name)
    }
}
