package io.nuclearmissile.autumn.utils

import io.nuclearmissile.autumn.utils.IOUtils.readInputStream
import io.nuclearmissile.autumn.utils.IOUtils.readInputStreamFromClassPath
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.nio.file.Paths

object YamlUtils {
    fun loadYamlAsPlainMap(path: String, fromClassPath: Boolean): Map<String, String> {
        return mutableMapOf<String, String>().apply {
            flatten(loadYaml(path, fromClassPath), "", this)
        }
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
            readInputStream(Paths.get(path)) { input -> yaml.load(input) }
    }

    private fun flatten(source: Map<*, *>, prefix: String, plain: MutableMap<String, String>) {
        source.forEach { (k, v) ->
            when (v) {
                is Map<*, *> -> {
                    flatten(v, "$prefix$k.", plain)
                }

                is List<*> -> {
                    plain[prefix + k] = v.joinToString(separator = ",")
                }

                else -> {
                    plain[prefix + k] = v.toString()
                }
            }
        }
    }
}
