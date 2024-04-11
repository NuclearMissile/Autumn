package org.example.scan

import org.example.autumn.annotation.Bean
import org.example.autumn.annotation.Component
import org.example.autumn.annotation.Configuration
import org.example.autumn.annotation.Primary

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


