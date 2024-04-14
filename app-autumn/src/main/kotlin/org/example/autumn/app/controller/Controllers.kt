package org.example.autumn.app.controller

import org.example.autumn.annotation.*
import org.example.autumn.exception.AbnormalResponseException
import org.example.autumn.servlet.ModelAndView
import org.example.autumn.utils.JsonUtils.toJson

@Controller
class MvcController {
    @Get("/")
    fun index(@Header("Cookie") a: String?): ModelAndView {
        return ModelAndView("/index.html")
    }

    @Get("/hello")
    fun hello(): ModelAndView {
        return ModelAndView("/hello.html")
    }

    @Get("/hello/error")
    fun error(): ModelAndView {
        return ModelAndView("/index.html", mapOf(), 400)
    }

    @Get("/hello/error/{errorCode}/{errorResp}")
    fun error(@PathVariable errorCode: Int, @PathVariable errorResp: String) {
        throw AbnormalResponseException(errorCode, "test", errorResp, Error("test"))
    }
}

@RestController
class RestApiController {
    @Get("/api/hello/{name}")
    @ResponseBody
    fun hello(@PathVariable("name") name: String): String {
        return mapOf("name" to name).toJson()
    }

    @Get("/api/params")
    fun params(@RequestParam("test") test: String): Map<String, String> {
        return mapOf("test" to test)
    }

    @Get("/api/error/{errorCode}/{errorResp}")
    fun error(@PathVariable errorCode: Int, @PathVariable errorResp: String) {
        throw AbnormalResponseException(errorCode, "test", errorResp)
    }
}