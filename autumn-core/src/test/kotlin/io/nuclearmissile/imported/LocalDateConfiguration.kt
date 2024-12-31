package io.nuclearmissile.imported

import io.nuclearmissile.autumn.annotation.Bean
import io.nuclearmissile.autumn.annotation.Configuration
import java.time.LocalDate
import java.time.LocalDateTime

@Configuration
class LocalDateConfiguration {
    @Bean
    fun startLocalDate(): LocalDate {
        return LocalDate.now()
    }

    @Bean
    fun startLocalDateTime(): LocalDateTime {
        return LocalDateTime.now()
    }
}
