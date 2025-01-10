package io.nuclearmissile.autumn.context

import io.nuclearmissile.autumn.annotation.Bean
import io.nuclearmissile.autumn.annotation.Configuration

@Configuration
class ApplicationContextConfiguration {
    @Bean
    fun applicationContext(): ApplicationContext {
        return ApplicationContextHolder.required
    }
}