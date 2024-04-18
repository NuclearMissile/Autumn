package org.example.autumn.resolver

import org.example.autumn.utils.ClassPathUtils
import org.example.autumn.utils.ConfigUtils.loadYamlAsPlainMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.io.UncheckedIOException
import java.time.*
import java.util.*

data class PropertyExpr(val key: String, val defaultValue: String?)

interface PropertyResolver {
    fun contains(key: String): Boolean
    fun getProperty(key: String): String?
    fun getProperty(key: String, defaultValue: String): String
    fun <T> getProperty(key: String, clazz: Class<T>): T?
    fun <T> getProperty(key: String, default: T, clazz: Class<T>): T
    fun getRequiredProperty(key: String): String
    fun <T> getRequiredProperty(key: String, clazz: Class<T>): T
}

class ConfigPropertyResolver(props: Properties) : PropertyResolver {
    companion object {
        private val logger = LoggerFactory.getLogger(Companion::class.java)
        private const val CONFIG_APP_YAML: String = "/application.yml"
        private const val CONFIG_APP_PROP: String = "/application.properties"

        /**
         * Try load property resolver from /application.yml or /application.properties.
         */
        fun load(): ConfigPropertyResolver {
            var props = Properties()
            // try load application.yml:
            try {
                val yamlMap = loadYamlAsPlainMap(CONFIG_APP_YAML).filter { it.value is String } as Map<String, String>
                logger.info("load config: {}", CONFIG_APP_YAML)
                props = yamlMap.toProperties()
            } catch (e: UncheckedIOException) {
                if (e.cause is FileNotFoundException) {
                    // try load application.properties:
                    ClassPathUtils.readInputStream(CONFIG_APP_PROP) { input ->
                        logger.info("load config: {}", CONFIG_APP_PROP)
                        props.load(input)
                    }
                }
            }
            return ConfigPropertyResolver(props)
        }
    }

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val properties: MutableMap<String, String> = mutableMapOf()
    private val converters: MutableMap<Class<*>, (String) -> Any> = mutableMapOf(
        String::class.java to { s -> s },
        Boolean::class.java to { s -> s.toBoolean() },
        java.lang.Boolean::class.java to { s -> s.toBoolean() },
        Byte::class.java to { s -> s.toByte() },
        java.lang.Byte::class.java to { s -> s.toByte() },
        Short::class.java to { s -> s.toShort() },
        java.lang.Short::class.java to { s -> s.toShort() },
        Int::class.java to { s -> s.toInt() },
        java.lang.Integer::class.java to { s -> s.toInt() },
        Long::class.java to { s -> s.toLong() },
        java.lang.Long::class.java to { s -> s.toLong() },
        Float::class.java to { s -> s.toFloat() },
        java.lang.Float::class.java to { s -> s.toFloat() },
        Double::class.java to { s -> s.toDouble() },
        java.lang.Double::class.java to { s -> s.toDouble() },
        LocalDate::class.java to LocalDate::parse,
        LocalTime::class.java to LocalTime::parse,
        LocalDateTime::class.java to LocalDateTime::parse,
        ZonedDateTime::class.java to ZonedDateTime::parse,
        Duration::class.java to Duration::parse,
        ZoneId::class.java to ZoneId::of,
    )

    init {
        properties += System.getenv()
        @Suppress("UNCHECKED_CAST")
        properties += props.toMap() as Map<String, String>
        if (logger.isDebugEnabled) {
            properties.toSortedMap().forEach { (key, value) ->
                logger.debug("PropertyResolver: $key = $value")
            }
        }
    }

    override fun contains(key: String) = properties.containsKey(key)

    override fun getProperty(key: String): String? {
        val keyExpr = parsePropertyExpr(key)
        if (keyExpr != null) {
            return if (keyExpr.defaultValue != null) {
                getProperty(keyExpr.key, keyExpr.defaultValue)
            } else {
                getRequiredProperty(keyExpr.key)
            }
        }
        val value = properties[key]
        return if (value != null) parseValue(value) else null
    }

    override fun getProperty(key: String, defaultValue: String): String {
        return getProperty(key) ?: parseValue(defaultValue)
    }

    override fun <T> getProperty(key: String, clazz: Class<T>): T? {
        val value = getProperty(key) ?: return null
        return getConverter(clazz).invoke(value)
    }

    override fun <T> getProperty(key: String, default: T, clazz: Class<T>): T {
        return getProperty(key, clazz) ?: default
    }

    override fun getRequiredProperty(key: String) =
        getProperty(key) ?: throw NullPointerException("key: $key not found")

    override fun <T> getRequiredProperty(key: String, clazz: Class<T>) =
        getProperty(key, clazz) ?: throw NullPointerException("key: $key not found")

    private fun <T> getConverter(clazz: Class<T>): (String) -> T {
        @Suppress("UNCHECKED_CAST")
        return converters[clazz] as? (String) -> T ?: throw IllegalArgumentException("unsupported type: $clazz")
    }

    private fun parseValue(value: String): String {
        val expr = parsePropertyExpr(value) ?: return value
        return if (expr.defaultValue != null) {
            getProperty(expr.key, expr.defaultValue)
        } else {
            getRequiredProperty(expr.key)
        }
    }

    private fun parsePropertyExpr(key: String): PropertyExpr? {
        if (key.startsWith("\${") && key.endsWith("}")) {
            val n = key.indexOf(':')
            if (n == -1) {
                // no default value: ${key}
                val k = key.substring(2, key.length - 1).also {
                    if (it.isEmpty()) throw IllegalArgumentException("Invalid key: $it")
                }
                return PropertyExpr(k, null)
            } else {
                // has default value: ${key:default}
                val k = key.substring(2, n).also {
                    if (it.isEmpty()) throw IllegalArgumentException("Invalid key: $it")
                }
                return PropertyExpr(k, key.substring(n + 1, key.length - 1))
            }
        }
        return null
    }
}