package org.example.autumn.eventbus

import org.example.autumn.annotation.Bean
import org.example.autumn.annotation.Configuration
import org.example.autumn.context.ApplicationContextHolder
import org.example.autumn.context.BeanPostProcessor
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.Executors

@Configuration
class EventBusConfig {
    @Bean(destroyMethod = "close")
    fun eventBus(): EventBus {
        return EventBus.instance
    }

    @Bean
    fun createEventSubscribeBeanPostProcessor(): EventSubscribeBeanPostProcessor {
        return EventSubscribeBeanPostProcessor()
    }
}

class EventSubscribeBeanPostProcessor : BeanPostProcessor {
    override fun afterInitialization(bean: Any, beanName: String): Any {
        val eventBus = ApplicationContextHolder.requiredApplicationContext.getBean(EventBus::class.java)
        for (method in bean.javaClass.methods) {
            if (method.annotations.map { it.annotationClass }.contains(Subscribe::class)) {
                eventBus.register(bean)
                return bean
            }
        }
        return bean
    }
}

@Target(AnnotationTarget.FUNCTION)
annotation class Subscribe(val eventMode: EventMode = EventMode.ASYNC)

enum class EventMode { ASYNC, SYNC }

class EventBus private constructor() : AutoCloseable {
    companion object {
        val instance by lazy { EventBus() }
    }

    private val subMap = Collections.synchronizedMap(WeakHashMap<Any, ArrayList<Method>>())
    private val executor = Executors.newCachedThreadPool()

    fun isRegistered(subscriber: Any): Boolean {
        return subMap.contains(subscriber)
    }

    fun register(subscriber: Any) {
        val methods = ArrayList<Method>()
        for (method in subscriber.javaClass.methods) {
            if (method.annotations.map { it.annotationClass }.contains(Subscribe::class)) {
                methods.add(method)
            }
        }
        if (methods.isNotEmpty()) {
            subMap[subscriber] = methods
        }
    }

    fun unregister(subscriber: Any) {
        subMap.remove(subscriber)
    }

    fun post(event: Any) {
        for ((subscriber, methods) in subMap) {
            for (method in methods) {
                if (method.genericParameterTypes.singleOrNull() == event.javaClass) {
                    val anno = method.getAnnotation(Subscribe::class.java)
                    if (anno.eventMode == EventMode.ASYNC) {
                        executor.submit { method.invoke(subscriber, event) }
                    } else {
                        method.invoke(subscriber, event)
                    }
                }
            }
        }
    }

    override fun close() {
        executor.shutdown()
    }
}
