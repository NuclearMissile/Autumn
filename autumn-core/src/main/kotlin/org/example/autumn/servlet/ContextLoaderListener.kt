package org.example.autumn.servlet

import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.context.ApplicationContext
import org.example.autumn.exception.AutumnException
import org.example.autumn.resolver.PropertyResolver
import org.example.autumn.utils.ServletUtils.createPropertyResolver
import org.example.autumn.utils.ServletUtils.registerDispatcherServlet
import org.example.autumn.utils.ServletUtils.registerFilters
import jakarta.servlet.ServletContextEvent
import jakarta.servlet.ServletContextListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ContextLoaderListener : ServletContextListener {
    val logger: Logger = LoggerFactory.getLogger(javaClass)

    override fun contextInitialized(sce: ServletContextEvent) {
        logger.info("init {}.", javaClass.name)
        val servletContext = sce.servletContext
        WebMvcConfiguration.servletContext = servletContext

        val propertyResolver = createPropertyResolver()
        val encoding = propertyResolver.getProperty("\${autumn.web.character-encoding:UTF-8}")!!
        servletContext.requestCharacterEncoding = encoding
        servletContext.responseCharacterEncoding = encoding
        val applicationContext = createApplicationContext(
            servletContext.getInitParameter("configuration"), propertyResolver
        )
        servletContext.setAttribute("applicationContext", applicationContext)
        // register filters:
        registerFilters(servletContext)
        // register DispatcherServlet:
        registerDispatcherServlet(servletContext, propertyResolver)
    }

    private fun createApplicationContext(configClassName: String, propertyResolver: PropertyResolver): ApplicationContext {
        logger.info("init ApplicationContext by configuration: {}", configClassName)
        if (configClassName.isEmpty()) {
            throw AutumnException("Cannot init ApplicationContext for missing init param name: configuration", null)
        }
        val configClass = try {
            Class.forName(configClassName)
        } catch (e: ClassNotFoundException) {
            throw AutumnException("Could not load class from init param 'configuration': $configClassName", null)
        }
        return AnnotationConfigApplicationContext(configClass, propertyResolver)
    }
}