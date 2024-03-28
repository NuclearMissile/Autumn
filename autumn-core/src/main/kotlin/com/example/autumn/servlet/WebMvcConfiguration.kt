package com.example.autumn.servlet

import com.example.autumn.annotation.Autowired
import com.example.autumn.annotation.Bean
import com.example.autumn.annotation.Configuration
import com.example.autumn.annotation.Value
import jakarta.servlet.ServletContext

@Configuration
class WebMvcConfiguration {
    companion object {
        var servletContext: ServletContext? = null
    }

    @Bean(initMethod = "init")
    fun viewResolver(
        @Autowired servletContext: ServletContext,
        @Value("\${autumn.web.freemarker.template-path:/WEB-INF/templates}") templatePath: String,
        @Value("\${autumn.web.freemarker.template-encoding:UTF-8}") templateEncoding: String
    ): ViewResolver {
        return FreeMarkerViewResolver(servletContext, templatePath, templateEncoding)
    }

    @Bean
    fun servletContext(): ServletContext {
        return requireNotNull(servletContext) { "ServletContext is not set." }
    }
}
