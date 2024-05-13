package org.example.autumn.eventbus

import com.google.common.eventbus.EventBus
import com.google.common.eventbus.Subscribe
import org.example.autumn.annotation.Bean
import org.example.autumn.annotation.Configuration
import org.example.autumn.context.ApplicationContextHolder
import org.example.autumn.context.BeanPostProcessor

@Configuration
class EventBusConfig {
    @Bean
    fun eventBus(): EventBus {
        return EventBus()
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