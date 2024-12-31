package io.nuclearmissile.autumn.aop.after

import io.nuclearmissile.autumn.annotation.Around
import io.nuclearmissile.autumn.annotation.Bean
import io.nuclearmissile.autumn.annotation.Component
import io.nuclearmissile.autumn.annotation.Configuration
import io.nuclearmissile.autumn.aop.AroundProxyBeanPostProcessor
import io.nuclearmissile.autumn.aop.Invocation
import io.nuclearmissile.autumn.aop.InvocationChain
import java.lang.reflect.Method

@Configuration
class AfterAopConfiguration {
    @Bean
    fun aroundProxyBeanPostProcessor(): AroundProxyBeanPostProcessor {
        return AroundProxyBeanPostProcessor()
    }
}

@Component
@Around("politeInvocation")
class GreetingBean {
    fun hello(name: String): String {
        return "Hello, $name."
    }

    fun morning(name: String): String {
        return "Morning, $name."
    }
}

@Component
class PoliteInvocation : Invocation {
    override fun after(
        caller: Any, returnValue: Any?, method: Method, chain: InvocationChain, args: Array<Any?>?,
    ): Any? {
        if (returnValue is String) {
            if (returnValue.endsWith(".")) {
                return returnValue.substring(0, returnValue.length - 1) + "!"
            }
        }
        return returnValue
    }
}


