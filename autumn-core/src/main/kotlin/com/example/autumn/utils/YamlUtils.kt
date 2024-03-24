package com.example.autumn.utils

import com.example.autumn.utils.ClassPathUtils.readInputStream
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver

/**
 * Parse yaml by snakeyaml:
 *
 * https://github.com/snakeyaml/snakeyaml
 */
object YamlUtils {
    fun loadYaml(path: String): Map<String, Any> {
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

    fun loadYamlAsPlainMap(path: String): Map<String, Any> {
        val result: MutableMap<String, Any> = mutableMapOf()
        flatten(loadYaml(path), "", result)
        return result
    }

    fun flatten(source: Map<String, Any?>, prefix: String, plain: MutableMap<String, Any>) {
        for (key in source.keys) {
            when (val value = source[key]) {
                is Map<*, *> -> {
                    flatten(value as Map<String, Any>, "$prefix$key.", plain)
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
