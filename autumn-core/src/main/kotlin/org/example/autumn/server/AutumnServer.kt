package org.example.autumn.server

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.annotation.WebListener
import jakarta.servlet.annotation.WebServlet
import org.apache.commons.cli.*
import org.example.autumn.resolver.Config
import org.example.autumn.resolver.PropertyResolver
import org.example.autumn.server.classloader.Resource
import org.example.autumn.server.classloader.WebAppClassLoader
import org.example.autumn.server.connector.HttpConnector
import org.example.autumn.utils.IOUtils.readInputStreamFromClassPath
import org.example.autumn.utils.IOUtils.readStringFromClassPath
import org.slf4j.LoggerFactory
import java.io.File
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


class AutumnServer {
    companion object {
        private val logger = LoggerFactory.getLogger(Companion::class.java)

        // cli entry point
        @JvmStatic
        fun main(args: Array<String>) {
            val options = Options().addOption(
                Option.builder("w").longOpt("war").argName("file").hasArg()
                    .desc("specify war file.").required().build()
            ).addOption(
                Option.builder("c").longOpt("config").argName("file").hasArg()
                    .desc("specify external configuration file.").build()
            )
            val war: String?
            val customConfig: String?
            try {
                val cmd = DefaultParser().parse(options, args)
                war = cmd.getOptionValue("war")!!
                customConfig = cmd.getOptionValue("config")
            } catch (e: ParseException) {
                System.err.println(e.message)
                HelpFormatter().printHelp("java -jar jarPath [options]", options)
                exitProcess(1)
            }
            startWar(war, customConfig)
        }

        private fun startWar(war: String, customConfig: String?) {
            val warPath = Path.of(war).toAbsolutePath().normalize()
            require(warPath.isRegularFile() || warPath.isDirectory()) {
                "warDir must be a file or a directory"
            }
            if (customConfig != null) {
                require(customConfig.endsWith(".yml")) { "customConfigPath must be .yml file" }
            }

            // show banner
            logger.info(readStringFromClassPath("/banner.txt"))

            val (classesPath, libPath) = extractWar(warPath)
            val webRoot = classesPath.parent.parent.toString()
            logger.info("set webRoot as {}", webRoot)

            val classLoader = WebAppClassLoader(classesPath, libPath)
            val config: PropertyResolver

            try {
                Thread.currentThread().contextClassLoader = classLoader
                try {
                    // load correct logger config
                    readInputStreamFromClassPath("logback.xml") {
                        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
                        loggerContext.reset()
                        val configurator = JoranConfigurator()
                        configurator.context = loggerContext
                        configurator.doConfigure(it)
                    }
                } catch (e: FileNotFoundException) {
                    logger.info("logback.xml not found, using default logging config")
                }
                // load config
                config = if (customConfig == null) Config.load() else
                    Config.load().merge(Config.loadYaml(Paths.get(customConfig).toString(), false))
            } finally {
                Thread.currentThread().contextClassLoader = null
            }

            // scan class:
            val classSet = mutableSetOf<Class<*>>()
            val handler = Consumer { r: Resource ->
                if (r.name.endsWith(".class")) {
                    val className = r.name.substring(0, r.name.length - 6).replace('/', '.')
                    if (className.endsWith("module-info") || className.endsWith("package-info")) {
                        return@Consumer
                    }
                    val clazz = try {
                        classLoader.loadClass(className)
                    } catch (e: Throwable) {
                        logger.debug(
                            "load class '{}' failed: {}: {}", className, e.javaClass.simpleName, e.message
                        )
                        return@Consumer
                    }
                    when {
                        clazz.isAnnotationPresent(WebServlet::class.java) -> {
                            logger.info("Found @WebServlet: {}", clazz.name)
                            classSet.add(clazz)
                        }

                        clazz.isAnnotationPresent(WebFilter::class.java) -> {
                            logger.info("Found @WebFilter: {}", clazz.name)
                            classSet.add(clazz)
                        }

                        clazz.isAnnotationPresent(WebListener::class.java) -> {
                            logger.info("Found @WebListener: {}", clazz.name)
                            classSet.add(clazz)
                        }
                    }
                }
            }
            classLoader.walkClassesPath(handler)
            classLoader.walkLibPaths(handler)

            start(webRoot, config, classLoader, classSet.toList(), false)
        }

        // server entry point
        fun start(
            webRoot: String, config: PropertyResolver, classLoader: ClassLoader,
            annoClasses: List<Class<*>>, showBanner: Boolean = true
        ) {
            // show banner in embedded mode
            if (showBanner) logger.info(readStringFromClassPath("/banner.txt"))
            // start info:
            val startTime = System.currentTimeMillis()
            val javaVersion = Runtime.version().feature()
            val pid = ManagementFactory.getRuntimeMXBean().pid
            val user = System.getProperty("user.name")
            val pwd = Paths.get("").toAbsolutePath().toString()
            logger.info(
                "Starting using Java {} with PID {} (started by {} in {})", javaVersion, pid, user, pwd
            )

            val executor = if (config.getRequiredProperty("server.enable-virtual-thread", Boolean::class.java))
                Executors.newVirtualThreadPerTaskExecutor() else ThreadPoolExecutor(
                5, config.getRequiredProperty("server.thread-pool-size", Int::class.java),
                10L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()
            )
            try {
                HttpConnector(config, classLoader, webRoot, executor, annoClasses).use {
                    it.start()
                    // started info:
                    val endTime = System.currentTimeMillis()
                    val appTime = "%.3f".format((endTime - startTime) / 1000.0)
                    val jvmTime = "%.3f".format(ManagementFactory.getRuntimeMXBean().uptime / 1000.0)
                    logger.info("Started in {} s (process running for {} s)", appTime, jvmTime)
                    while (true) {
                        try {
                            Thread.sleep(1000)
                        } catch (e: InterruptedException) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e.message, e)
            }
        }

        private fun extractWar(warPath: Path): Pair<Path, Path> {
            if (Files.isDirectory(warPath)) {
                logger.info("war is directy: {}", warPath)
                val classesPath = warPath.resolve("WEB-INF/classes")
                val libPath = warPath.resolve("WEB-INF/lib")
                Files.createDirectories(classesPath)
                Files.createDirectories(libPath)
                return Pair(classesPath, libPath)
            }

            val tmpPath = Files.createTempDirectory("Autumn_Server_")
            Runtime.getRuntime().addShutdownHook(Thread {
                Files.walk(tmpPath).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete)
            })

            logger.info("extract {} to {}", warPath, tmpPath)
            val war = JarFile(warPath.toFile())
            war.stream().forEach { entry ->
                if (!entry.isDirectory) {
                    val file = tmpPath.resolve(entry.name)
                    val dir = file.parent
                    if (!Files.isDirectory(dir)) {
                        Files.createDirectories(dir)
                    }
                    war.getInputStream(entry).use { Files.copy(it, file) }
                }
            }

            // check WEB-INF/classes and WEB-INF/lib:
            val classesPath = tmpPath.resolve("WEB-INF/classes")
            val libPath = tmpPath.resolve("WEB-INF/lib")
            Files.createDirectories(classesPath)
            Files.createDirectories(libPath)
            return Pair(classesPath, libPath)
        }
    }
}