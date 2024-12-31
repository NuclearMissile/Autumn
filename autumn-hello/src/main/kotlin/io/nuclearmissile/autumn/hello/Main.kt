package io.nuclearmissile.autumn.hello

import io.nuclearmissile.autumn.annotation.*
import io.nuclearmissile.autumn.aop.AroundConfiguration
import io.nuclearmissile.autumn.db.DbConfiguration
import io.nuclearmissile.autumn.eventbus.Event
import io.nuclearmissile.autumn.eventbus.EventBus
import io.nuclearmissile.autumn.eventbus.EventBusConfiguration
import io.nuclearmissile.autumn.eventbus.EventMode
import io.nuclearmissile.autumn.exception.RequestErrorException
import io.nuclearmissile.autumn.exception.ServerErrorException
import io.nuclearmissile.autumn.server.AutumnServer
import io.nuclearmissile.autumn.servlet.ContextLoadListener
import io.nuclearmissile.autumn.servlet.FilterRegistration
import io.nuclearmissile.autumn.servlet.ModelAndView
import io.nuclearmissile.autumn.servlet.WebMvcConfiguration
import io.nuclearmissile.autumn.hello.model.User
import io.nuclearmissile.autumn.hello.service.UserService
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.annotation.WebListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        AutumnServer.start(listOf(HelloConfig::class.java))
    }
}

@WebListener
@Import(WebMvcConfiguration::class, DbConfiguration::class, AroundConfiguration::class, EventBusConfiguration::class)
class HelloConfig : ContextLoadListener()

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
        logger.info("Login: ${e.user}")
    }

    @Subscribe(EventMode.ASYNC)
    fun onLogoff(e: LogoffEvent) {
        logger.info("Logoff: ${e.user}")
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
