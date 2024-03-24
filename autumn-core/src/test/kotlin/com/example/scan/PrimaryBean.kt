package com.example.scan

import com.example.autumn.annotation.Bean
import com.example.autumn.annotation.Component
import com.example.autumn.annotation.Configuration
import com.example.autumn.annotation.Primary

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


