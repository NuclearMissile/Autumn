package com.example.scan

import com.example.autumn.annotation.Autowired
import com.example.autumn.annotation.Component
import com.example.autumn.annotation.Order
import com.example.autumn.annotation.Value
import com.example.autumn.context.BeanPostProcessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
class OriginBean {
    @Value("\${app.title}")
    var name: String? = null

    @set:Value("\${app.version}")
    var version: String? = null
}

@Component
class InjectProxyOnConstructorBean(@Autowired val injected: OriginBean)

@Component
class InjectProxyOnPropertyBean {
    @Autowired
    var injected: OriginBean? = null
}

class FirstProxyBean(val target: OriginBean) : OriginBean() {
    override var name: String?
        get() = target.name
        set(value) {
            super.name = value
        }

    override var version: String?
        get() = target.version
        set(value) {
            target.version = value
        }
}


@Order(100)
@Component
class FirstProxyBeanPostProcessor : BeanPostProcessor {
    val logger: Logger = LoggerFactory.getLogger(javaClass)

    var originBeans: MutableMap<String, Any> = HashMap()

    override fun beforeInitialization(bean: Any, beanName: String): Any {
        if (OriginBean::class.java.isAssignableFrom(bean.javaClass)) {
            logger.debug("create first proxy for bean '{}': {}", beanName, bean)
            val proxy = FirstProxyBean(bean as OriginBean)
            originBeans[beanName] = bean
            return proxy
        }
        return bean
    }

    override fun beforePropertySet(bean: Any, beanName: String): Any {
        val origin = originBeans[beanName]
        if (origin != null) {
            logger.debug("auto set property for {} from first proxy {} to origin bean: {}", beanName, bean, origin)
            return origin
        }
        return bean
    }
}

class SecondProxyBean(val target: OriginBean) : OriginBean() {
    override var name: String?
        get() = target.name
        set(value) {
            target.name = value
        }

    override var version: String?
        get() = target.version
        set(value) {
            target.version = value
        }
}


@Order(200)
@Component
class SecondProxyBeanPostProcessor : BeanPostProcessor {
    val logger: Logger = LoggerFactory.getLogger(javaClass)
    var originBeans: MutableMap<String, Any> = HashMap()

    override fun beforeInitialization(bean: Any, beanName: String): Any {
        if (OriginBean::class.java.isAssignableFrom(bean.javaClass)) {
            logger.debug("create second proxy for bean '{}': {}", beanName, bean)
            val proxy = SecondProxyBean(bean as OriginBean)
            originBeans[beanName] = bean
            return proxy
        }
        return bean
    }

    override fun beforePropertySet(bean: Any, beanName: String): Any {
        val origin = originBeans[beanName]
        if (origin != null) {
            logger.debug("auto set property for {} from second proxy {} to origin bean: {}", beanName, bean, origin)
            return origin
        }
        return bean
    }
}