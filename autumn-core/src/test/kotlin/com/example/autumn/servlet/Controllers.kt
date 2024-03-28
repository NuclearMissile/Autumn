package com.example.autumn.servlet

import com.example.autumn.annotation.*
import com.example.autumn.utils.JsonUtils.toJson
import com.example.autumn.utils.JsonUtils.writeJson
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

@Configuration
@Import(WebMvcConfiguration::class)
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
    var password: String? = null
)

@RestController
class RestApiController {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Get("/api/hello/{name}")
    @ResponseBody
    fun hello(@PathVariable("name") name: String): String {
        return mapOf("name" to name).toJson()
    }

    @Get("/api/greeting")
    fun greeting(
        @RequestParam(value = "action", defaultValue = "Hello") action: String,
        @RequestParam("name") name: String
    ): Map<String, Any> {
        return mapOf("action" to mapOf("name" to name))
    }

    @Get("/api/download/{file}")
    fun download(
        @PathVariable("file") file: String, @RequestParam("time") downloadTime: Float, @RequestParam("md5") md5: String,
        @RequestParam("length") length: Int, @RequestParam("hasChecksum") checksum: Boolean
    ): FileObj {
        return FileObj(file, length, downloadTime, md5, "A".repeat(length).toByteArray(StandardCharsets.UTF_8))
    }

    @Get("/api/download-part")
    fun downloadPart(
        @RequestParam("file") file: String,
        @RequestParam("time") downloadTime: Float,
        @RequestParam("md5") md5: String,
        @RequestParam("length") length: Int,
        @RequestParam("hasChecksum") checksum: Boolean,
        resp: HttpServletResponse
    ) {
        val f = FileObj(file, length, downloadTime, md5, "A".repeat(length).toByteArray(StandardCharsets.UTF_8))
        resp.contentType = "application/json"
        resp.writer.writeJson(f).flush()
    }

    @Post("/api/register")
    fun register(@RequestBody signin: SigninObj, resp: HttpServletResponse) {
        resp.contentType = "application/json"
        val pw = resp.writer
        pw.write("[\"${signin.name}\",true,12345]")
        pw.flush()
    }
}

@Controller
class MvcController {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Get("/hello/{name}")
    @ResponseBody
    fun hello(@PathVariable("name") name: String): String {
        return "Hello, $name"
    }

    @Get("/greeting")
    @ResponseBody
    fun greeting(
        @RequestParam(value = "action", defaultValue = "Hello") action: String,
        @RequestParam("name") name: String
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
        @RequestParam("hasChecksum") checksum: Boolean
    ): ByteArray {
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
        session.setAttribute("signout", java.lang.Boolean.TRUE)
        resp.sendRedirect("/signin?name=$name")
        return null
    }
}

