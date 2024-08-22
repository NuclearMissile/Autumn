package org.example.autumn.aop

import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy
import net.bytebuddy.implementation.InvocationHandlerAdapter
import net.bytebuddy.implementation.attribute.MethodAttributeAppender
import net.bytebuddy.matcher.ElementMatchers
import org.example.autumn.context.ApplicationContextHolder
import org.example.autumn.context.BeanPostProcessor
import org.example.autumn.exception.AopConfigException
import org.slf4j.LoggerFactory
import java.lang.reflect.ParameterizedType

abstract class AnnotationProxyBeanPostProcessor<A : Annotation> : BeanPostProcessor {
    companion object {
        private val logger = LoggerFactory.getLogger(Companion::class.java)
        private val byteBuddy = ByteBuddy()

        fun <T> createProxy(bean: T, aopHandlers: List<Invocation>): T {
            if (aopHandlers.isEmpty()) return bean
            val targetClass = bean!!.javaClass
            logger.atDebug().log("create proxy for bean {}", targetClass.name)
            val proxyClass = byteBuddy.subclass(targetClass, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR)
                .method(ElementMatchers.isPublic())
                .intercept(InvocationHandlerAdapter.of { _, method, args ->
                    InvocationChain(aopHandlers).invokeChain(bean, method, args)
                })
                .attribute(MethodAttributeAppender.ForInstrumentedMethod.EXCLUDING_RECEIVER)
                .make().load(targetClass.classLoader).loaded
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
        @Suppress("UNCHECKED_CAST")
        ret as Class<A>
    }

    override fun beforeInitialization(bean: Any, beanName: String): Any {
        originalBeans[beanName] = bean
        val anno = bean.javaClass.getAnnotation(annotationClass) ?: return bean
        val context = ApplicationContextHolder.required
        val beanInfo = context.beanInfoMap[beanName]!!

        beanInfo.aopBeanInfos += try {
            @Suppress("UNCHECKED_CAST")
            anno.annotationClass.java.getMethod("value").invoke(anno) as Array<String>
        } catch (e: ReflectiveOperationException) {
            throw AopConfigException("@${annotationClass.simpleName} must have value().", e)
        }.map {
            context.beanInfoMap[it] ?: throw AopConfigException(
                "@${annotationClass.simpleName} proxy handler '$it' not found."
            )
        }

        return bean
    }

    override fun beforePropertySet(bean: Any, beanName: String): Any {
        return originalBeans[beanName] ?: bean
    }
}