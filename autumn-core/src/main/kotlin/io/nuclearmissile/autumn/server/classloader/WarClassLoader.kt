package io.nuclearmissile.autumn.server.classloader

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

data class Resource(val path: Path, val name: String)

class WarClassLoader(classesPath: Path, libPath: Path?) :
    URLClassLoader("WarClassLoader", createUrls(classesPath, libPath), ClassLoader.getSystemClassLoader()) {
    companion object {
        private fun Path.absPath(): String {
            return "/" + this.toAbsolutePath().normalize().toString().replace("\\", "/").removePrefix("/")
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

    fun walkLibPaths(visitor: Consumer<Resource>) {
        libPaths.forEach { libPath ->
            JarFile(libPath.toFile()).stream().filter { !it.isDirectory }.forEach {
                visitor.accept(Resource(libPath, it.name))
            }
        }
    }

    fun walkClassesPath(visitor: Consumer<Resource>, basePath: Path = classesPath) {
        Files.walk(basePath).filter(Path::isRegularFile).forEach { path ->
            visitor.accept(Resource(path, basePath.relativize(path).toString().replace("\\", "/")))
        }
    }
}