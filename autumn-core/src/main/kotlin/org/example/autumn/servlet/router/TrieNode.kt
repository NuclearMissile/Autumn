package org.example.autumn.servlet.router

data class TrieNode(
    var pattern: String = "",
    val part: String = "",
    val children: MutableList<TrieNode> = mutableListOf(),
    val isWildcard: Boolean = false,
) {
    fun insert(path: String, parts: List<String>, height: Int) {
        if (parts.size == height) {
            pattern = path
            return
        }

        val _part = parts[height]
        var child = matchChild(_part)
        if (child == null) {
            child = TrieNode(part = _part, isWildcard = _part.startsWith(':') || _part.startsWith('*'))
            children.add(child)
        }
        child.insert(path, parts, height + 1)
    }

    fun search(parts: List<String>, height: Int): TrieNode? {
        if (parts.size == height || part.startsWith('*')) {
            return if (pattern.isEmpty()) null else this
        }

        val _part = parts[height]
        val children = matchChildren(_part)

        for (child in children) {
            val ret = child.search(parts, height + 1)
            if (ret != null) return ret
        }
        return null
    }

    fun travel(list: MutableList<TrieNode>) {
        if (pattern.isNotEmpty()) {
            list.add(this)
        }
        for (child in children) {
            child.travel(list)
        }
    }

    fun matchChild(part: String): TrieNode? {
        return children.find { it.part == part || it.isWildcard }
    }

    fun matchChildren(part: String): List<TrieNode> {
        return children.filter { it.part == part || it.isWildcard }
    }
}

