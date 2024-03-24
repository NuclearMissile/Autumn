package com.example.imported

import com.example.autumn.annotation.Bean
import com.example.autumn.annotation.Configuration
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
