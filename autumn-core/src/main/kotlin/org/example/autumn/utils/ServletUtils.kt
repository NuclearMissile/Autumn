package org.example.autumn.utils

import org.example.autumn.context.ApplicationContextHolder
import org.example.autumn.resolver.PropertyResolver
import org.example.autumn.servlet.DispatcherServlet
import org.example.autumn.servlet.FilterRegistrationBean
import org.example.autumn.utils.ConfigUtils.loadYamlAsPlainMap
import jakarta.servlet.DispatcherType
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletException
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.io.UncheckedIOException
import java.util.*
import java.util.Objects.requireNonNull
import java.util.regex.Pattern

object ServletUtils {
    private val logger = LoggerFactory.getLogger(javaClass)
    private const val CONFIG_APP_YAML: String = "/application.yml"
    private const val CONFIG_APP_PROP: String = "/application.properties"

    const val DUMMY_VALUE = "\n\t\t\n\t\t\n\ue000\ue001\ue002\n\t\t\t\t\n"

    fun compilePath(path: String): Pattern {
        val regPath = path.replace("\\{([a-zA-Z][a-zA-Z0-9]*)\\}".toRegex(), "(?<$1>[^/]*)")
        if (regPath.find { it == '{' || it == '}' } != null) {
            throw ServletException("Invalid path: $path")
        }
        return Pattern.compile("^$regPath$")
    }

    fun registerDispatcherServlet(servletContext: ServletContext, propertyResolver: PropertyResolver) {
        val dispatcherServlet = DispatcherServlet(ApplicationContextHolder.requiredApplicationContext, propertyResolver)
        logger.info("register servlet {} for URL '/'", dispatcherServlet.javaClass.name)
        val dispatcherReg = servletContext.addServlet("dispatcherServlet", dispatcherServlet)
        dispatcherReg.addMapping("/")
        dispatcherReg.setLoadOnStartup(0)
    }

    fun registerFilters(servletContext: ServletContext) {
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
            val filterReg = servletContext.addFilter(filterRegBean.name, filter)
            filterReg.addMappingForUrlPatterns(
                EnumSet.of(DispatcherType.REQUEST), true, *urlPatterns.toTypedArray()
            )
        }
    }

    /**
     * Try load property resolver from /application.yml or /application.properties.
     */
    fun createPropertyResolver(): PropertyResolver {
        var props = Properties()
        // try load application.yml:
        try {
            val yamlMap = loadYamlAsPlainMap(CONFIG_APP_YAML).filter { it.value is String } as Map<String, String>
            logger.info("load config: {}", CONFIG_APP_YAML)
            props = yamlMap.toProperties()
        } catch (e: UncheckedIOException) {
            if (e.cause is FileNotFoundException) {
                // try load application.properties:
                ClassPathUtils.readInputStream(CONFIG_APP_PROP) { input ->
                    logger.info("load config: {}", CONFIG_APP_PROP)
                    props.load(input)
                }
            }
        }
        return PropertyResolver(props)
    }
}
