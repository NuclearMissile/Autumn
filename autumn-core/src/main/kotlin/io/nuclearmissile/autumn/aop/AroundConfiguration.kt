package io.nuclearmissile.autumn.aop

import io.nuclearmissile.autumn.annotation.Around
import io.nuclearmissile.autumn.annotation.Bean
import io.nuclearmissile.autumn.annotation.Configuration

@Configuration
class AroundConfiguration {
    @Bean
    fun aroundProxyBeanPostProcessor(): AroundProxyBeanPostProcessor {
        return AroundProxyBeanPostProcessor()
    }
}

class AroundProxyBeanPostProcessor : AnnotationProxyBeanPostProcessor<Around>()