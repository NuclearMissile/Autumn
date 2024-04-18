package org.example.autumn.boot

import jakarta.servlet.ServletContainerInitializer
import jakarta.servlet.ServletContext
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.webresources.DirResourceSet
import org.apache.catalina.webresources.StandardRoot
import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.resolver.ConfigPropertyResolver
import org.example.autumn.servlet.DispatcherServlet
import org.example.autumn.servlet.WebMvcConfiguration
import org.example.autumn.utils.ClassPathUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Paths

class AutumnApplication {
    companion object {
        fun run(webDir: String, baseDir: String, contextPath: String, configClass: Class<*>, vararg args: String) {
            AutumnApplication().start(webDir, baseDir, contextPath, configClass, *args)
        }
    }

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun start(webDir: String, baseDir: String, contextPath: String, configClass: Class<*>, vararg args: String) {
        logger.info(ClassPathUtils.readString("/banner.txt"))

        // start info:
        val startTime = System.currentTimeMillis()
        val javaVersion = Runtime.version().feature()
        val pid = ManagementFactory.getRuntimeMXBean().pid
        val user = System.getProperty("user.name")
        val pwd = Paths.get("").toAbsolutePath().toString()
        logger.info(
            "Starting {} using Java {} with PID {} (started by {} in {})",
            configClass.simpleName, javaVersion, pid, user, pwd
        )

        val configPropertyResolver = ConfigPropertyResolver.load()
        val port = configPropertyResolver.getProperty("\${server.port:8080}", Int::class.java)!!
        logger.info("starting Tomcat at port {}...", port)

        // config Tomcat
        val tomcat = Tomcat()
        tomcat.setPort(port)
        tomcat.connector.throwOnFailure = true
        val ctx = tomcat.addWebapp(contextPath, File(webDir).absolutePath)
        val resources = StandardRoot(ctx)
        resources.addPreResources(DirResourceSet(resources, "/WEB-INF/classes", File(baseDir).absolutePath, "/"))
        ctx.resources = resources
        ctx.addServletContainerInitializer(ContextLoaderInitializer(configClass, configPropertyResolver), setOf())
        tomcat.start()
        logger.info("Tomcat started at http://localhost:{}", port)

        // started info:
        val endTime = System.currentTimeMillis()
        val appTime = "%.3f".format((endTime - startTime) / 1000.0)
        val jvmTime = "%.3f".format(ManagementFactory.getRuntimeMXBean().uptime / 1000.0)
        logger.info("Started {} in {} s (process running for {} s)", configClass.simpleName, appTime, jvmTime)

        tomcat.server.await()
    }
}

class ContextLoaderInitializer(
    private val configClass: Class<*>, private val configPropertyResolver: ConfigPropertyResolver
) : ServletContainerInitializer {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    override fun onStartup(c: Set<Class<*>>, servletContext: ServletContext) {
        logger.info("Servlet container start. ServletContext = {}", servletContext)

        val encoding = configPropertyResolver.getProperty("\${autumn.web.character-encoding:UTF-8}")
        servletContext.requestCharacterEncoding = encoding
        servletContext.responseCharacterEncoding = encoding

        WebMvcConfiguration.servletContext = servletContext
        val applicationContext = AnnotationConfigApplicationContext(configClass, configPropertyResolver)
        logger.info("Application context created: {}", applicationContext)

        DispatcherServlet.registerFilters(servletContext)
        DispatcherServlet.registerDispatcherServlet(servletContext)
    }
}