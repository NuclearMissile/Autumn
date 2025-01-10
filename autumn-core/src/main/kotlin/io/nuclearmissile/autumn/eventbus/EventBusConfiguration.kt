package io.nuclearmissile.autumn.eventbus

import io.nuclearmissile.autumn.annotation.Bean
import io.nuclearmissile.autumn.annotation.Configuration

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
