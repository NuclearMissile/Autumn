package com.example.autumn.boot

import com.example.autumn.context.AnnotationConfigApplicationContext
import com.example.autumn.resolver.PropertyResolver
import com.example.autumn.servlet.WebMvcConfiguration
import com.example.autumn.utils.ClassPathUtils
import com.example.autumn.utils.ServletUtils
import jakarta.servlet.ServletContainerInitializer
import jakarta.servlet.ServletContext
import org.apache.catalina.Server
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.webresources.DirResourceSet
import org.apache.catalina.webresources.StandardRoot
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Paths

class AutumnApplication {
    companion object {
        fun run(webDir: String, baseDir: String, configClass: Class<*>, vararg args: String) {
            AutumnApplication().start(webDir, baseDir, configClass, *args)
        }
    }

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun start(webDir: String, baseDir: String, configClass: Class<*>, vararg args: String) {
        println(ClassPathUtils.readString("/banner.txt"))

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

        val propertyResolver = ServletUtils.createPropertyResolver()
        val server = startTomcat(webDir, baseDir, configClass, propertyResolver)

        // started info:
        val endTime = System.currentTimeMillis()
        val appTime = "%.3f".format((endTime - startTime) / 1000.0)
        val jvmTime = "%.3f".format(ManagementFactory.getRuntimeMXBean().uptime / 1000.0)
        logger.info("Started {} in {} s (process running for {} s)", configClass.simpleName, appTime, jvmTime)

        server.await()
    }

    private fun startTomcat(
        webDir: String, baseDir: String, configClass: Class<*>, propertyResolver: PropertyResolver
    ): Server {
        val port = propertyResolver.getProperty("\${server.port:8080}", Int::class.java)!!
        logger.info("starting Tomcat at port {}...", port)
        val tomcat = Tomcat()
        tomcat.setPort(port)
        tomcat.connector.throwOnFailure = true
        val ctx = tomcat.addWebapp("", File(webDir).absolutePath)
        val resources = StandardRoot(ctx)
        resources.addPreResources(DirResourceSet(resources, "/WEB-INF/classes", File(baseDir).absolutePath, "/"))
        ctx.resources = resources
        ctx.addServletContainerInitializer(ContextLoaderInitializer(configClass, propertyResolver), setOf<Class<*>>())
        tomcat.start()
        logger.info("Tomcat started at http://localhost:{}", port)
        return tomcat.server
    }
}

class ContextLoaderInitializer(
    private val configClass: Class<*>, private val propertyResolver: PropertyResolver
) : ServletContainerInitializer {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    override fun onStartup(c: Set<Class<*>>, ctx: ServletContext) {
        logger.info("Servlet container start. ServletContext = {}", ctx)

        val encoding = propertyResolver.getProperty("\${autumn.web.character-encoding:UTF-8}")
        ctx.requestCharacterEncoding = encoding
        ctx.responseCharacterEncoding = encoding

        WebMvcConfiguration.servletContext = ctx
        val applicationContext = AnnotationConfigApplicationContext(this.configClass, this.propertyResolver)
        logger.info("Application context created: {}", applicationContext)

        // register filters:
        ServletUtils.registerFilters(ctx)
        // register DispatcherServlet:
        ServletUtils.registerDispatcherServlet(ctx, this.propertyResolver)
    }
}