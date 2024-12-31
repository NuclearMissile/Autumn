package io.nuclearmissile.autumn.aop.before

import io.nuclearmissile.autumn.annotation.Around
import io.nuclearmissile.autumn.annotation.Bean
import io.nuclearmissile.autumn.annotation.Component
import io.nuclearmissile.autumn.annotation.Configuration
import io.nuclearmissile.autumn.aop.AroundProxyBeanPostProcessor
import io.nuclearmissile.autumn.aop.Invocation
import io.nuclearmissile.autumn.aop.InvocationChain
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

@Configuration
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
class LogInvocation : Invocation {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun before(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?) {
        logger.info("[Before] {}()", method.name)
    }
}
