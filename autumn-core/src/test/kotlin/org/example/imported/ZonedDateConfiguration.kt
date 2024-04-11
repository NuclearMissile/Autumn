package org.example.imported

import org.example.autumn.annotation.Bean
import org.example.autumn.annotation.Configuration
import java.time.ZonedDateTime

@Configuration
class ZonedDateConfiguration {
    @Bean
    fun startZonedDateTime(): ZonedDateTime {
        return ZonedDateTime.now()
    }
}
