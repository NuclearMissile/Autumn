package org.example.autumn.server.classloader

import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.extension

data class Resource(val path: Path, val name: String)

class WebAppClassLoader(classPath: Path, libPath: Path?) :
    URLClassLoader("WebAppClassLoader", createUrls(classPath, libPath), ClassLoader.getSystemClassLoader()) {
    companion object {
        private fun Path.absPath(): String {
            return this.toAbsolutePath().normalize().toString().replace("\\", "/")
        }

        private fun Path.dirUrl(): URL {
            if (!Files.isDirectory(this)) throw IOException("Path '$this' is not a directory")
            val abs = this.absPath().removeSuffix("/") + "/"
            return URI.create("file://$abs").toURL()
        }

        private fun Path.jarUrl(): URL {
            if (!Files.isRegularFile(this) || this.extension != "jar")
                throw IOException("Path '$this' is not a .jar file")
            return URI.create("file://${this.absPath()}").toURL()
        }

        private fun Path.scanJars(): List<Path> {
            return Files.list(this).filter { it.extension == "jar" }.sorted().toList()
        }

        private fun createUrls(classPath: Path, libPath: Path?): Array<URL> {
            return buildList {
                add(classPath.dirUrl())
                if (libPath != null) addAll(libPath.scanJars().map { it.jarUrl() })
            }.toTypedArray()
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
    private val classPath = classPath.toAbsolutePath().normalize()
    private val libPaths = libPath?.scanJars() ?: emptyList()

    init {
        logger.info("set class path: ${this.classPath}")
        libPaths.forEach { logger.info("set jar path: $it") }
    }

    fun scanLibPaths(handler: (Resource) -> Unit) {
        fun scanJar(handler: (Resource) -> Unit, jarPath: Path) {
            JarFile(jarPath.toFile()).stream().filter { !it.isDirectory }.forEach {
                handler.invoke(Resource(jarPath, it.name))
            }
        }
        libPaths.forEach { scanJar(handler, it) }
    }

    fun scanClassPath(handler: (Resource) -> Unit, basePath: Path = classPath, path: Path = classPath) {
        Files.list(path).sorted().forEach { p ->
            if (Files.isDirectory(p)) {
                scanClassPath(handler, basePath, p)
            } else if (Files.isRegularFile(p)) {
                handler.invoke(Resource(p, basePath.relativize(p).toString().replace("\\", "/")))
            }
        }
    }
}