package io.nuclearmissile.autumn.utils

import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.isRegularFile

object IOUtils {
    fun <T> readInputStreamFromClassPath(path: String, callback: (stream: InputStream) -> T): T {
        val filePath = path.removePrefix("/")
        val classLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
        classLoader.getResourceAsStream(filePath).use { input ->
            input ?: throw FileNotFoundException("File not found under classpath: $filePath")
            return callback.invoke(input)
        }
    }

    fun readStringFromClassPath(path: String): String {
        return readInputStreamFromClassPath(path) { input -> String(input.readAllBytes()) }
    }

    fun <T> readInputStream(path: Path, callback: (stream: InputStream) -> T): T {
        require(path.isRegularFile()) { "Path is not a regular file: $path" }
        return callback.invoke(path.toFile().inputStream())
    }

    fun readString(path: Path): String {
        return readInputStream(path) { input -> String(input.readAllBytes()) }
    }

    fun Path.toPortableString(): String {
        val sb = StringBuilder()
        var first = true
        val root = this.root
        if (root != null) {
            sb.append(root.toString().replace('\\', '/'))
        }
        this.forEach { elem ->
            if (first)
                first = false
            else
                sb.append('/')
            sb.append(elem.toString())
        }
        return sb.toString()
    }
}