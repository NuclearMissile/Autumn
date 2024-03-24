package com.example.imported

import com.example.autumn.annotation.Bean
import com.example.autumn.annotation.Configuration
import java.time.ZonedDateTime

@Configuration
class ZonedDateConfiguration {
    @Bean
    fun startZonedDateTime(): ZonedDateTime {
        return ZonedDateTime.now()
    }
}
