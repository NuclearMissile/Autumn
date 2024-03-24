package com.example.scan

import com.example.autumn.annotation.Bean
import com.example.autumn.annotation.Component
import com.example.autumn.annotation.Configuration
import com.example.autumn.annotation.Value
import jakarta.annotation.PostConstruct


@Component
class AnnotationInitBean {
    @Value("\${app.title}")
    var appTitle: String? = null

    @Value("\${app.version}")
    var appVersion: String? = null

    var appName: String? = null

    @PostConstruct
    fun init() {
        this.appName = this.appTitle + " / " + this.appVersion
    }
}

class SpecifyInitBean internal constructor(var appTitle: String, var appVersion: String) {
    var appName: String? = null

    fun init() {
        this.appName = this.appTitle + " / " + this.appVersion
    }
}

@Configuration
class SpecifyInitConfiguration {
    @Bean(initMethod = "init")
    fun createSpecifyInitBean(
        @Value("\${app.title}") appTitle: String?,
        @Value("\${app.version}") appVersion: String?
    ): SpecifyInitBean {
        return SpecifyInitBean(appTitle!!, appVersion!!)
    }
}

