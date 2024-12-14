package org.example.autumn.servlet.router

import org.example.autumn.servlet.IDispatcher
import java.util.*

interface IRouter {
    fun setRoute(method: String, path: String, dispatcher: IDispatcher)
    fun getRoute(method: String, path: String): Pair<IDispatcher, Map<String, String>>?
}

class Router : IRouter {
    companion object {
        fun normalizePath(p: String): String {
            val stack = ArrayDeque<String>()
            for (part in p.split("/")) {
                if (part == "." || part.isEmpty()) {
                    continue
                } else if (part == "..") {
                    if (stack.isNotEmpty()) stack.pop()
                } else {
                    stack.push(part)
                }
            }
            val trailingSlash = if (p.length > 1 && p.last() == '/' && stack.isNotEmpty()) "/" else ""
            return stack.reversed().joinToString("/", prefix = "/", postfix = trailingSlash)
        }
    }

    private val roots = mutableMapOf<String, TrieNode>()
    private val dispatchers = mutableMapOf<String, IDispatcher>()

    override fun setRoute(method: String, path: String, disp: IDispatcher) {
        TODO()
    }

    override fun getRoute(method: String, path: String): Pair<IDispatcher, Map<String, String>>? {
        TODO()
    }
}