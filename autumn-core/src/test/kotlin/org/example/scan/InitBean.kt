package org.example.scan

import org.example.autumn.annotation.*


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

