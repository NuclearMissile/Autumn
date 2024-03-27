package com.example.autumn.utils

import jakarta.servlet.ServletException
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

object WebUtils {
    private val logger = LoggerFactory.getLogger(javaClass)

    const val DEFAULT_PARAM_VALUE = "__DUMMY__"
    const val CONFIG_APP_YAML: String = "/application.yml"
    const val CONFIG_APP_PROP: String = "/application.properties"
}

object PathUtils {
    fun compilePath(path: String): Pattern {
        val regPath = path.replace("\\{([a-zA-Z][a-zA-Z0-9]*)\\}".toRegex(), "(?<$1>[^/]*)")
        if (regPath.find { it == '{' || it == '}' } != null) {
            throw ServletException("Invalid path: $path")
        }
        return Pattern.compile("^$regPath$")
    }
}
