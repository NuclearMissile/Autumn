package org.example.autumn.utils

import org.example.autumn.utils.ClassPathUtils.readInputStream
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.util.*

object ConfigUtils {
    fun loadProperties(path: String): Map<String, String> {
        // try load *.properties:
        return readInputStream(path) { input ->
            Properties().apply { load(input) }.toMap() as Map<String, String>
        }
    }

    fun loadYamlAsPlainMap(path: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        flatten(loadYaml(path), "", result)
        return result
    }

    private fun loadYaml(path: String): Map<String, Any> {
        val loaderOptions = LoaderOptions()
        val dumperOptions = DumperOptions()
        val representer = Representer(dumperOptions)
        val yaml = Yaml(Constructor(loaderOptions), representer, dumperOptions, loaderOptions, object : Resolver() {
            init {
                yamlImplicitResolvers.clear()
            }
        })
        return readInputStream(path) { input -> yaml.load(input) as Map<String, Any> }
    }

    private fun flatten(source: Map<String, Any>, prefix: String, plain: MutableMap<String, Any>) {
        for (key in source.keys) {
            when (val value = source[key]) {
                is Map<*, *> -> {
                    flatten(value as Map<String, String>, "$prefix$key.", plain)
                }

                is List<*> -> {
                    plain[prefix + key] = value
                }

                else -> {
                    plain[prefix + key] = value.toString()
                }
            }
        }
    }
}
