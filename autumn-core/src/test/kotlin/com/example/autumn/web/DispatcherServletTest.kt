package com.example.autumn.web

import com.example.autumn.context.AnnotationConfigApplicationContext
import com.example.autumn.resolver.PropertyResolver
import com.example.autumn.utils.JsonUtils.toJsonAsBytes
import com.example.autumn.web.controller.ControllerConfiguration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockServletContext
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class DispatcherServletTest {
    private lateinit var dispatcherServlet: DispatcherServlet
    private lateinit var ctx: MockServletContext

    @Test
    fun getHello() {
        val req = createMockRequest("GET", "/hello/Alice", null, null)
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("text/html", resp.contentType)
        assertEquals("Hello, Alice", resp.contentAsString)
    }

    @Test
    fun getApiHello() {
        val req = createMockRequest("GET", "/api/hello/Bob", null, null)
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("application/json", resp.contentType)
        assertEquals("{\"name\":\"Bob\"}", resp.contentAsString)
    }

    @Test
    fun getProduct() {
        val req = createMockRequest("GET", "/product/123", null, mapOf("name" to "Bob"))
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        Assertions.assertEquals(200, resp.status)
        Assertions.assertTrue(resp.contentAsString.contains("<h1>Hello, Bob</h1>"))
        Assertions.assertTrue(resp.contentAsString.contains("<a href=\"/product/123\">Autumn Software</a>"))
    }

    @BeforeEach
    fun init() {
        ctx = createMockServletContext()
        WebMvcConfiguration.servletContext = ctx
        val propertyResolver = createPropertyResolver()
        val applicationContext = AnnotationConfigApplicationContext(
            ControllerConfiguration::class.java, propertyResolver
        )
        dispatcherServlet = DispatcherServlet(applicationContext, propertyResolver)
        dispatcherServlet.init()
    }

    private fun createPropertyResolver(): PropertyResolver {
        return PropertyResolver(
            mapOf(
                "app.title" to "Scan App",
                "app.version" to "v1.0",
                "autumn.web.favicon-path" to "/icon/favicon.ico",
                "autumn.web.freemarker.template-path" to "/templates",
                "jdbc.username" to "sa",
                "jdbc.password" to "",
            ).toProperties()
        )
    }

    private fun createMockServletContext(): MockServletContext {
        val path = Path.of("./src/test/resources").toAbsolutePath().normalize()
        val ctx = MockServletContext("file://$path")
        ctx.requestCharacterEncoding = "UTF-8"
        ctx.responseCharacterEncoding = "UTF-8"
        return ctx
    }

    private fun createMockRequest(
        method: String, path: String, body: Any?, params: Map<String, String>?
    ): MockHttpServletRequest {
        val req = MockHttpServletRequest(ctx, method, path)
        if (method == "GET" && params != null) {
            params.forEach { (k, v) -> req.setParameter(k, v) }
        } else if (method == "POST") {
            if (body != null) {
                req.contentType = "application/json"
                req.setContent(body.toJsonAsBytes())
            } else {
                req.contentType = "application/x-www-form-urlencoded"
                params?.forEach { (k, v) -> req.setParameter(k, v) }
            }
        }
        req.session = MockHttpSession()
        return req
    }

    private fun createMockResponse(): MockHttpServletResponse {
        val resp = MockHttpServletResponse()
        resp.setDefaultCharacterEncoding("UTF-8")
        return resp
    }
}