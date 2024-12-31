package io.nuclearmissile.autumn.utils

import io.nuclearmissile.autumn.utils.ClassUtils.toPrimitive
import io.nuclearmissile.autumn.utils.YamlUtils.loadYamlAsPlainMap
import java.time.*
import java.util.*

data class PropertyExpr(val key: String, val defaultValue: String?)

interface IProperties {
    fun contains(key: String): Boolean
    fun set(key: String, value: String): IProperties
    fun addAll(map: Map<String, String>): IProperties
    fun getString(key: String): String?
    fun getString(key: String, defaultValue: String): String
    fun <T> get(key: String, clazz: Class<T>): T?
    fun <T> get(key: String, default: T, clazz: Class<T>): T
    fun getRequiredString(key: String): String
    fun <T> getRequired(key: String, clazz: Class<T>): T
    fun merge(other: IProperties): IProperties
    fun toMap(): Map<String, String>
}

inline fun <reified T> IProperties.get(key: String): T? {
    return get(key, T::class.java)
}

inline fun <reified T> IProperties.get(key: String, default: T): T {
    return get(key, default, T::class.java)
}

inline fun <reified T> IProperties.getRequired(key: String): T {
    return getRequired(key, T::class.java)
}

class ConfigProperties(props: Properties) : IProperties {
    companion object {
        private const val CONFIG_YML = "/config.yml"
        private const val DEFAULT_CONFIG_YML = "/__default-config__.yml"

        fun load(): IProperties {
            return loadYaml(DEFAULT_CONFIG_YML).merge(loadYaml(CONFIG_YML))
        }

        fun loadYaml(yamlPath: String, fromClassPath: Boolean = true): IProperties {
            return ConfigProperties(loadYamlAsPlainMap(yamlPath, fromClassPath).toProperties())
        }
    }

    private val properties: MutableMap<String, String> = mutableMapOf()

    init {
        properties += System.getenv()
        @Suppress("UNCHECKED_CAST")
        properties += props.toMap() as Map<String, String>
    }

    override fun merge(other: IProperties): IProperties {
        properties += other.toMap()
        return this
    }

    override fun toMap(): Map<String, String> {
        return properties.toMap()
    }

    override fun contains(key: String) = properties.containsKey(key)

    override fun set(key: String, value: String): IProperties {
        properties[key] = value
        return this
    }

    override fun addAll(map: Map<String, String>): IProperties {
        properties += map
        return this
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

    @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
    override fun <T> get(key: String, clazz: Class<T>): T? {
        val value = getString(key) ?: return null
        return value.toPrimitive(clazz) ?: when {
            LocalDate::class.java.isAssignableFrom(clazz) -> LocalDate.parse(value)
            LocalTime::class.java.isAssignableFrom(clazz) -> LocalTime.parse(value)
            LocalDateTime::class.java.isAssignableFrom(clazz) -> LocalDateTime.parse(value)
            ZonedDateTime::class.java.isAssignableFrom(clazz) -> ZonedDateTime.parse(value)
            Duration::class.java.isAssignableFrom(clazz) -> Duration.parse(value)
            ZoneId::class.java.isAssignableFrom(clazz) -> ZoneId.of(value)
            Iterable::class.java.isAssignableFrom(clazz) -> value.split(',').map(String::trim)
            else -> throw IllegalArgumentException("unsupported type to convert: $clazz")
        } as T?
    }

    override fun <T> get(key: String, default: T, clazz: Class<T>): T {
        return get(key, clazz) ?: default
    }

    override fun getRequiredString(key: String) =
        getString(key) ?: throw IllegalArgumentException("key: $key not found")

    override fun <T> getRequired(key: String, clazz: Class<T>) =
        get(key, clazz) ?: throw IllegalArgumentException("key: $key not found")

    private fun parseValue(value: String): String {
        val valueExpr = parsePropertyExpr(value) ?: return value
        return if (valueExpr.defaultValue != null) {
            getString(valueExpr.key, valueExpr.defaultValue)
        } else {
            getRequiredString(valueExpr.key)
        }
    }

    private fun parsePropertyExpr(s: String): PropertyExpr? {
        if (s.startsWith("\${") && s.endsWith("}")) {
            val n = s.indexOf(':')
            if (n == -1) {
                // no default value: ${key}
                val k = s.substring(2, s.length - 1).apply {
                    if (isEmpty()) throw IllegalArgumentException("Invalid key: $this")
                }
                return PropertyExpr(k, null)
            } else {
                // has default value: ${key:default}
                val k = s.substring(2, n).apply {
                    if (isEmpty()) throw IllegalArgumentException("Invalid key: $this")
                }
                return PropertyExpr(k, s.substring(n + 1, s.length - 1))
            }
        }
        return null
    }
}