package io.nuclearmissile.autumn.server

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import io.nuclearmissile.autumn.server.classloader.Resource
import io.nuclearmissile.autumn.server.classloader.WarClassLoader
import io.nuclearmissile.autumn.server.connector.HttpConnector
import io.nuclearmissile.autumn.utils.ClassUtils.withClassLoader
import io.nuclearmissile.autumn.utils.ConfigProperties
import io.nuclearmissile.autumn.utils.IOUtils.readInputStreamFromClassPath
import io.nuclearmissile.autumn.utils.IProperties
import io.nuclearmissile.autumn.utils.getRequired
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.annotation.WebListener
import jakarta.servlet.annotation.WebServlet
import org.apache.commons.cli.*
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.jar.JarFile
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.system.exitProcess

object AutumnServer {
    private val logger = LoggerFactory.getLogger(javaClass)

    // cli entry point
    @JvmStatic
    fun main(args: Array<String>) {
        val startTime = System.currentTimeMillis()
        val options = Options().addOption(
            Option.builder("w").longOpt("war").argName("file").hasArg()
                .desc("specify war file.").required().build()
        ).addOption(
            Option.builder("c").longOpt("config").argName("file").hasArg()
                .desc("specify external configuration file.").build()
        )

        val cmd = try {
            DefaultParser().parse(options, args)
        } catch (e: ParseException) {
            System.err.println(e.message)
            HelpFormatter().printHelp("java -jar jarPath [options]", options)
            exitProcess(1)
        }
        val war = cmd.getOptionValue("war")!!
        val customConfig = cmd.getOptionValue("config")
        startWar(war, customConfig, startTime)
    }

    private fun startWar(war: String, customConfig: String?, startTime: Long) {
        fun extractWar(warPath: Path): Triple<Path, Path, Path?> {
            if (Files.isDirectory(warPath)) {
                logger.info("war path is already a directory, no need to extract: {}", warPath)
                val classesPath = warPath.resolve("WEB-INF/classes")
                val libPath = warPath.resolve("WEB-INF/lib")
                Files.createDirectories(classesPath)
                Files.createDirectories(libPath)
                return Triple(classesPath, libPath, null)
            }

            val tmpPath = Files.createTempDirectory("Autumn_Server_")
            val warFile = JarFile(warPath.toFile())
            warFile.stream().forEach { entry ->
                if (!entry.isDirectory) {
                    val file = tmpPath.resolve(entry.name)
                    val dir = file.parent
                    if (!Files.isDirectory(dir)) {
                        Files.createDirectories(dir)
                    }
                    warFile.getInputStream(entry).use { Files.copy(it, file) }
                }
            }

            // check WEB-INF/classes and WEB-INF/lib:
            val classesPath = tmpPath.resolve("WEB-INF/classes")
            val libPath = tmpPath.resolve("WEB-INF/lib")
            Files.createDirectories(classesPath)
            Files.createDirectories(libPath)
            return Triple(classesPath, libPath, tmpPath)
        }

        val warPath = Paths.get(war).toAbsolutePath().normalize()
        require(warPath.isRegularFile() || warPath.isDirectory()) {
            "warDir must be a file or a directory"
        }
        if (customConfig != null) {
            require(customConfig.endsWith(".yml")) { "customConfigPath must be .yml file" }
        }

        val (classesPath, libPath, tmpPath) = extractWar(warPath)
        val webRoot = classesPath.parent.parent.toString()
        logger.info("set WEBROOT as {}", webRoot)

        val wcl = WarClassLoader(classesPath, libPath)
        val config = withClassLoader(wcl) {
            // load correct logger config
            try {
                readInputStreamFromClassPath("logback.xml") {
                    val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
                    loggerContext.reset()
                    val configurator = JoranConfigurator()
                    configurator.context = loggerContext
                    configurator.doConfigure(it)
                }
            } catch (_: FileNotFoundException) {
                logger.info("logback.xml not found, using default logging config")
            }
            // load config
            if (customConfig == null)
                ConfigProperties.load()
            else
                ConfigProperties.load().merge(ConfigProperties.loadYaml(customConfig, false))
        }

        // scan class:
        val classSet = mutableSetOf<Class<*>>()

        wcl.walkPaths(Consumer { r: Resource ->
            if (r.fqcn.endsWith(".class")) {
                val className = r.fqcn.removeSuffix(".class").replace('/', '.')
                if (className.endsWith("module-info") || className.endsWith("package-info")) {
                    return@Consumer
                }
                val clazz = try {
                    wcl.loadClass(className)
                } catch (e: Throwable) {
                    logger.debug(
                        "load class '{}' failed: {}: {}", className, e.javaClass.simpleName, e.message
                    )
                    return@Consumer
                }
                when {
                    clazz.isAnnotationPresent(WebServlet::class.java) -> {
                        logger.debug("Found @WebServlet: {}", clazz.name)
                        classSet.add(clazz)
                    }

                    clazz.isAnnotationPresent(WebFilter::class.java) -> {
                        logger.debug("Found @WebFilter: {}", clazz.name)
                        classSet.add(clazz)
                    }

                    clazz.isAnnotationPresent(WebListener::class.java) -> {
                        logger.debug("Found @WebListener: {}", clazz.name)
                        classSet.add(clazz)
                    }
                }
            }
        })

        start(classSet.toList(), webRoot, tmpPath, config, wcl, startTime)
    }

    // server entry point
    fun start(
        annoClasses: List<Class<*>>,
        webRoot: String = "src/main/webapp",
        tmpPath: Path? = null,
        config: IProperties = ConfigProperties.load(),
        classLoader: ClassLoader = javaClass.classLoader,
        startTime: Long = System.currentTimeMillis(),
    ) {
        // start info:
        val jvmVersion = Runtime.version().feature()
        val pid = ManagementFactory.getRuntimeMXBean().pid
        val user = System.getProperty("user.name")
        val pwd = Paths.get("").toAbsolutePath().toString()
        val enableVirtualThread = config.getRequired<Boolean>("server.enable-virtual-thread")
        logger.info(
            "starting with Java {}, PID {} (by {} in {})", jvmVersion, pid, user, pwd
        )

        // virtual thread related check
        if (enableVirtualThread && jvmVersion < 21) {
            logger.warn(
                "to enable virtual thread executor, jvm version >= 21 required, current: $jvmVersion, fallback to thread pool executor"
            )
        }
        val executor = if (enableVirtualThread && jvmVersion >= 21) {
            @Suppress("Since15")
            Executors.newVirtualThreadPerTaskExecutor()
        } else ThreadPoolExecutor(
            config.getRequired("server.core-pool-size"),
            config.getRequired("server.max-pool-size"),
            config.getRequired("server.keep-alive-time"),
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue()
        )

        val connector = HttpConnector(config, classLoader, webRoot, executor, annoClasses)
        // exit gracefully
        Runtime.getRuntime().addShutdownHook(Thread {
            connector.close()
            if (tmpPath != null) {
                logger.info("cleanup tmp path: {}", tmpPath)
                Files.walk(tmpPath).sorted(Comparator.reverseOrder()).forEach { it.toFile().delete() }
            }
        })

        try {
            connector.use {
                it.start()
                // started info:
                val endTime = System.currentTimeMillis()
                val appTime = "%.3f".format((endTime - startTime) / 1000.0)
                val jvmTime = "%.3f".format(ManagementFactory.getRuntimeMXBean().uptime / 1000.0)
                logger.info("startup in {} s (process running for {} s)", appTime, jvmTime)
                while (true) {
                    try {
                        Thread.sleep(1000)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e.message, e)
        }
    }
}