package org.example.autumn.servlet.router

import org.example.autumn.servlet.IDispatcher
import kotlin.math.max

data class TrieNode(
    var path: String = "",
    var indices: String = "",
    var isWildChild: Boolean = false,
    var type: NodeType = NodeType.STATIC,
    var priority: Int = 0,
    var children: MutableList<TrieNode>? = null,
    var dispatcher: IDispatcher? = null,
) {
    companion object {
        enum class NodeType {
            STATIC, ROOT, PARAM, CATCH_ALL
        }

        fun longestCommonPrefix(a: String, b: String): Int {
            var result = 0
            val max = max(a.length, b.length)
            while (result < max && a[result] == b[result]) {
                result++
            }
            return result
        }

        fun countParams(path: String) = path.count { it == ':' || it == '*' }

        /**
         * Search for a wildcard segment and check the name for invalid characters.
         * Returns -1 as index, if no wildcard was found.
         *
         * @return Triple(wildcard, startIndex, isValid)
         */
        fun findWildcard(path: String): Triple<String, Int, Boolean> {
            var isValid = false
            for (start in path.indices) {
                val c = path[start]
                if (c != ':' && c != '*') continue

                isValid = true
                for (end in start + 1..<path.length) {
                    val cc = path[end]
                    if (cc == '/') return Triple(path.substring(start, end), start, isValid)
                    if (cc == ':' || cc == '*') isValid = false
                }
                return Triple(path.substring(start), start, isValid)
            }
            return Triple("", -1, false)
        }
    }

    /**
     * Increments priority of the given child and reorders if necessary
     */
    fun incChildPriority(pos: Int): Int {
        children!![pos].priority++
        val priority = children!![pos].priority

        var newPos = pos
        while (newPos > 0 && children!![newPos - 1].priority < priority) {
            val tmp = children!![newPos - 1]
            children!![newPos - 1] = children!![newPos]
            children!![newPos] = tmp
            newPos--
        }

        if (newPos != pos) {
            indices = indices.substring(0, newPos) +
                indices.substring(pos, pos + 1) + indices.substring(newPos, pos) + indices.substring(pos+1)
        }

        return newPos
    }
}

