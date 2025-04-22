package io.nuclearmissile.autumn.server.classloader

import io.nuclearmissile.autumn.IS_WINDOWS
import io.nuclearmissile.autumn.utils.IOUtils.toUnixString
import java.io.IOException
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import java.util.jar.JarFile
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

data class Resource(val path: Path, val fqcn: String)

class WarClassLoader(classesPath: Path, libPath: Path?) :
    URLClassLoader("WarClassLoader", createUrls(classesPath, libPath), getSystemClassLoader()) {
    companion object {
        private fun Path.absPath(): String {
            return (if (IS_WINDOWS) "/" else "") + this.toAbsolutePath().normalize().toUnixString()
        }

        private fun Path.dirUrl(): URL {
            if (!Files.isDirectory(this))
                throw IOException("Path '$this' is not a directory")
            return URI.create("${if (IS_WINDOWS) "file://" else "file:"}${this.absPath()}/").toURL()
        }

        private fun Path.jarUrl(): URL {
            if (!Files.isRegularFile(this) || this.extension != "jar")
                throw IOException("Path '$this' is not a .jar file")
            return URI.create("${if (IS_WINDOWS) "file://" else "file:"}${this.absPath()}").toURL()
        }

        private fun Path.scanJars(): List<Path> {
            return Files.list(this).filter { it.extension == "jar" }.toList()
        }

        private fun createUrls(classesPath: Path, libPath: Path?): Array<URL> {
            return buildList {
                add(classesPath.dirUrl())
                if (libPath != null)
                    addAll(libPath.scanJars().map { it.jarUrl() })
            }.toTypedArray()
        }
    }

    private val classesPath = classesPath.toAbsolutePath().normalize()
    private val libPaths = libPath?.scanJars() ?: emptyList()

    fun walkPaths(visitor: Consumer<Resource>) {
        // walk class path
        Files.walk(classesPath).filter(Path::isRegularFile).forEach { path ->
            visitor.accept(Resource(path, classesPath.relativize(path).toUnixString()))
        }

        // walk lib paths
        libPaths.forEach { libPath ->
            JarFile(libPath.toFile()).stream().filter { !it.isDirectory }.forEach {
                visitor.accept(Resource(libPath, it.name))
            }
        }
    }
}