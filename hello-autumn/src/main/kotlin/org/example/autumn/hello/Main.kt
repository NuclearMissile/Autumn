package org.example.autumn.hello

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.annotation.WebListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.example.autumn.DEFAULT_ERROR_RESP_BODY
import org.example.autumn.annotation.*
import org.example.autumn.aop.AroundConfiguration
import org.example.autumn.aop.Invocation
import org.example.autumn.aop.InvocationChain
import org.example.autumn.db.DbConfiguration
import org.example.autumn.eventbus.Event
import org.example.autumn.eventbus.EventBus
import org.example.autumn.eventbus.EventBusConfiguration
import org.example.autumn.eventbus.EventMode
import org.example.autumn.exception.RequestErrorException
import org.example.autumn.exception.ServerErrorException
import org.example.autumn.hello.model.User
import org.example.autumn.hello.service.UserService
import org.example.autumn.server.AutumnServer
import org.example.autumn.servlet.*
import org.example.autumn.utils.JsonUtils.toJson
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        AutumnServer.start(listOf(HelloConfig::class.java))
    }
}

@WebListener
@Import(WebMvcConfiguration::class, DbConfiguration::class, AroundConfiguration::class, EventBusConfiguration::class)
class HelloConfig : ContextLoadListener()

class HelloException(val statusCode: Int, message: String, val responseBody: String? = null) : Exception(message)

class HelloException2(val statusCode: Int, message: String, val responseBody: String? = null) : Exception(message)

@Component
class HelloExceptionMapper : ExceptionMapper<HelloException>() {
    private val logger = LoggerFactory.getLogger(javaClass)
    override fun map(e: HelloException, req: HttpServletRequest, resp: HttpServletResponse) {
        val url = req.requestURI.removePrefix(req.contextPath)
        logger.warn("process request failed for $url, message: ${e.message}, status: ${e.statusCode}", e)
        resp.set(ResponseEntity(e.responseBody, e.statusCode, "text/plain"))
    }
}

@Component
class BeforeLogInvocation : Invocation {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun before(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?) {
        logger.info("[Before] ${method.declaringClass.toString().removePrefix("class ")}.${method.name}")
    }
}

@Component
class AfterLogInvocation : Invocation {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun after(
        caller: Any, returnValue: Any?, method: Method, chain: InvocationChain, args: Array<Any?>?,
    ): Any? {
        logger.info("[After] ${method.declaringClass.toString().removePrefix("class ")}.${method.name}")
        return returnValue
    }
}

@Order(100)
@Component
class LogFilter : FilterRegistration() {
    override val name = "logFilter"
    override val urlPatterns = listOf("/*")
    override val filter = object : Filter {
        private val logger = LoggerFactory.getLogger(javaClass)
        override fun doFilter(req: ServletRequest, resp: ServletResponse, chain: FilterChain) {
            val startTime = System.currentTimeMillis()
            val httpReq = req as HttpServletRequest
            val httpResp = resp as HttpServletResponse
            chain.doFilter(req, resp)
            logger.info(
                "{}: {} [{}, ${System.currentTimeMillis() - startTime}ms]",
                httpReq.method, httpReq.requestURI, httpResp.status
            )
        }
    }
}

@Component
class LoginEventListener {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Subscribe(EventMode.SYNC)
    fun onLogin(e: LoginEvent) {
        logger.info("{Login} ${e.user}")
    }

    @Subscribe(EventMode.ASYNC)
    fun onLogoff(e: LogoffEvent) {
        logger.info("{Logoff} ${e.user}")
    }
}

data class LoginEvent(val user: User) : Event
data class LogoffEvent(val user: User) : Event

@Controller
class IndexController @Autowired constructor(private val userService: UserService) {
    companion object {
        const val USER_SESSION_KEY = "USER_SESSION_KEY"
    }

    @Autowired
    private lateinit var eventBus: EventBus

