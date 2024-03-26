package com.example.autumn.aop.around;

import com.example.autumn.annotation.*
import com.example.autumn.aop.AroundProxyBeanPostProcessor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

@Configuration
@ComponentScan
class AroundApplication {
    @Bean
    fun createAroundProxyBeanPostProcessor(): AroundProxyBeanPostProcessor {
        return AroundProxyBeanPostProcessor()
    }
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Polite

@Component
class PoliteInvocationHandler : InvocationHandler {
    override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any? {
        // 拦截标记了@Polite的方法返回值:
        val ret = method.invoke(proxy, *(args ?: emptyArray()))
        method.getAnnotation(Polite::class.java) ?: return ret

        ret as String
        return if (ret.endsWith(".")) ret.substring(0, ret.length - 1) + "!" else ret
    }
}

@Component
@Around("politeInvocationHandler")
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

@Order(0)
@Component
class OtherBean @Autowired constructor(val origin: OriginBean)

