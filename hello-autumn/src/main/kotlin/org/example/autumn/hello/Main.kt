package org.example.autumn.hello

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.annotation.WebListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.example.autumn.annotation.*
import org.example.autumn.exception.ResponseErrorException
import org.example.autumn.resolver.Config
import org.example.autumn.server.AutumnServer
import org.example.autumn.servlet.ContextLoadListener
import org.example.autumn.servlet.FilterRegistrationBean
import org.example.autumn.servlet.ModelAndView
import org.example.autumn.servlet.WebMvcConfiguration
import org.example.autumn.utils.JsonUtils.toJson
import org.example.autumn.utils.JsonUtils.writeJson
import org.slf4j.LoggerFactory

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        AutumnServer.start(
            "src/main/webapp", Config.load(), javaClass.classLoader, listOf(HelloContextLoadListener::class.java)
        )
    }
}

@ComponentScan
@Configuration
@Import(WebMvcConfiguration::class)
class HelloConfiguration

@WebListener
class HelloContextLoadListener : ContextLoadListener()

@Order(100)
@Component
class LogFilterRegistrationBean : FilterRegistrationBean() {
    override val urlPatterns: List<String>
        get() = listOf("/*")
    override val filter: Filter
        get() = LogFilter()

    class LogFilter : Filter {
        private val logger = LoggerFactory.getLogger(javaClass)
        override fun doFilter(req: ServletRequest, resp: ServletResponse, chain: FilterChain) {
            val httpReq = req as HttpServletRequest
            logger.info("{}: {}", httpReq.method, httpReq.requestURI)
            chain.doFilter(req, resp)
        }
    }
}

@Order(200)
@Component
class ApiErrorFilterReg : FilterRegistrationBean() {
    override val urlPatterns: List<String>
        get() = listOf("/api/*")
    override val filter: Filter
        get() = ApiErrorFilter()

    class ApiErrorFilter : Filter {
        private val logger = LoggerFactory.getLogger(javaClass)
        override fun doFilter(req: ServletRequest, resp: ServletResponse, chain: FilterChain) {
            try {
                chain.doFilter(req, resp)
            } catch (e: Throwable) {
                req as HttpServletRequest
                resp as HttpServletResponse
                logger.warn("api error when handling {}: {}", req.method, req.requestURI)
                if (!resp.isCommitted) {
                    resp.apply {
                        reset()
                        status = 400
                        writer.writeJson(mapOf("message" to e.message, "type" to (e.cause ?: e).javaClass.simpleName))
                            .flush()
                    }
                }
            }
        }
    }
}

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
        throw ResponseErrorException(errorCode, "test", errorResp)
    }
}

@Controller("/user")
class UserController {
    @Get("/")
    fun user(req: HttpServletRequest): ModelAndView {
        val userName = req.session.getAttribute("username")
        return ModelAndView("/user.ftl", mapOf("username" to userName))
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

@RestController("/api")
class RestApiController {
    @Get("/hello/{name}")
    @ResponseBody
    fun hello(@PathVariable("name") name: String): String {
        return mapOf("name" to name).toJson()
    }

    @Get("/params")
    fun params(@RequestParam("test") test: String): Map<String, String> {
        return mapOf("test" to test)
    }

    @Get("/error")
    fun error() {
        throw Exception("api test error")
    }

    @Get("/error/{errorCode}/{errorResp}")
    fun error(@PathVariable errorCode: Int, @PathVariable errorResp: String) {
        throw ResponseErrorException(errorCode, "test", errorResp)
    }
}