    @PostConstruct
    fun init() {
        // @Transactional proxy of UserService injected
        assert(userService.javaClass != UserService::class.java)
    }

    @Get("/")
    fun index(session: HttpSession): ModelAndView {
        val user = session.getAttribute(USER_SESSION_KEY)
        return if (user == null)
            ModelAndView("redirect:/login") else ModelAndView("/index.ftl", mapOf("user" to user))
    }

    @Get("/register")
    fun register(session: HttpSession): ModelAndView {
        val user = session.getAttribute(USER_SESSION_KEY)
        return if (user == null)
            ModelAndView("/register.ftl") else ModelAndView("redirect:/")
    }

    @Post("/register")
    fun register(
        @RequestParam email: String, @RequestParam name: String, @RequestParam password: String,
    ): ModelAndView {
        if (name.isBlank()) return ModelAndView("/register.ftl", mapOf("error" to "name is blank"))
        return if (userService.register(email, name, password) != null)
            ModelAndView("redirect:/login")
        else
            ModelAndView("/register.ftl", mapOf("error" to "email is already registered"))
    }

    @Get("/login")
    fun login(session: HttpSession): ModelAndView {
        val user = session.getAttribute(USER_SESSION_KEY)
        return if (user == null)
            ModelAndView("/login.ftl") else ModelAndView("redirect:/")
    }

    @Post("/login")
    fun login(@RequestParam email: String, @RequestParam password: String, session: HttpSession): ModelAndView {
        val user = userService.validate(email, password)
            ?: return ModelAndView("/login.ftl", mapOf("error" to "email or password is incorrect"))
        session.setAttribute(USER_SESSION_KEY, user)
        eventBus.post(LoginEvent(user))
        return ModelAndView("redirect:/")
    }

    @Get("/logoff")
    fun logoff(session: HttpSession): String {
        val user = session.getAttribute(USER_SESSION_KEY) as User?
            ?: throw RequestErrorException("cannot logoff due to not logged in")
        session.removeAttribute(USER_SESSION_KEY)
        eventBus.post(LogoffEvent(user))
        return "redirect:/login"
    }

    @Get("/changePassword")
    fun changePassword(session: HttpSession): ModelAndView {
        session.getAttribute(USER_SESSION_KEY)
            ?: throw RequestErrorException("cannot change password due to not logged in")
        return ModelAndView("/changePassword.ftl")
    }

    @Post("/changePassword")
    fun changePassword(
        @RequestParam("old_password") oldPassword: String,
        @RequestParam("new_password") newPassword: String,
        @RequestParam("new_password_repeat") newPasswordRepeat: String,
        session: HttpSession,
    ): ModelAndView {
        val user = session.getAttribute(USER_SESSION_KEY) as User?
            ?: throw RequestErrorException("cannot change password due to not logged in")
        if (newPassword != newPasswordRepeat)
            return ModelAndView("/changePassword.ftl", mapOf("error" to "passwords are different"))
        if (newPassword == oldPassword)
            return ModelAndView("/changePassword.ftl", mapOf("error" to "new password must be different from old one"))
        userService.validate(user.email, oldPassword)
            ?: return ModelAndView("/changePassword.ftl", mapOf("error" to "old password is incorrect"))
        if (userService.changePassword(user, newPassword)) {
            session.removeAttribute(USER_SESSION_KEY)
            return ModelAndView("redirect:/login")
        } else {
            throw ServerErrorException("change password failed due to internal error")
        }
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
        return ModelAndView(null, mapOf(), 400)
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
        return ResponseEntity(
            DEFAULT_ERROR_RESP_BODY.getOrDefault(status, "<h1>Error: Status $status</h1>"), status, "text/html"
        )
    }

    @Get("/error/{errorCode}/{errorResp}")
    fun error(@PathVariable errorCode: Int, @PathVariable errorResp: String) {
        throw HelloException(
            errorCode, "test", mapOf("errorCode" to errorCode, "errorResp" to errorResp).toJson()
        )
    }
}