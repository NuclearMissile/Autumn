package io.nuclearmissile.autumn.servlet

import io.nuclearmissile.autumn.context.AnnotationApplicationContext
import io.nuclearmissile.autumn.context.ApplicationContextHolder
import io.nuclearmissile.autumn.exception.AutumnException
import io.nuclearmissile.autumn.utils.ClassUtils.getBeanName
import io.nuclearmissile.autumn.utils.ConfigProperties
import io.nuclearmissile.autumn.utils.IOUtils.readStringFromClassPath
import io.nuclearmissile.autumn.utils.IProperties
import jakarta.servlet.DispatcherType
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletContextEvent
import jakarta.servlet.ServletContextListener
import org.slf4j.LoggerFactory
import java.util.*
import java.util.Objects.requireNonNull

abstract class AutumnApplication : ServletContextListener {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun contextInitialized(sce: ServletContextEvent) {
        // show banner
        logger.info(readStringFromClassPath("/banner.txt"))
        val servletContext = sce.servletContext
        WebMvcConfiguration.servletContext = servletContext
        val config = servletContext.getAttribute("autumn_config") as IProperties? ?: ConfigProperties.load()
        servletContext.requestCharacterEncoding = config.getRequiredString("server.request-encoding")
        servletContext.responseCharacterEncoding = config.getRequiredString("server.response-encoding")

        val appClassName = javaClass.name
        logger.info("init ApplicationContext by application class: {}", appClassName)
        val appClass = try {
            Class.forName(appClassName, true, Thread.currentThread().contextClassLoader)
        } catch (e: Exception) {
            throw AutumnException("Could not load autumn config class: $appClassName", e)
        }
        val applicationContext = AnnotationApplicationContext(appClass, config)
        logger.info("Application context created: {}", applicationContext)

        registerFilters(servletContext)
        registerDispatcherServlet(servletContext)
    }

    override fun contextDestroyed(sce: ServletContextEvent) {
        ApplicationContextHolder.required.close()
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
            val filterBeanName = filterRegBean.javaClass.getBeanName()
            require(urlPatterns.isNotEmpty()) {
                "No url patterns for $filterBeanName"
            }
            val filter = requireNonNull(filterRegBean.filter) {
                "FilterRegistrationBean.filter must not return null."
            }
            logger.info(
                "register filter '{}': {} for url pattern: {}",
                filterBeanName, filter.javaClass.name, urlPatterns.joinToString()
            )
            servletContext.addFilter(filterBeanName, filter)?.addMappingForUrlPatterns(
                EnumSet.of(DispatcherType.REQUEST), true, *urlPatterns.toTypedArray()
            )
        }
    }
}