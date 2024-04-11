package org.example.imported

import org.example.autumn.annotation.Bean
import org.example.autumn.annotation.Configuration
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
