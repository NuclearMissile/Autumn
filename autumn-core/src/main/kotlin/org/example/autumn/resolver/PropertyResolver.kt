package org.example.autumn.resolver

import org.example.autumn.utils.YamlUtils.loadYamlAsPlainMap
import java.time.*
import java.util.*

data class PropertyExpr(val key: String, val defaultValue: String?)

interface PropertyResolver {
    fun contains(key: String): Boolean
    fun set(key: String, value: String)
    fun addAll(map: Map<String, String>)
    fun getString(key: String): String?
    fun getString(key: String, defaultValue: String): String
    fun <T> get(key: String, clazz: Class<T>): T?
    fun <T> get(key: String, default: T, clazz: Class<T>): T
    fun getRequiredString(key: String): String
    fun <T> getRequired(key: String, clazz: Class<T>): T
    fun merge(other: PropertyResolver): PropertyResolver
    fun getMap(): Map<String, String>
}

inline fun <reified T> PropertyResolver.get(key: String): T? {
    return get(key, T::class.java)
}

inline fun <reified T> PropertyResolver.get(key: String, default: T): T {
    return get(key, default, T::class.java)
}

inline fun <reified T> PropertyResolver.getRequired(key: String): T {
    return getRequired(key, T::class.java)
}

class Config(props: Properties) : PropertyResolver {
    companion object {
        private const val CONFIG_YML = "/config.yml"
        private const val DEFAULT_CONFIG_YML = "/__default-config__.yml"

        fun load(): PropertyResolver {
            return loadYaml(DEFAULT_CONFIG_YML).merge(loadYaml(CONFIG_YML))
        }

        fun loadYaml(yamlPath: String, fromClassPath: Boolean = true): PropertyResolver {
            return Config(loadYamlAsPlainMap(yamlPath, fromClassPath).toProperties())
        }
    }

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
    }

    override fun merge(other: PropertyResolver): PropertyResolver {
        properties += other.getMap()
        return this
    }

    override fun getMap(): Map<String, String> {
        return properties
    }

    override fun contains(key: String) = properties.containsKey(key)

    override fun set(key: String, value: String) {
        properties[key] = value
    }

    override fun addAll(map: Map<String, String>) {
        properties += map
    }

    override fun getString(key: String): String? {
        val keyExpr = parsePropertyExpr(key)
        if (keyExpr != null) {
            return if (keyExpr.defaultValue != null) {
                getString(keyExpr.key, keyExpr.defaultValue)
            } else {
                getRequiredString(keyExpr.key)
            }
        }
        val value = properties[key]
        return if (value != null) parseValue(value) else null
    }

    override fun getString(key: String, defaultValue: String): String {
        return getString(key) ?: parseValue(defaultValue)
    }

    override fun <T> get(key: String, clazz: Class<T>): T? {
        val value = getString(key) ?: return null
        return getConverter(clazz).invoke(value)
    }

    override fun <T> get(key: String, default: T, clazz: Class<T>): T {
        return get(key, clazz) ?: default
    }

    override fun getRequiredString(key: String) =
        getString(key) ?: throw IllegalArgumentException("key: $key not found")

    override fun <T> getRequired(key: String, clazz: Class<T>) =
        get(key, clazz) ?: throw IllegalArgumentException("key: $key not found")

    private fun <T> getConverter(clazz: Class<T>): (String) -> T {
        @Suppress("UNCHECKED_CAST")
        return converters[clazz] as? (String) -> T ?: throw IllegalArgumentException("unsupported type: $clazz")
    }

    private fun parseValue(value: String): String {
        val expr = parsePropertyExpr(value) ?: return value
        return if (expr.defaultValue != null) {
            getString(expr.key, expr.defaultValue)
        } else {
            getRequiredString(expr.key)
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