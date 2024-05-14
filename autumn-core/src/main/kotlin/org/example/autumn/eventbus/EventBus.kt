package org.example.autumn.eventbus

import org.example.autumn.annotation.Bean
import org.example.autumn.annotation.Configuration
import org.example.autumn.annotation.Subscribe
import org.example.autumn.context.ApplicationContextHolder
import org.example.autumn.context.BeanPostProcessor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@Configuration
class EventBusConfiguration {
    @Bean(destroyMethod = "close")
    fun eventBus(): EventBus {
        return EventBus()
    }

    @Bean
    fun eventSubscribeBeanPostProcessor(): EventSubscribeBeanPostProcessor {
        return EventSubscribeBeanPostProcessor()
    }
}

class EventSubscribeBeanPostProcessor : BeanPostProcessor {
    override fun afterInitialization(bean: Any, beanName: String): Any {
        val eventBus = ApplicationContextHolder.requiredApplicationContext.getBean(EventBus::class.java)
        for (method in bean.javaClass.methods) {
            if (method.getAnnotation(Subscribe::class.java) != null) {
                eventBus.register(bean)
                return bean
            }
        }
        return bean
    }
}

enum class EventMode { ASYNC, SYNC }

interface Event

class EventBus internal constructor() : AutoCloseable {
    private val subMap = ConcurrentHashMap<Any, ArrayList<Method>>()
    private val executor = Executors.newCachedThreadPool()

    fun isRegistered(subscriber: Any): Boolean {
        return subMap.containsKey(subscriber)
    }

    fun register(subscriber: Any) {
        val methods = ArrayList<Method>()
        for (method in subscriber.javaClass.methods) {
            if (method.getAnnotation(Subscribe::class.java) != null) {
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

    fun post(event: Event) {
        for ((subscriber, methods) in subMap) {
            for (method in methods) {
                if (method.genericParameterTypes.singleOrNull() == event.javaClass) {
                    if (method.getAnnotation(Subscribe::class.java).eventMode == EventMode.ASYNC) {
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
        subMap.clear()
    }
}
