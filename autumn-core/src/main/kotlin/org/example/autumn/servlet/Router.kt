package org.example.autumn.servlet

import org.example.autumn.exception.AutumnException
import org.example.autumn.servlet.Router.Companion.isPathVar

data class Route<T>(val method: String, val path: String, val handler: T)

data class PathVar(val name: String, val pattern: String?, private val regex: Regex?) {
    companion object {
        fun create(part: String): PathVar {
            var internalPart = part
            if (isPathVar(part)) {
                internalPart = part.substring(1, part.length - 1)
            }
            val parts = internalPart.split(':')
            if (parts.size > 1) {
                return PathVar(parts[0], parts[1], parts[1].toRegex())
            }
            return PathVar(parts[0], null, null)
        }
    }

    fun match(part: String): Boolean = regex != null && regex.matches(part)
}

class TrieNode<T>(
    var value: String = "",
    var route: Route<T>? = null,
    var pathVar: PathVar? = null,
) {
    companion object {
        private val COMPARATOR = object : Comparator<TrieNode<*>> {
            override fun compare(o1: TrieNode<*>, o2: TrieNode<*>): Int {
                val o1PathVar = o1.pathVar!!
                val o2PathVar = o2.pathVar!!
                if (o1PathVar.name != o2PathVar.name) {
                    return o1PathVar.name.compareTo(o2PathVar.name)
                }
                val o1HasRegex = o1PathVar.pattern != null
                val o2HasRegex = o2PathVar.pattern != null
                if (o1HasRegex != o2HasRegex) {
                    return if (o1HasRegex) -1 else 1
                }
                if (o1HasRegex && o2HasRegex) {
                    return o2PathVar.pattern.length - o1PathVar.pattern.length
                }
                return 0
            }
        }
    }

    val children = mutableMapOf<String, TrieNode<T>>()
    val pathVarChildren = sortedSetOf<TrieNode<T>>(COMPARATOR)
    val regexPathVarChildren = sortedSetOf<TrieNode<T>>(COMPARATOR)

    fun addChild(key: String, child: TrieNode<T>) {
        children[key] = child
        child.value = key
        if (child.pathVar != null) {
            if (child.pathVar!!.pattern != null) {
                regexPathVarChildren.add(child)
            } else {
                pathVarChildren.add(child)
            }
        }
    }

    fun hasChild(key: String) = children.containsKey(key)

    override fun toString(): String {
        return printNode(this, 0)
    }

    private fun printNode(node: TrieNode<T>, level: Int): String {
        /**
         * eg:
         * ROOT
         *     ==> campaigns --> path: </campaigns> handler: <<DUMMY>>
         *         ==> 123 --> path: </campaigns/123> handler: <<DUMMY>>
         *             ==> details --> path: </campaigns/123/details> handler: <<DUMMY>>
         *         ==> 789
         *             ==> detail --> path: </campaigns/789/detail> handler: <<DUMMY>>
         *         ==> {var:[0-9]+} --> path: </campaigns/{id:[0-9]+}> handler: <<DUMMY>>
         *         ==> {var} --> path: </campaigns/{id}> handler: <<DUMMY>>
         *     ==> apidocs
         *         ==> swagger.json --> path: </apidocs/swagger.json> handler: <<DUMMY>>
         *         ==> swagger.yaml --> path: </apidocs/swagger.yaml> handler: <<DUMMY>>
         */
        val result = StringBuilder()
        result.append(
            if (node.value.isBlank()) "ROOT"
            else node.value + if (node.route == null) "" else " --> path: <${node.route!!.path}> handler: ${node.route!!.handler}"
        )
        result.append("\n")
        for ((_, v) in node.children) {
            result.append("\t".repeat(level + 1)).append("==> ")
            result.append(printNode(v, level + 1))
        }
        return result.toString()
    }
}

data class MatchResult<T>(val route: Route<T>, val params: Map<String, String>)

class Router<T> {
    companion object {
        private const val PATH_SEPARATOR = "/"
        private const val PATH_VARIABLE_PREFIX = "{"
        private const val PATH_VARIABLE_SUFFIX = "}"
        private const val PATH_VARIABLE_KEY = "{var}"
        private val SUPPORTED_METHODS = listOf("GET", "POST")

        fun isPathVar(part: String): Boolean =
            part.startsWith(PATH_VARIABLE_PREFIX) && part.endsWith(PATH_VARIABLE_SUFFIX)
    }

    private val routes = mutableSetOf<Route<T>>()
    private val roots = mutableMapOf<String, TrieNode<T>>()

    init {
        SUPPORTED_METHODS.forEach { method -> roots[method] = TrieNode<T>() }
    }

    fun getRoutes() = routes.toList()

    fun match(method: String, path: String): MatchResult<T>? {
        if (!SUPPORTED_METHODS.contains(method)) {
            throw IllegalArgumentException("method $method id not supported")
        }

        val current = roots[method]!!
        val parts = path.split(PATH_SEPARATOR).filter { it.isNotEmpty() }
        val params = mutableMapOf<String, String>()
        val matched = matchNode(current, parts, 0, params)
        if (matched != null && matched.route != null) {
            return MatchResult(matched.route!!, params)
        }
        return null
    }

    fun add(method: String, path: String, handler: T) {
        if (!SUPPORTED_METHODS.contains(method)) {
            throw IllegalArgumentException("method `$method` is not supported")
        }

        val route = Route(method, path, handler)
        var current = roots[method] ?: throw AutumnException("unsupported method: $method")

        for (part in path.split(PATH_SEPARATOR).filter { it.isNotEmpty() }) {
            var key = part
            if (isPathVar(part)) {
                val pathVar = PathVar.create(part)
                key = if (pathVar.pattern == null) PATH_VARIABLE_KEY else "{var:${pathVar.pattern}}"
                if (!current.hasChild(key)) {
                    current.addChild(key, TrieNode(pathVar = pathVar))
                }
            } else if (!current.hasChild(key)) {
                current.addChild(key, TrieNode())
            }
            current = current.children[key]!!
        }

        if (current.route != null) {
            throw AutumnException("${route.path} conflicts with existing route ${current.route!!.path}")
        }
        current.route = route
        routes.add(route)
    }

    fun matchNode(
        node: TrieNode<T>,
        parts: List<String>,
        index: Int,
        params: MutableMap<String, String>,
    ): TrieNode<T>? {
        if (index == parts.size) {
            return if (node.route != null) node else null
        }

        val part = parts[index]
        val child = node.children[part]
        if (child != null) {
            val result = matchNode(child, parts, index + 1, params)
            if (result != null) {
                return result
            }
        }

        for (trieNode in node.regexPathVarChildren) {
            val pathVar = trieNode.pathVar
            if (pathVar != null && pathVar.match(part)) {
                params[pathVar.name] = part
                val result = matchNode(trieNode, parts, index + 1, params)
                if (result != null) {
                    return result
                }
            }
        }

        val pathVarNode = if (node.pathVarChildren.isEmpty()) null else node.pathVarChildren.iterator().next()
        if (pathVarNode != null) {
            params[pathVarNode.pathVar!!.name] = part
            return matchNode(pathVarNode, parts, index + 1, params)
        }

        return null
    }

    override fun toString(): String {
        val result = StringBuilder()
        roots.forEach { (method, root) ->
            if (root.children.isNotEmpty()) {
                result.append("Method $method:\n")
                result.append(root)
                result.append("\n")
            }
        }
        return result.toString()
    }
}