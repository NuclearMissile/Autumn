package org.example.autumn.servlet

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.example.autumn.DEFAULT_ERROR_MSG
import org.example.autumn.annotation.*
import org.example.autumn.exception.NotFoundException
import org.example.autumn.exception.ResponseErrorException
import org.example.autumn.utils.JsonUtils.toJson
import org.example.autumn.utils.JsonUtils.writeJson
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

@Configuration
class ControllerConfiguration

class FileObj(
    var file: String? = null,
    var length: Int = 0,
    var downloadTime: Float? = null,
    var md5: String? = null,
    var content: ByteArray? = null,
)

class SigninObj(
    var name: String? = null,
    var password: String? = null,
)

@RestController("/api")
class RestApiController {
    @ExceptionHandler(ResponseErrorException::class, "text/plain")
    fun reeExceptionHandler(e: ResponseErrorException, resp: HttpServletResponse): ResponseEntity {
        return ResponseEntity(e.responseBody ?: "", e.statusCode, "")
    }

    @Get("/error/{errorCode}/{errorResp}")
    fun error(@PathVariable errorCode: Int, @PathVariable errorResp: String) {
        throw ResponseErrorException(errorCode, "test", errorResp)
    }

    @Get("/error/{errorCode}")
    fun error(@PathVariable errorCode: Int) {
        throw ResponseErrorException(errorCode, "test")
    }

    @Get("/error_not_found")
    fun notFound() {
        throw NotFoundException("test", "test_404_error")
    }

    @Get("/error")
    fun error() {
        throw Exception("test")
    }

    @Get("/hello/{name}", produce = "application/json")
    @ResponseBody
    fun hello(@PathVariable("name") name: String): String {
        return mapOf("name" to name).toJson()
    }

    @Get("/hello/produce_text/{name}", "text/plain")
    @ResponseBody
    fun helloProduce(@PathVariable("name") name: String): String {
        return name
    }

    @Get("/greeting", produce = "application/json")
    fun greeting(
        @RequestParam(value = "action", defaultValue = "Hello") action: String,
        @RequestParam("name") name: String,
    ): Map<String, Any> {
        return mapOf("action" to mapOf("name" to name))
    }

    @Get("/download/{file}", produce = "application/json")
    fun download(
        @PathVariable("file") file: String, @RequestParam("time") downloadTime: Float, @RequestParam("md5") md5: String,
        @RequestParam("length") length: Int, @RequestParam("hasChecksum") checksum: Boolean,
    ): FileObj {
        return FileObj(file, length, downloadTime, md5, "A".repeat(length).toByteArray(StandardCharsets.UTF_8))
    }

    @Get("/download-part")
    fun downloadPart(
        @RequestParam("file") file: String,
        @RequestParam("time") downloadTime: Float,
        @RequestParam("md5") md5: String,
        @RequestParam("length") length: Int,
        @RequestParam("hasChecksum") checksum: Boolean,
        resp: HttpServletResponse,
    ) {
        val f = FileObj(file, length, downloadTime, md5, "A".repeat(length).toByteArray(StandardCharsets.UTF_8))
        resp.contentType = "application/json"
        resp.writer.writeJson(f).flush()
    }

    @Post("/register")
    fun register(@RequestBody signin: SigninObj, resp: HttpServletResponse) {
        resp.contentType = "application/json"
        val pw = resp.writer
        pw.write("[\"${signin!!.name}\",true,12345]")
        pw.flush()
    }

    @Post("/echo-string-body")
    fun echoStringBody(@RequestBody body: String, resp: HttpServletResponse) {
        resp.contentType = "text/plain"
        val pw = resp.writer
        pw.write(body)
        pw.flush()
    }
}

@Controller
class MvcController {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Get("/hello/{name}")
    @ResponseBody
    fun hello(@PathVariable name: String, resp: HttpServletResponse): String {
        resp.contentType = "text/html"
        return "Hello, $name"
    }

    @Get("/greeting")
    @ResponseBody
    fun greeting(
        @RequestParam(value = "action", defaultValue = "Hello") action: String,
        @RequestParam("name") name: String,
    ): String {
        return "$action, $name"
    }

    @Get("/download/{file}")
    @ResponseBody
    fun download(
        @PathVariable("file") file: String,
        @RequestParam("time") downloadTime: Float,
        @RequestParam("md5") md5: String,
        @RequestParam("length") length: Int,
        @RequestParam("hasChecksum") checksum: Boolean,
    ): ByteArray {
        return "A".repeat(length).toByteArray(StandardCharsets.UTF_8)
    }

    @Get("/download2/{file}")
    @ResponseBody
    fun download2(
        @PathVariable("file") file: String,
        @RequestParam(defaultValue = "10") downloadTime: Float,
        @RequestParam("md5") md5: String,
        @RequestParam("length") length: Int,
        @RequestParam(required = false) checksum: Boolean?,
        @Header header1: String?,
        @Header(defaultValue = "test_header2") header2: String,
        @Header(required = false) header3: String?,
    ): ByteArray {
        assert(checksum == null)
        assert(downloadTime == 10f)
        assert(header1 == "test_header1")
        assert(header2 == "test_header2")
        assert(header3 == null)
        return "A".repeat(length).toByteArray(StandardCharsets.UTF_8)
    }


    @Get("/download-part")
    fun downloadPart(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.status = 206
        resp.setHeader("Range", "bytes=100-108")
        val output = resp.outputStream
        output.write("A".repeat(8).toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    @Get("/login")
    fun login(@RequestParam(value = "next", defaultValue = "/signin") next: String): String {
        return "redirect:$next"
    }

    @Get("/product/{id}")
    fun product(@PathVariable("id") id: Long, @RequestParam("name") name: String): ModelAndView {
        return ModelAndView(
            "/product.html", mapOf("name" to name, "product" to mapOf("id" to id, "name" to "Autumn Software"))
        )
    }

    @Post("/signin")
    fun signin(@RequestParam("name") name: String, @RequestParam("password") password: String): ModelAndView {
        return ModelAndView("redirect:/home?name=$name")
    }

    @Post("/register")
    fun register(@RequestParam("name") name: String, @RequestParam("password") password: String): ModelAndView {
        return ModelAndView("/register.html", mapOf("name" to name))
    }

    @Post("/signout")
    fun signout(session: HttpSession, req: HttpServletRequest, resp: HttpServletResponse): ModelAndView? {
        val name = req.getParameter("name")
        session.setAttribute("signout", true)
        resp.sendRedirect("/signin?name=$name")
        return null
    }

    @Get("/error/{status}")
    fun error(@PathVariable status: Int): ResponseEntity {
        return ResponseEntity(
            DEFAULT_ERROR_MSG.getOrDefault(status, "<h1>Error: Status $status</h1>"), status, "text/html"
        )
    }

    @Post("/echo")
    fun echo(req: RequestEntity): ResponseEntity {
        return ResponseEntity(req, 200, "application/json")
    }
}

