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
        @Value("autumn.web.template-path") templatePath: String,
        @Value("autumn.web.error-template-path") errorTemplatePath: String,
        @Value("autumn.web.template-encoding") templateEncoding: String
    ): ViewResolver {
        return FreeMarkerViewResolver(servletContext, templatePath, errorTemplatePath, templateEncoding)
    }

    @Bean
    fun servletContext(): ServletContext {
        return requireNotNull(servletContext) { "ServletContext is not set." }
    }
}
