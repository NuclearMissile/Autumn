package com.example.autumn.resolver

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class Resource(val path: String, val name: String)

class ResourceResolver(private val basePackage: String) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val contextClassLoader: ClassLoader
        get() = Thread.currentThread().contextClassLoader ?: javaClass.classLoader

    fun <R> scanResources(mapper: (Resource) -> R?): List<R> {
        val basePackagePath = basePackage.replace('.', '/')
        logger.atDebug().log("scan resources from path: $basePackagePath")
        return buildList {
            val en = contextClassLoader.getResources(basePackagePath)
            while (en.hasMoreElements()) {
                val uri = en.nextElement().toURI()
                val uriStr = uri.toString().removeSuffix("/")
                var uriBaseStr = uriStr.substring(0, uriStr.length - basePackagePath.length).removeSuffix("/")
                if (uriBaseStr.startsWith("file:")) {
                    uriBaseStr = uriBaseStr.substring(5)
                }
                if (uriStr.startsWith("jar:")) {
                    scanFile(true, uriBaseStr, jarUriToPath(basePackagePath, uri), this, mapper)
                } else {
                    scanFile(false, uriBaseStr, Paths.get(uri), this, mapper)
                }
            }
        }
    }

    private fun jarUriToPath(basePackagePath: String, jarUri: URI): Path {
        return FileSystems.newFileSystem(jarUri, mutableMapOf<String, Any?>()).use {
            it.getPath(basePackagePath)
        }
    }

    private fun <R> scanFile(
        isJar: Boolean, baseDir: String, root: Path, collector: MutableList<R>, mapper: (Resource) -> R?
    ) {
        Files.walk(root).toList().filter { path -> Files.isRegularFile(path) }.map { path ->
            val res = if (isJar) {
                Resource(baseDir, path.toString().removePrefix("/"))
            } else {
                val name = path.toString().substring(baseDir.length).removePrefix("/")
                Resource("file:$path", name)
            }
            logger.atDebug().log("found resource: {}", res)
            mapper(res)?.also { collector.add(it) }
        }
    }
}