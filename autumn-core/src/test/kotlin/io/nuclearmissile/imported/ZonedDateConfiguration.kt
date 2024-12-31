package io.nuclearmissile.imported

import io.nuclearmissile.autumn.annotation.Bean
import io.nuclearmissile.autumn.annotation.Configuration
import java.time.ZonedDateTime

@Configuration
class ZonedDateConfiguration {
    @Bean
    fun startZonedDateTime(): ZonedDateTime {
        return ZonedDateTime.now()
    }
}
