package org.example.autumn.servlet

import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.resolver.PropertyResolver
import org.example.autumn.utils.JsonUtils.toJsonAsBytes
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeEach
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockServletContext
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DispatcherServletTest {
    private lateinit var dispatcherServlet: DispatcherServlet
    private lateinit var ctx: MockServletContext

    @Test
    fun getHello() {
        val req = createMockRequest("GET", "/hello/Alice")
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("text/html", resp.contentType)
        assertEquals("Hello, Alice", resp.contentAsString)
    }

    @Test
    fun getStaticResource() {
        val req = createMockRequest("GET", "/static/autumn.png")
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("image/png", resp.contentType)
    }

    @Test
    fun getApiHello() {
        val req = createMockRequest("GET", "/api/hello/Bob")
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("application/json", resp.contentType)
        assertEquals("{\"name\":\"Bob\"}", resp.contentAsString)
    }

    @Test
    fun getGreeting() {
        val req = createMockRequest("GET", "/greeting", null, mapOf("name" to "Bob"))
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("Hello, Bob", resp.contentAsString)
    }

    @Test
    fun getApiGreeting() {
        val req = createMockRequest("GET", "/api/greeting", null, mapOf("name" to "Bob"))
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("application/json", resp.contentType)
        assertEquals("{\"action\":{\"name\":\"Bob\"}}", resp.contentAsString)
    }

    @Test
    fun getGreeting2() {
        val req = createMockRequest("GET", "/greeting", null, mapOf("action" to "Morning", "name" to "Bob"))
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("Morning, Bob", resp.contentAsString)
    }

    @Test
    fun getGreeting3() {
        val req = createMockRequest("GET", "/greeting", null, mapOf("action" to "Morning"))
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(400, resp.status)
    }

    @Test
    fun getDownload() {
        val req = createMockRequest(
            "GET", "/download/server.jar", null, mapOf(
                "hasChecksum" to "true",
                "length" to "8",
                "time" to "123.4",
                "md5" to "aee9e38cb4d40ec2794542567539b4c8"
            )
        )
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertArrayEquals("AAAAAAAA".toByteArray(), resp.contentAsByteArray)
    }

    @Test
    fun getDownload2() {
        val req = createMockRequest(
            "GET", "/download2/server.jar", null, mapOf(
                "hasChecksum" to "true",
                "length" to "8",
                "time" to "123.4",
                "md5" to "aee9e38cb4d40ec2794542567539b4c8"
            )
        )
        req.addHeader("header1", "test_header1")
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertArrayEquals("AAAAAAAA".toByteArray(), resp.contentAsByteArray)
    }

    @Test
    fun getApiError() {
        val req = createMockRequest("GET", "/api/error/402/test_402_error")
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(402, resp.status)
        assertEquals("text/html", resp.contentType)
        assertTrue(resp.contentAsString == "test_402_error")

        val req2 = createMockRequest("GET", "/api/error/400")
        val resp2 = createMockResponse()
        dispatcherServlet.service(req2, resp2)
        assertEquals(400, resp2.status)
        assertEquals("text/html", resp2.contentType)
        assertTrue(resp2.contentAsString.contains("400 Error!"))

        val req3 = createMockRequest("GET", "/api/error")
        val resp3 = createMockResponse()
        dispatcherServlet.service(req3, resp3)
        assertEquals(500, resp3.status)
        assertEquals("text/html", resp3.contentType)
    }

    @Test
    fun getApiDownload() {
        val req = createMockRequest(
            "GET", "/api/download/server.jar", null, mapOf(
                "hasChecksum" to "true",
                "length" to "8",
                "time" to "123.4",
                "md5" to "aee9e38cb4d40ec2794542567539b4c8"
            )
        )
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("application/json", resp.contentType)
        assertTrue(resp.contentAsString.contains("\"file\":\"server.jar\""))
        assertTrue(resp.contentAsString.contains("\"length\":8"))
        assertTrue(resp.contentAsString.contains("\"content\":\"QUFBQUFBQUE=\""))
    }

    @Test
    fun getDownloadPart() {
        val req = createMockRequest("GET", "/download-part")
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(206, resp.status)
        assertEquals("bytes=100-108", resp.getHeader("Range"))
        assertArrayEquals("AAAAAAAA".toByteArray(), resp.contentAsByteArray)
    }

    @Test
    fun getApiDownloadPart() {
        val req = createMockRequest(
            "GET", "/api/download-part", null, mapOf(
                "file" to "server.jar",
                "hasChecksum" to "true",
                "length" to "8",
                "time" to "123.4",
                "md5" to "aee9e38cb4d40ec2794542567539b4c8"
            )
        )
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("application/json", resp.contentType)
        assertTrue(resp.contentAsString.contains("\"file\":\"server.jar\""))
        assertTrue(resp.contentAsString.contains("\"length\":8"))
        assertTrue(resp.contentAsString.contains("\"content\":\"QUFBQUFBQUE=\""))
    }

    @Test
    fun getLogin() {
        val req = createMockRequest("GET", "/login")
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(302, resp.status)
        assertEquals("/signin", resp.redirectedUrl)
    }

    @Test
    fun getProduct() {
        val req = createMockRequest("GET", "/product/123", null, mapOf("name" to "Bob"))
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertTrue(resp.contentAsString.contains("<h1>Hello, Bob</h1>"))
        assertTrue(resp.contentAsString.contains("<a href=\"/product/123\">Autumn Software</a>"))
    }

    @Test
    fun postSignin() {
        val req = createMockRequest("POST", "/signin", null, mapOf("name" to "Bob", "password" to "hello123"))
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(302, resp.status)
        assertEquals("/home?name=Bob", resp.redirectedUrl)
    }

    @Test
    fun postRegister() {
        val req = createMockRequest("POST", "/register", null, mapOf("name" to "Bob", "password" to "hello123"))
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertTrue(resp.contentAsString.contains("<h1>Welcome, Bob</h1>"))
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

    @Test
    fun postApiRegister() {
        val signin = SigninObj()
        signin.name = "Bob"
        signin.password = "hello123"
        val req = createMockRequest("POST", "/api/register", signin)
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("application/json", resp.contentType)
        assertEquals("[\"Bob\",true,12345]", resp.contentAsString)
    }

    @Test
    fun postSignout() {
        val req = createMockRequest("POST", "/signout", null, mapOf("name" to "Bob"))
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(302, resp.status)
        assertEquals("/signin?name=Bob", resp.redirectedUrl)
        assertEquals(true, req.session!!.getAttribute("signout"))
    }

    private fun createPropertyResolver(): PropertyResolver {
        return PropertyResolver(
            mapOf(
                "app.title" to "Scan App",
                "app.version" to "v1.0",
                "autumn.web.favicon-path" to "/icon/favicon.ico",
                "autumn.web.freemarker.template-path" to "/WEB-INF/templates",
                "jdbc.username" to "sa",
                "jdbc.password" to "",
            ).toProperties()
        )
    }

    private fun createMockServletContext(): MockServletContext {
        val path = Path.of("./src/test/resources").toAbsolutePath().normalize()
        val ctx = MockServletContext(
            if (System.getProperty("os.name").lowercase().contains("windows")) "file:///$path" else "file://$path"
        )
        ctx.requestCharacterEncoding = "UTF-8"
        ctx.responseCharacterEncoding = "UTF-8"
        return ctx
    }

    private fun createMockRequest(
        method: String, path: String, body: Any? = null, params: Map<String, String>? = null
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
