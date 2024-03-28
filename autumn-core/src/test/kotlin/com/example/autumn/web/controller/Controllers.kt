package com.example.autumn.web.controller

import com.example.autumn.annotation.*
import com.example.autumn.utils.JsonUtils.toJson
import com.example.autumn.web.ModelAndView
import com.example.autumn.web.WebMvcConfiguration
import org.slf4j.LoggerFactory


@Configuration
@Import(WebMvcConfiguration::class)
class ControllerConfiguration

@RestController
class RestApiController {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Get("/api/hello/{name}")
    @ResponseBody
    fun hello(@PathVariable("name") name: String): String {
        return mapOf("name" to name).toJson()
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

    @Get("/product/{id}")
    fun product(@PathVariable("id") id: Long, @RequestParam("name") name: String?): ModelAndView {
        return ModelAndView(
            "/product.html",
            mutableMapOf("name" to name, "product" to mutableMapOf("id" to id, "name" to "Autumn Software"))
        )
    }
}

