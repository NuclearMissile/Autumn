package org.example.autumn.servlet.router

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.example.autumn.servlet.IDispatcher
import org.example.autumn.servlet.router.Router.Companion.normalizePath
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RouterTest {
    private val DUMMY_DISP = object : IDispatcher {
        override val controllerBeanName: String
            get() = TODO("Not yet implemented")
        override val produce: String
            get() = TODO("Not yet implemented")
        override val isRest: Boolean
            get() = TODO("Not yet implemented")
        override val isResponseBody: Boolean
            get() = TODO("Not yet implemented")
        override val isVoid: Boolean
            get() = TODO("Not yet implemented")

        override fun process(
            url: String,
            params: Map<String, String>,
            req: HttpServletRequest,
            resp: HttpServletResponse,
            exception: Exception?,
        ): Any? {
            TODO("Not yet implemented")
        }
    }

    private val CLEAN_TESTS = listOf(
        // OK
        Pair("/", "/"),
        Pair("/abc", "/abc"),
        Pair("/a/b/c", "/a/b/c"),
        Pair("/abc/", "/abc/"),
        Pair("/a/b/c/", "/a/b/c/"),

        // Missing root
        Pair("", "/"),
        Pair("a/", "/a/"),
        Pair("abc", "/abc"),
        Pair("abc/def", "/abc/def"),
        Pair("a/b/c", "/a/b/c"),

        // Double slash
        Pair("//", "/"),
        Pair("/abc//", "/abc/"),
        Pair("/abc/def//", "/abc/def/"),
        Pair("/a/b/c//", "/a/b/c/"),
        Pair("//a//b//c", "/a/b/c"),
        Pair("//abc", "/abc"),
        Pair("///abc", "/abc"),
        Pair("//abc//", "/abc/"),

        // Remove . elements
        Pair(".", "/"),
        Pair("./", "/"),
        Pair("/abc/./def", "/abc/def"),
        Pair("/./abc/def", "/abc/def"),
        Pair("/abc/.", "/abc"),

        // Remove .. elements
        Pair("..", "/"),
        Pair("../", "/"),
        Pair("../../", "/"),
        Pair("../..", "/"),
        Pair("../../abc", "/abc"),
        Pair("/abc/def/ghi/../jkl", "/abc/def/jkl"),
        Pair("/abc/def/../ghi/../jkl", "/abc/jkl"),
        Pair("/abc/def/..", "/abc"),
        Pair("/abc/def/../..", "/"),
        Pair("/abc/def/../../..", "/"),
        Pair("/abc/def/../../..", "/"),
        Pair("/abc/def/../../../ghi/jkl/../../../mno", "/mno"),

        // Combinations
        Pair("abc/./../def", "/def"),
        Pair("abc//./../def", "/def"),
        Pair("abc/../../././../def", "/def"),
    )

    fun createTestRouter(): Router {
        return Router().apply {
            setRoute("GET", "/", DUMMY_DISP)
            setRoute("GET", "/hello", DUMMY_DISP)
            setRoute("GET", "/hello/:name", DUMMY_DISP)
            setRoute("GET", "/hi/:name", DUMMY_DISP)
            setRoute("GET", "/hello/a/b/c", DUMMY_DISP)
            setRoute("GET", "/static/*filepath", DUMMY_DISP)
        }
    }

    @Test
    fun testGetRoute() {
        val router = createTestRouter()
        val (_, params) = router.getRoute("GET", "/hello/aaa")!!
        assertEquals("aaa", params["name"])
    }

    @Test
    fun testCleanPath() {
        CLEAN_TESTS.forEach {
            assertEquals(it.second, normalizePath(it.first))
        }
    }
}