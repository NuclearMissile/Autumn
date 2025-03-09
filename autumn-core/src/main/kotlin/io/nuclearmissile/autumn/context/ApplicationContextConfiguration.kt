package io.nuclearmissile.autumn.context

import io.nuclearmissile.autumn.annotation.Bean
import io.nuclearmissile.autumn.annotation.Configuration
import io.nuclearmissile.autumn.utils.IProperties

@Configuration
class ApplicationContextConfiguration {
    @Bean
    fun applicationContext(): ApplicationContext {
        return ApplicationContextHolder.required
    }

    @Bean
    fun applicationConfig(): IProperties {
        return ApplicationContextHolder.required.config
    }
}