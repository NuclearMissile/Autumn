package org.example.autumn.hello

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.example.autumn.annotation.*
import org.example.autumn.servlet.*
import org.example.autumn.utils.HttpUtils.getDefaultErrorResponse
import org.example.autumn.utils.JsonUtils.toJson
import org.slf4j.LoggerFactory

class HelloException(val statusCode: Int, message: String, val responseBody: String? = null) : Exception(message)

class HelloException2(val statusCode: Int, message: String) : Exception(message)

@Component
class HelloExceptionMapper : ExceptionMapper<HelloException>() {
    private val logger = LoggerFactory.getLogger(javaClass)
    override fun map(e: HelloException, req: HttpServletRequest, resp: HttpServletResponse) {
        logger.info("exception is handled by exception mapper.")
        logger.warn("process request failed for ${req.requestURI}, message: ${e.message}, status: ${e.statusCode}", e)
        resp.set(ResponseEntity(e.responseBody, e.statusCode, "text/plain"))
    }
}

@Controller("/hello")
class HelloController {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Get("/")
    fun hello(): ModelAndView {
        return ModelAndView("/hello.html")
    }

    @Get("/error")
    fun error(): ModelAndView {
        throw Exception("MVC test error")
    }

    @Get("/error/{errorCode}/{errorResp}")
    fun error(@PathVariable errorCode: Int, @PathVariable errorResp: String) {
        throw HelloException(errorCode, "test", errorResp)
    }

    @Get("/error/{errorCode}")
    fun error(@PathVariable errorCode: Int) {
        throw HelloException2(errorCode, "test")
    }

    @Get("/echo")
    fun echo(req: RequestEntity): ResponseEntity {
        return ResponseEntity(req, 200, "application/json")
    }

    @ResponseBody
    @ExceptionHandler(HelloException2::class, "text/html")
    fun exceptionHandlerTest(e: HelloException2): ResponseEntity {
        logger.info("exception is handled by exceptionHandler", e)
        return ResponseEntity(e.javaClass.name, e.statusCode)
    }
}

@RestController("/api")
class RestApiController {
    @Get("/hello/{name}")
    @ResponseBody
    fun hello(@PathVariable name: String): String {
        return mapOf("name" to name).toJson()
    }

    @Get("/params")
    fun params(@RequestParam test: String): Map<String, String> {
        return mapOf("test" to test)
    }

    @Get("/error")
    fun error() {
        throw Exception("api test error")
    }

    @Get("/error/{status}")
    fun error(@PathVariable status: Int): ResponseEntity {
        return ResponseEntity(getDefaultErrorResponse(status), status, "text/html")
    }

    @Get("/error/{errorCode}/{errorResp}")
    fun error(@PathVariable errorCode: Int, @PathVariable errorResp: String) {
        throw HelloException(
            errorCode, "test", mapOf("errorCode" to errorCode, "errorResp" to errorResp).toJson()
        )
    }
}