package org.example.autumn.servlet.router

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.example.autumn.servlet.IDispatcher
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
    fun getRoute() {
        val router = createTestRouter()
        val (disp, params) = router.getRoute("GET", "/hello/aaa")!!
        assertEquals("aaa", params["name"])
    }
}