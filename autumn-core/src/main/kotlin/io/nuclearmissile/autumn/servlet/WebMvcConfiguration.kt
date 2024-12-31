package io.nuclearmissile.autumn.servlet

import io.nuclearmissile.autumn.annotation.Autowired
import io.nuclearmissile.autumn.annotation.Bean
import io.nuclearmissile.autumn.annotation.Configuration
import io.nuclearmissile.autumn.annotation.Value
import jakarta.servlet.ServletContext

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
        @Value("autumn.web.template-encoding") templateEncoding: String,
    ): ViewResolver {
        return FreeMarkerViewResolver(servletContext, templatePath, errorTemplatePath, templateEncoding)
    }

    @Bean
    fun servletContext(): ServletContext {
        return requireNotNull(servletContext) { "ServletContext is not set." }
    }
}
