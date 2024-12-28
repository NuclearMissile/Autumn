package org.example.autumn.servlet

import org.example.autumn.exception.AutumnException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


class RouterTest {
    private lateinit var router: Router<String>
    private val dummyHandler = "DUMMY_HANDLER"

    @BeforeEach
    fun setUp() {
        router = Router<String>()
    }

    @Test
    fun testNotFound() {
        router.add("GET", "/a/{id}", dummyHandler)
        var result = router.match("GET", "/a")
        assertNull(result)
        result = router.match("POST", "/a/123")
        assertNull(result)
    }

    @Test
    fun testRoute() {
        router.add("GET", "/campaigns/{id}", dummyHandler)
        router.add("GET", "/campaigns/{id:[0-9]+}", dummyHandler)
        router.add("GET", "/campaigns/{id:[a-zA-Z]+}", dummyHandler)
        router.add("GET", "/campaigns/123", dummyHandler)
        router.add("GET", "/campaigns/123/{id}", dummyHandler)
        router.add("GET", "/campaigns/123/details", dummyHandler)
        router.add("GET", "/campaigns/456/details", dummyHandler)
        router.add("GET", "/campaigns", dummyHandler)
        router.add("POST", "/creatives", dummyHandler)

        var result = router.match("GET", "/campaigns/123")
        assertNotNull(result)
        assertTrue { result.params.isEmpty() }

        result = router.match("GET", "/campaigns/abc")
        assertNotNull(result)
        assertEquals("/campaigns/{id:[a-zA-Z]+}", result.path)
        assertEquals("abc", result.params["id"])

        result = router.match("GET", "/campaigns/456/details")
        assertNotNull(result)
        assertEquals("/campaigns/456/details", result.path)
        assertTrue { result.params.isEmpty() }

        result = router.match("GET", "/campaigns/789")
        assertNotNull(result)
        assertEquals("/campaigns/{id:[0-9]+}", result.path)
        assertEquals("789", result.params["id"])

        result = router.match("GET", "/creatives")
        assertNull(result)

        println(router)
    }

    @Test
    fun testConflict() {
        router.add("GET", "/campaigns/{id}", dummyHandler)
        try {
            router.add("GET", "/campaigns/{name}", dummyHandler)
        } catch (e: AutumnException) {
            assertTrue { e.message!!.contains("conflicts with existing route") }
        }

        router.add("GET", "/campaigns/{id:[0-9]+}", dummyHandler)
        try {
            router.add("GET", "/campaigns/{name:[0-9]+}", dummyHandler)
        } catch (e: AutumnException) {
            assertTrue { e.message!!.contains("conflicts with existing route") }
        }

        router.add("GET", "/campaigns/{id}/{name}", dummyHandler)
        println(router)
    }

    @Test
    fun testUnsupportedMethod() {
        try {
            router.add("DELETE", "/a/{id}", dummyHandler)
        } catch (e: IllegalArgumentException) {
            assertTrue { e.message!!.contains("is not supported") }
        }
    }

    @Test
    fun testPathVar(){
        var pathVar = PathVar.create("{id:[0-9]+}")
        assertEquals("[0-9]+", pathVar.pattern)
        assertTrue { pathVar.match("123") }
        assertFalse { pathVar.match("abc") }
        assertEquals("id", pathVar.name)

        pathVar = PathVar.create("{id}")
        assertNull(pathVar.pattern)
        assertEquals("id", pathVar.name)
    }
}