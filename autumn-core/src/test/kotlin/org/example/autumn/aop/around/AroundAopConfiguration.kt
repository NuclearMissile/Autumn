package org.example.autumn.aop.around

import org.example.autumn.annotation.*
import org.example.autumn.aop.AroundProxyBeanPostProcessor
import org.example.autumn.aop.Invocation
import org.example.autumn.aop.InvocationChain
import java.lang.reflect.Method

@Configuration
class AroundAopConfiguration {
    @Bean
    fun aroundProxyBeanPostProcessor(): AroundProxyBeanPostProcessor {
        return AroundProxyBeanPostProcessor()
    }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Polite

@Component
class PoliteInvocation : Invocation {
    override fun invoke(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?): Any? {
        // 拦截标记了@Polite的方法返回值:
        val ret = chain.invokeChain(caller, method, args)
        method.getAnnotation(Polite::class.java) ?: return ret

        ret as String
        return if (ret.endsWith(".")) ret.substring(0, ret.length - 1) + "!" else ret
    }
}

@Component
@Around("politeInvocation")
class OriginBean {
    @Value("\${customer.name}")
    var name: String? = null

    @Polite
    fun hello(): String {
        return "Hello, $name."
    }

    fun morning(): String {
        return "Morning, $name."
    }
}

@Component
class OtherBean @Autowired constructor(val proxied: OriginBean)

