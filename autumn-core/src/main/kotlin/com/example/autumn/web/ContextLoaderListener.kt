package com.example.autumn.web

import com.example.autumn.context.AnnotationConfigApplicationContext
import com.example.autumn.context.ApplicationContext
import com.example.autumn.exception.AutumnException
import com.example.autumn.resolver.PropertyResolver
import com.example.autumn.utils.WebUtils.createPropertyResolver
import com.example.autumn.utils.WebUtils.registerDispatcherServlet
import com.example.autumn.utils.WebUtils.registerFilters
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
        // register filters:
        registerFilters(servletContext)
        // register DispatcherServlet:
        registerDispatcherServlet(servletContext, propertyResolver)

        val applicationContext = createApplicationContext(
            servletContext.getInitParameter("configuration"), propertyResolver
        )
        servletContext.setAttribute("applicationContext", applicationContext)
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