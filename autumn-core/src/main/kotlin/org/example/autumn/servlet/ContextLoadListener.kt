package org.example.autumn.servlet

import jakarta.servlet.DispatcherType
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletContextEvent
import jakarta.servlet.ServletContextListener
import org.example.autumn.context.AnnotationApplicationContext
import org.example.autumn.context.ApplicationContextHolder
import org.example.autumn.exception.AutumnException
import org.example.autumn.utils.ConfigProperties
import org.example.autumn.utils.IProperties
import org.example.autumn.utils.IOUtils.readStringFromClassPath
import org.slf4j.LoggerFactory
import java.util.*
import java.util.Objects.requireNonNull

abstract class ContextLoadListener : ServletContextListener {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun contextInitialized(sce: ServletContextEvent) {
        // show banner
        logger.info(readStringFromClassPath("/banner.txt"))
        val servletContext = sce.servletContext
        WebMvcConfiguration.servletContext = servletContext
        val config = servletContext.getAttribute("autumn_config") as IProperties? ?: ConfigProperties.load()
        servletContext.requestCharacterEncoding = config.getRequiredString("server.request-encoding")
        servletContext.responseCharacterEncoding = config.getRequiredString("server.response-encoding")

        val configClassName = config.getRequiredString("autumn.config-class-name")
        logger.info("init ApplicationContext by configuration: {}", configClassName)
        if (configClassName.isEmpty()) {
            throw AutumnException("Cannot init ApplicationContext for missing configClassName", null)
        }
        val configClass = try {
            Class.forName(configClassName, true, Thread.currentThread().contextClassLoader)
        } catch (e: ClassNotFoundException) {
            throw AutumnException("Could not load autumn config class: $configClassName", null)
        }
        val applicationContext = AnnotationApplicationContext(configClass, config)
        logger.info("Application context created: {}", applicationContext)

        registerFilters(servletContext)
        registerDispatcherServlet(servletContext)
    }

    private fun registerDispatcherServlet(servletContext: ServletContext) {
        val dispatcherServlet = DispatcherServlet()
        logger.info("register servlet {} for ROOT", dispatcherServlet.javaClass.name)
        servletContext.addServlet("dispatcherServlet", dispatcherServlet)?.apply {
            addMapping("/*")
            setLoadOnStartup(0)
        }
    }

    private fun registerFilters(servletContext: ServletContext) {
        val context = ApplicationContextHolder.required
        for (filterRegBean in context.getBeans(FilterRegistration::class.java)) {
            val urlPatterns = filterRegBean.urlPatterns
            require(urlPatterns.isNotEmpty()) {
                "No url patterns for ${filterRegBean.javaClass.name}"
            }
            val filter = requireNonNull(filterRegBean.filter) {
                "FilterRegistrationBean.filter must not return null."
            }
            logger.info(
                "register filter '{}' {} for url pattern: {}",
                filterRegBean.name, filter.javaClass.name, urlPatterns.joinToString()
            )
            servletContext.addFilter(filterRegBean.name, filter)?.addMappingForUrlPatterns(
                EnumSet.of(DispatcherType.REQUEST), true, *urlPatterns.toTypedArray()
            )
        }
    }
}