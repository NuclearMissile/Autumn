package org.example.autumn.servlet

import jakarta.servlet.ServletContextEvent
import jakarta.servlet.ServletContextListener
import jakarta.servlet.annotation.WebListener
import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.context.ApplicationContext
import org.example.autumn.exception.AutumnException
import org.example.autumn.resolver.CONFIG_APP_PROP
import org.example.autumn.resolver.CONFIG_APP_YAML
import org.example.autumn.resolver.ConfigPropertyResolver
import org.example.autumn.servlet.DispatcherServlet.Companion.registerDispatcherServlet
import org.example.autumn.servlet.DispatcherServlet.Companion.registerFilters
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@WebListener
class ContextLoaderListener : ServletContextListener {
    val logger: Logger = LoggerFactory.getLogger(javaClass)

    override fun contextInitialized(sce: ServletContextEvent) {
        logger.info("init {}.", javaClass.name)
        val servletContext = sce.servletContext
        WebMvcConfiguration.servletContext = servletContext

        val configPropertyResolver = ConfigPropertyResolver.load(CONFIG_APP_YAML, CONFIG_APP_PROP)
        val encoding = configPropertyResolver.getProperty("\${autumn.web.character-encoding:UTF-8}")!!
        servletContext.requestCharacterEncoding = encoding
        servletContext.responseCharacterEncoding = encoding
        val configClassName = servletContext.getInitParameter("configClassPath")
            ?: configPropertyResolver.getRequiredProperty("\${autumn.config-class-path}")
        val applicationContext = createApplicationContext(configClassName, configPropertyResolver)
        logger.info("Application context created: {}", applicationContext)

        registerFilters(servletContext)
        registerDispatcherServlet(servletContext)
    }

    private fun createApplicationContext(
        configClassName: String, configPropertyResolver: ConfigPropertyResolver
    ): ApplicationContext {
        logger.info("init ApplicationContext by configuration: {}", configClassName)
        if (configClassName.isEmpty()) {
            throw AutumnException("Cannot init ApplicationContext for missing init param name: configuration", null)
        }
        val configClass = try {
            Class.forName(configClassName)
        } catch (e: ClassNotFoundException) {
            throw AutumnException("Could not load class from init param 'configuration': $configClassName", null)
        }
        return AnnotationConfigApplicationContext(configClass, configPropertyResolver)
    }
}