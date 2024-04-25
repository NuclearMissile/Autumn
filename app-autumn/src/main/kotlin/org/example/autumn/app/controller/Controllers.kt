package org.example.autumn.app.controller

import jakarta.servlet.http.HttpServletRequest
import org.example.autumn.annotation.*
import org.example.autumn.exception.ResponseErrorException
import org.example.autumn.servlet.ModelAndView
import org.example.autumn.utils.JsonUtils.toJson

val USERS = mapOf("test" to "test")

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
        throw ResponseErrorException(errorCode, "test", errorResp, Error("test"))
    }
}

@Controller("/user")
class UserController {
    @Get("/")
    fun user(req: HttpServletRequest): ModelAndView {
        val userName = req.session.getAttribute("username")
        return if (userName == null) ModelAndView("/user_login.html") else
            ModelAndView("/user_welcome.html", mapOf("username" to userName))
    }

    @Get("/logout")
    fun logout(req: HttpServletRequest): String {
        req.session.invalidate()
        return "redirect:/user"
    }

    @Post("/login")
    fun login(req: HttpServletRequest): ModelAndView {
        val username = req.getParameter("username")
        val password = req.getParameter("password")
        if (username == null || password == null || USERS[username] != password) {
            return ModelAndView("/login_failed.html")
        }
        req.session.setAttribute("username", username)
        return ModelAndView("redirect:/user")
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
        throw ResponseErrorException(errorCode, "test", errorResp, null)
    }
}