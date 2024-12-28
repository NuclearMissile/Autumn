package org.example.autumn.servlet

import org.example.autumn.exception.AutumnException
import org.example.autumn.servlet.Router.Companion.isPathVar

internal data class Route<T>(val method: String, val path: String, val handler: T)

internal data class PathVar(val name: String, val pattern: String?, private val regex: Regex?) {
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

data class MatchResult<T>(val method: String, val path: String, val handler: T, val params: Map<String, String>)

internal class TrieNode<T>(
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
    val regexPathVarChildren = sortedSetOf<TrieNode<T>>(COMPARATOR)
    var pathVarChild: TrieNode<T>? = null

    internal fun addChild(key: String, child: TrieNode<T>) {
        children[key] = child
        child.value = key
        if (child.pathVar != null) {
            if (child.pathVar!!.pattern != null) {
                regexPathVarChildren.add(child)
            } else {
                pathVarChild = child
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
         * Method GET:
         * ROOT
         *  ==> hello --> path: </hello/>, handler: helloController.hello[0]
         *  	==> error --> path: </hello/error>, handler: helloController.error[0]
         *  		==> {var} --> path: </hello/error/{errorCode}>, handler: helloController.error[1]
         *  			==> {var} --> path: </hello/error/{errorCode}/{errorResp}>, handler: helloController.error[2]
         *  	==> echo --> path: </hello/echo>, handler: helloController.echo[1]
         *  ==> register --> path: </register>, handler: indexController.register[1]
         *  ==> changePassword --> path: </changePassword>, handler: indexController.changePassword[1]
         *  ==> logoff --> path: </logoff>, handler: indexController.logoff[1]
         *  ==> login --> path: </login>, handler: indexController.login[1]
         *  ==> api
         *  	==> params --> path: </api/params>, handler: restApiController.params[1]
         *  	==> error --> path: </api/error>, handler: restApiController.error[0]
         *  		==> {var} --> path: </api/error/{status}>, handler: restApiController.error[1]
         *  			==> {var} --> path: </api/error/{errorCode}/{errorResp}>, handler: restApiController.error[2]
         *  	==> hello
         *  		==> {var} --> path: </api/hello/{name}>, handler: restApiController.hello[1]
         *
         * Method POST:
         * ROOT
         * 	==> register --> path: </register>, handler: indexController.register[3]
         * 	==> changePassword --> path: </changePassword>, handler: indexController.changePassword[4]
         * 	==> login --> path: </login>, handler: indexController.login[3]
         */
        val result = StringBuilder()
        result.append(
            if (node.value.isBlank()) "ROOT"
            else node.value + if (node.route == null) "" else " --> path: <${node.route!!.path}>, handler: ${node.route!!.handler}"
        )
        result.append("\n")
        for ((_, v) in node.children) {
            result.append("\t".repeat(level + 1)).append("==> ")
            result.append(printNode(v, level + 1))
        }
        return result.toString()
    }
}

class Router<T> {
    companion object {
        private const val PATH_SEPARATOR = "/"
        private const val PATH_VARIABLE_PREFIX = "{"
        private const val PATH_VARIABLE_SUFFIX = "}"
        private val SUPPORTED_METHODS = listOf("GET", "POST")

        fun isPathVar(part: String): Boolean =
            part.startsWith(PATH_VARIABLE_PREFIX) && part.endsWith(PATH_VARIABLE_SUFFIX)
    }

    private val roots = mutableMapOf<String, TrieNode<T>>()

    init {
        SUPPORTED_METHODS.forEach { method -> roots[method] = TrieNode<T>() }
    }

    fun match(method: String, path: String): MatchResult<T>? {
        if (!SUPPORTED_METHODS.contains(method)) {
            throw IllegalArgumentException("method $method id not supported")
        }

        val current = roots[method]!!
        val parts = path.split(PATH_SEPARATOR).filter { it.isNotEmpty() }
        val params = mutableMapOf<String, String>()
        val matched = matchNode(current, parts, 0, params)
        if (matched != null && matched.route != null) {
            val route = matched.route!!
            return MatchResult(route.method, route.path, route.handler, params)
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
                key = if (pathVar.pattern == null) "{var}" else "{var:${pathVar.pattern}}"
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
    }

    private fun matchNode(
        node: TrieNode<T>, parts: List<String>, index: Int, params: MutableMap<String, String>,
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

        val pathVarNode = node.pathVarChild
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