package org.example.autumn.utils

import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.isRegularFile

object IOUtils {
    fun <T> readInputStreamFromClassPath(path: String, callback: (stream: InputStream) -> T): T {
        val _path = path.removePrefix("/")
        val classLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
        classLoader.getResourceAsStream(_path).use { input ->
            input ?: throw FileNotFoundException("File not found in classpath: $_path")
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
}