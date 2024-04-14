package org.example.autumn.servlet

import jakarta.servlet.ServletContext
import org.example.autumn.annotation.Autowired
import org.example.autumn.annotation.Bean
import org.example.autumn.annotation.Configuration
import org.example.autumn.annotation.Value

@Configuration
class WebMvcConfiguration {
    companion object {
        var servletContext: ServletContext? = null
    }

    @Bean(initMethod = "init")
    fun viewResolver(
        @Autowired servletContext: ServletContext,
        @Value("\${autumn.web.freemarker.template-path:/WEB-INF/templates}") templatePath: String,
        @Value("\${autumn.web.freemarker.error-path:/WEB-INF/errors}") errorTemplatePath: String,
        @Value("\${autumn.web.freemarker.template-encoding:UTF-8}") templateEncoding: String
    ): ViewResolver {
        return FreeMarkerViewResolver(servletContext, templatePath, errorTemplatePath, templateEncoding)
    }

    @Bean
    fun servletContext(): ServletContext {
        return requireNotNull(servletContext) { "ServletContext is not set." }
    }
}
