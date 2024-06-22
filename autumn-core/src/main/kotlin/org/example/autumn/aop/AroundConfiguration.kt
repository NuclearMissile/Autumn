package org.example.autumn.aop

import org.example.autumn.annotation.Around
import org.example.autumn.annotation.Bean
import org.example.autumn.annotation.Configuration

@Configuration
class AroundConfiguration {
    @Bean
    fun aroundProxyBeanPostProcessor(): AroundProxyBeanPostProcessor {
        return AroundProxyBeanPostProcessor()
    }
}

class AroundProxyBeanPostProcessor : AnnotationProxyBeanPostProcessor<Around>()