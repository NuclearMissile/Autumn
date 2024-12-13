package org.example.autumn.servlet.router

import org.example.autumn.servlet.router.TrieNode.Companion.findWildcard
import kotlin.test.Test
import kotlin.test.assertEquals

class TrieNodeTest {
    private val wildcardCases = listOf(
        "/" to Triple("", -1, false),
        "/cmd/:tool/:sub" to Triple(":tool", 5, true),
        "/cmd/:tool/" to Triple(":tool", 5, true),
        "/src/*filepath" to Triple("*filepath", 5, true),
        "/search/" to Triple("", -1, false),
        "/search/:query" to Triple(":query", 8, true),
        "/user_:name" to Triple(":name", 6, true),
        "/user_:name/about" to Triple(":name", 6, true),
        "/files/:dir/*filepath" to Triple(":dir", 7, true),
        "/doc/" to Triple("", -1, false),
        "/doc/go_faq.html" to Triple("", -1, false),
        "/doc/go1.html" to Triple("", -1, false),
        "/info/:user/public" to Triple(":user", 6, true),
        "/info/:user/project/:project" to Triple(":user", 6, true),
    )

    @Test
    fun testFindWildcard() {
        for (case in wildcardCases) {
            assertEquals(case.second, findWildcard(case.first))
        }
    }
}