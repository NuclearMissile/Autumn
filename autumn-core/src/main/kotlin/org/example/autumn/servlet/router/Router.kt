package org.example.autumn.servlet.router

import org.example.autumn.servlet.IDispatcher

interface IRouter {
    fun addRoute(method: String, path: String, dispatcher: IDispatcher)
    fun getRoute(method: String, path: String): Pair<IDispatcher, Map<String, String>>?
}

class Router : IRouter {
    companion object {
        fun parsePath(path: String): List<String> {
            return buildList {
                for (part in path.split('/').filter { it.isNotEmpty() }) {
                    add(part)
                    if (part.startsWith('*')) return@buildList
                }
            }
        }
    }

    private val roots = mutableMapOf<String, TrieNode>()
    private val dispatchers = mutableMapOf<String, IDispatcher>()

    override fun addRoute(method: String, path: String, disp: IDispatcher) {
        val parts = parsePath(path)
        if (!roots.containsKey(method)) roots[method] = TrieNode()

        roots[method]!!.insert(path, parts, 0)
        dispatchers["${method}:${path}"] = disp
    }

    override fun getRoute(method: String, path: String): Pair<IDispatcher, Map<String, String>>? {
        val searchParts = parsePath(path)
        val params = mutableMapOf<String, String>()

        val root = roots[method] ?: return null
        val node = root.search(searchParts, 0)

        if (node != null) {
            val parts = parsePath(node.pattern)
            for (index in parts.indices) {
                val part = parts[index]
                if (part.startsWith(':')) {
                    params[part.substring(1)] = searchParts[index]
                }
                if (part.startsWith('*') && part.length > 1) {
                    params[part.substring(1)] = searchParts.slice(1 until searchParts.size).joinToString("/")
                    break
                }
            }
            return Pair(dispatchers["${method}_${node.pattern}"]!!, params)
        }

        return null
    }
}