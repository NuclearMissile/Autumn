package org.example.autumn.utils

import org.example.autumn.utils.IOUtils.readInputStream
import org.example.autumn.utils.IOUtils.readInputStreamFromClassPath
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.nio.file.Path

object ConfigUtils {
    fun loadYamlAsPlainMap(path: String, fromClassPath: Boolean): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        flatten(loadYaml(path, fromClassPath), "", result)
        return result
    }

    private fun loadYaml(path: String, fromClassPath: Boolean): Map<String, Any> {
        val loaderOptions = LoaderOptions()
        val dumperOptions = DumperOptions()
        val representer = Representer(dumperOptions)
        val yaml = Yaml(Constructor(loaderOptions), representer, dumperOptions, loaderOptions, object : Resolver() {
            init {
                yamlImplicitResolvers.clear()
            }
        })
        return if (fromClassPath)
            readInputStreamFromClassPath(path) { input -> yaml.load(input) }
        else
            readInputStream(Path.of(path)) { input -> yaml.load(input) }
    }

    private fun flatten(source: Map<String, Any>, prefix: String, plain: MutableMap<String, Any>) {
        source.forEach { (k, v) ->
            when (v) {
                is Map<*, *> -> {
                    flatten(v as Map<String, Any>, "$prefix$k.", plain)
                }

                is List<*> -> {
                    plain[prefix + k] = v
                }

                else -> {
                    plain[prefix + k] = v.toString()
                }
            }
        }
    }
}
