package com.example.autumn.aop

import com.example.autumn.annotation.Around
import com.example.autumn.context.ApplicationContextUtils
import com.example.autumn.context.BeanPostProcessor
import com.example.autumn.context.ConfigurableApplicationContext
import com.example.autumn.exception.AopConfigException
import com.example.autumn.utils.AopProxyUtils
import java.lang.reflect.InvocationHandler
import java.lang.reflect.ParameterizedType

/**
 * Create proxy for @Around.
 */
class AroundProxyBeanPostProcessor : AnnotationProxyBeanPostProcessor<Around>()

abstract class AnnotationProxyBeanPostProcessor<A : Annotation> : BeanPostProcessor {
    private val originBeans = mutableMapOf<String, Any>()
    private val annotationClass by lazy {
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
        val ctx = ApplicationContextUtils.requiredApplicationContext as ConfigurableApplicationContext
        val info = ctx.findBeanMetaInfo(handlerName) ?: throw AopConfigException(
            "@${annotationClass.simpleName} proxy handler '$handlerName' not found."
        )
        val handlerBean = info.instance ?: ctx.createBeanAsEarlySingleton(info)
        val proxy = if (handlerBean is InvocationHandler)
            AopProxyUtils.createProxy(bean, handlerBean)
        else throw AopConfigException(
            "@${annotationClass.simpleName} proxy handler '$handlerName' is not type of ${InvocationHandler::class.java.name}.",
        )
        originBeans[beanName] = bean
        return proxy
    }

    override fun beforePropertySet(bean: Any, beanName: String): Any {
        return originBeans[beanName] ?: bean
    }
}