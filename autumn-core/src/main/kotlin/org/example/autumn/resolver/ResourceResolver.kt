package org.example.autumn.resolver

import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class Resource(val path: String, val name: String)

class ResourceResolver(private val basePackage: String) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val classLoader: ClassLoader
        get() = Thread.currentThread().contextClassLoader ?: javaClass.classLoader

    fun <T> scanResources(mapper: (Resource) -> T?): List<T> {
        val basePackagePath = basePackage.replace('.', '/')
        logger.atDebug().log("scan resources from path: $basePackagePath")
        return buildList {
            val en = classLoader.getResources(basePackagePath)
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
        return FileSystems.newFileSystem(jarUri, emptyMap<String, Any>()).getPath(basePackagePath)
    }

    private fun <T> scanFile(
        isJar: Boolean, baseDir: String, root: Path, collector: MutableList<T>, mapper: (Resource) -> T?
    ) {
        Files.walk(root).filter { path -> Files.isRegularFile(path) }.forEach { path ->
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