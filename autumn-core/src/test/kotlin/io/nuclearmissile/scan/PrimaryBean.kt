package io.nuclearmissile.scan

import io.nuclearmissile.autumn.annotation.Bean
import io.nuclearmissile.autumn.annotation.Component
import io.nuclearmissile.autumn.annotation.Configuration
import io.nuclearmissile.autumn.annotation.Primary

@Configuration
class PrimaryConfiguration {
    @Primary
    @Bean
    fun husky(): DogBean {
        return DogBean("Husky")
    }

    @Bean
    fun teddy(): DogBean {
        return DogBean("Teddy")
    }
}


class DogBean(val type: String)

abstract class PersonBean

@Component
class StudentBean : PersonBean()

@Primary
@Component
class TeacherBean : PersonBean()


