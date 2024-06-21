package org.example.autumn.aop.after

import org.example.autumn.annotation.*
import org.example.autumn.aop.AroundProxyBeanPostProcessor
import org.example.autumn.aop.Invocation
import org.example.autumn.aop.InvocationChain
import java.lang.reflect.Method

@Configuration
@ComponentScan
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


