package org.example.autumn.servlet

import jakarta.servlet.ServletException
import org.example.autumn.DEFAULT_ERROR_RESP_BODY
import org.example.autumn.context.AnnotationApplicationContext
import org.example.autumn.servlet.Dispatcher.Companion.compilePath
import org.example.autumn.utils.ConfigProperties
import org.example.autumn.utils.JsonUtils.readJson
import org.example.autumn.utils.JsonUtils.toJsonAsBytes
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockServletContext
import java.nio.file.Path
import kotlin.test.*

class DispatcherServletTest {
    private lateinit var dispatcherServlet: DispatcherServlet
    private lateinit var ctx: MockServletContext

    @Test
    fun testValidPath() {
        val p0 = compilePath("/user/{userId}/{orderId}")
        val m0 = p0.matchEntire("/user/0/11")
        assertNotNull(m0)
        assertEquals("0", m0.groups["userId"]!!.value)
        assertEquals("11", m0.groups[2]!!.value)

        assertNull(p0.matchEntire("/user/123/a/456"))

        val p1 = compilePath("/test/{a123dsdas}")
        val m1 = p1.matchEntire("/test/aaabbbccc")
        assertNotNull(m1)
        assertEquals("aaabbbccc", m1.groups["a123dsdas"]!!.value)
    }

    @Test
    fun testInvalidPath() {
        assertThrows<ServletException> { compilePath("/empty/{}") }
        assertThrows<ServletException> { compilePath("/start-with-digit/{123}") }
        assertThrows<ServletException> { compilePath("/invalid-name/{abc-def}") }
        assertThrows<ServletException> { compilePath("/missing-left/a}") }
        assertThrows<ServletException> { compilePath("/missing-right/a}") }
    }

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
    fun getApiHelloProduceText() {
        val req = createMockRequest("GET", "/api/hello/produce_text/Bob")
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("text/plain", resp.contentType)
        assertEquals("Bob", resp.contentAsString)
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
        assertEquals("text/plain", resp.contentType)
        assertEquals("test_402_error", resp.contentAsString)

        val req2 = createMockRequest("GET", "/api/error/400")
        val resp2 = createMockResponse()
        dispatcherServlet.service(req2, resp2)
        assertEquals(400, resp2.status)
        assertEquals("text/plain", resp2.contentType)
        assertEquals("", resp2.contentAsString)

        val req3 = createMockRequest("GET", "/api/error")
        val resp3 = createMockResponse()
        dispatcherServlet.service(req3, resp3)
        assertEquals(500, resp3.status)
        assertEquals("text/html", resp3.contentType)
        assertEquals(resp3.contentAsString, DEFAULT_ERROR_RESP_BODY[500])

        val req4 = createMockRequest("GET", "/api/error_not_found")
        val resp4 = createMockResponse()
        dispatcherServlet.service(req4, resp4)
        assertEquals(404, resp4.status)
        assertEquals("text/plain", resp4.contentType)
        assertEquals("test_404_error", resp4.contentAsString)
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
    fun testEchoStringBody() {
        val req = createMockRequest("POST", "/api/echo-string-body", "test")
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("text/plain", resp.contentType)
        assertEquals("test", resp.contentAsString)
    }

    @Test
    fun testEchoStringBody1() {
        val req = createMockRequest("POST", "/api/echo-string-body", null)
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        assertEquals(200, resp.status)
        assertEquals("text/plain", resp.contentType)
        assertEquals("", resp.contentAsString)
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

    @Test
    fun getErrorStatus() {
        for (status in 400..599) {
            val req = createMockRequest("GET", "/error/$status", null)
            val resp = createMockResponse()
            dispatcherServlet.service(req, resp)
            assertEquals(status, resp.status)
            assertEquals(resp.contentAsString, DEFAULT_ERROR_RESP_BODY.getOrDefault(status, "<h1>Error: Status $status</h1>"))
        }
    }

    @Test
    fun testEcho() {
        val req = createMockRequest("POST", "/echo", "test echo")
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        val respEntity = resp.contentAsString.readJson<Map<*, *>>()!!
        assertEquals(200, resp.status)
        assertEquals("application/json", resp.contentType)
        assertEquals(req.contentAsString, respEntity["body"])
        assertEquals("POST", respEntity["method"])
        assertTrue((respEntity["reqParams"] as Map<*, *>).isEmpty())
        assertTrue((respEntity["cookies"] as List<*>).isEmpty())
    }

    @Test
    fun testEcho1() {
        val req = createMockRequest("POST", "/echo", null)
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        val respEntity = resp.contentAsString.readJson<Map<*, *>>()!!
        assertEquals(200, resp.status)
        assertEquals("application/json", resp.contentType)
        assertEquals("", respEntity["body"])
        assertEquals("POST", respEntity["method"])
        assertTrue((respEntity["reqParams"] as Map<*, *>).isEmpty())
        assertTrue((respEntity["cookies"] as List<*>).isEmpty())
    }

    @Test
    fun testEcho2() {
        val req = createMockRequest("POST", "/echo", "")
        val resp = createMockResponse()
        dispatcherServlet.service(req, resp)
        val respEntity = resp.contentAsString.readJson<Map<*, *>>()!!
        assertEquals(200, resp.status)
        assertEquals("application/json", resp.contentType)
        assertEquals("", respEntity["body"])
        assertEquals("POST", respEntity["method"])
        assertTrue((respEntity["reqParams"] as Map<*, *>).isEmpty())
        assertTrue((respEntity["cookies"] as List<*>).isEmpty())
    }

    @BeforeEach
    fun init() {
        ctx = createMockServletContext()
        WebMvcConfiguration.servletContext = ctx
        val config = ConfigProperties.load()
        AnnotationApplicationContext(ControllerConfiguration::class.java, config)
        dispatcherServlet = DispatcherServlet()
        dispatcherServlet.init()
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
        method: String, path: String, body: Any? = null, params: Map<String, String>? = null,
    ): MockHttpServletRequest {
        val req = MockHttpServletRequest(ctx, method, path)
        req.characterEncoding = "UTF-8"
        if (method == "GET" && params != null) {
            params.forEach { (k, v) -> req.setParameter(k, v) }
        } else if (method == "POST") {
            if (body != null) {
                req.contentType = "application/json"
                req.setContent(if (body is String) body.toByteArray() else body.toJsonAsBytes())
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
