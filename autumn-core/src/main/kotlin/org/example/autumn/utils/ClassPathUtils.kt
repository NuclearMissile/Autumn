package org.example.autumn.utils

import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.charset.StandardCharsets

object ClassPathUtils {
    fun <T> readInputStream(path: String, inputStreamCallback: (stream: InputStream) -> T): T {
        val _path = path.removePrefix("/")
        contextClassLoader.getResourceAsStream(_path).use { input ->
            input ?: throw FileNotFoundException("File not found in classpath: $_path")
            return inputStreamCallback.invoke(input)
        }
    }

    fun readString(path: String): String {
        return readInputStream(path) { input -> String(input.readAllBytes(), StandardCharsets.UTF_8) }
    }

    private val contextClassLoader: ClassLoader
        get() = Thread.currentThread().contextClassLoader ?: ClassPathUtils::class.java.classLoader

}