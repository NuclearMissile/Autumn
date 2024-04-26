package org.example.autumn.servlet

import jakarta.servlet.DispatcherType
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletContextEvent
import jakarta.servlet.ServletContextListener
import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.context.ApplicationContext
import org.example.autumn.context.ApplicationContextHolder
import org.example.autumn.exception.AutumnException
import org.example.autumn.resolver.PropertyResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.Objects.requireNonNull

open class ContextLoadListener : ServletContextListener {
    val logger: Logger = LoggerFactory.getLogger(javaClass)

    override fun contextInitialized(sce: ServletContextEvent) {
        logger.info("init {}.", javaClass.name)
        val servletContext = sce.servletContext
        WebMvcConfiguration.servletContext = servletContext

        val config = servletContext.getAttribute("config") as PropertyResolver
        val encoding = config.getRequiredProperty("\${autumn.web.character-encoding:UTF-8}")
        servletContext.requestCharacterEncoding = encoding
        servletContext.responseCharacterEncoding = encoding
        val configClassName = config.getRequiredProperty("autumn.config-class-path")
        val applicationContext = createApplicationContext(configClassName, config)
        logger.info("Application context created: {}", applicationContext)

        registerFilters(servletContext)
        registerDispatcherServlet(servletContext)
    }

    private fun registerDispatcherServlet(servletContext: ServletContext) {
        val dispatcherServlet = DispatcherServlet()
        logger.info("register servlet {} for ROOT", dispatcherServlet.javaClass.name)
        servletContext.addServlet("dispatcherServlet", dispatcherServlet)?.apply {
            addMapping("/")
            setLoadOnStartup(0)
        }
    }

    private fun registerFilters(servletContext: ServletContext) {
        val applicationContext = ApplicationContextHolder.requiredApplicationContext
        for (filterRegBean in applicationContext.getBeans(FilterRegistrationBean::class.java)) {
            val urlPatterns = filterRegBean.urlPatterns
            require(urlPatterns.isNotEmpty()) {
                "No url patterns for ${filterRegBean.javaClass.name}"
            }
            val filter = requireNonNull(filterRegBean.filter) {
                "FilterRegistrationBean.filter must not return null."
            }
            logger.info(
                "register filter '{}' {} for URLs: {}",
                filterRegBean.name, filter.javaClass.name, urlPatterns.joinToString()
            )
            servletContext.addFilter(filterRegBean.name, filter)?.addMappingForUrlPatterns(
                EnumSet.of(DispatcherType.REQUEST), true, *urlPatterns.toTypedArray()
            )
        }
    }

    private fun createApplicationContext(
        configClassName: String, config: PropertyResolver
    ): ApplicationContext {
        logger.info("init ApplicationContext by configuration: {}", configClassName)
        if (configClassName.isEmpty()) {
            throw AutumnException("Cannot init ApplicationContext for missing configClassName", null)
        }
        val configClass = try {
            Class.forName(configClassName, true, Thread.currentThread().contextClassLoader)
        } catch (e: ClassNotFoundException) {
            throw AutumnException("Could not load autumn config class: $configClassName", null)
        }
        return AnnotationConfigApplicationContext(configClass, config)
    }
}