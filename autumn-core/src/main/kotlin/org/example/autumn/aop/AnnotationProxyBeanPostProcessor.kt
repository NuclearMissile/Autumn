package org.example.autumn.aop

import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy
import net.bytebuddy.implementation.InvocationHandlerAdapter
import net.bytebuddy.matcher.ElementMatchers
import org.example.autumn.annotation.Around
import org.example.autumn.annotation.Bean
import org.example.autumn.annotation.Configuration
import org.example.autumn.context.ApplicationContextHolder
import org.example.autumn.context.BeanPostProcessor
import org.example.autumn.exception.AopConfigException
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.ParameterizedType

@Configuration
class AroundAopConfiguration {
    @Bean
    fun createAroundProxyBeanPostProcessor(): AroundProxyBeanPostProcessor {
        return AroundProxyBeanPostProcessor()
    }
}

class AroundProxyBeanPostProcessor : AnnotationProxyBeanPostProcessor<Around>()

abstract class AnnotationProxyBeanPostProcessor<A : Annotation> : BeanPostProcessor {
    companion object {
        private val logger = LoggerFactory.getLogger(Companion::class.java)
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

    private val originalBeans = mutableMapOf<String, Any>()
    private val annotationClass = run {
        val type = javaClass.genericSuperclass
        require(type is ParameterizedType) { "Class ${javaClass.name} does not have parameterized type." }
        val types = type.actualTypeArguments
        require(types.size == 1) { "Class ${javaClass.name} has more than 1 parameterized types." }
        val ret = types.single()
        require(ret is Class<*>) { "Class ${javaClass.name} does not have parameterized type of class." }
        ret as Class<A>
    }

    override fun beforeInitialization(bean: Any, beanName: String): Any {
        val anno = bean.javaClass.getAnnotation(annotationClass) ?: return bean

        val handlerName = try {
            anno.annotationClass.java.getMethod("value").invoke(anno)
        } catch (e: ReflectiveOperationException) {
            throw AopConfigException("@${annotationClass.simpleName} must have value().", e)
        } as String
        val ctx = ApplicationContextHolder.requiredApplicationContext
        val handlerInfo = ctx.findBeanMetaInfo(handlerName) ?: throw AopConfigException(
            "@${annotationClass.simpleName} proxy handler '$handlerName' not found."
        )
        val handler = handlerInfo.instance ?: ctx.createEarlySingleton(handlerInfo)
        val proxy = if (handler is InvocationHandler)
            createProxy(bean, handler)
        else throw AopConfigException(
            "@${annotationClass.simpleName} proxy handler '$handlerName' is not type of ${InvocationHandler::class.java.name}.",
        )
        originalBeans[beanName] = bean
        return proxy
    }

    override fun beforePropertySet(bean: Any, beanName: String): Any {
        return originalBeans[beanName] ?: bean
    }
}