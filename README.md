<img src="https://raw.githubusercontent.com/NuclearMissile/Autumn/master/autumn.png" width="300"> 

# Autumn

_Yet another toy web application framework imitating Spring with homemade http server in Kotlin._

![](screenshot.png)

## Features

- [x] DI + AOP + MVC web framework
  - [ ] Resolve circular dependency in ctor injection 
- [x] Homemade Jakarta Servlet 6.0 http server
- [x] JdbcTemplate and naive ORM, support @Transactional annotation
- [x] Standard .war packaging
- [x] Demo webapp

## Demo

hello-autumn (user login demo):

_test account: test@test.com: test_

![](login-demo.png)

<details>

<summary>Code</summary>

```kotlin
@Controller
class IndexController(@Autowired private val userService: UserService) {
    companion object {
        const val USER_SESSION_KEY = "USER_SESSION_KEY"
    }

    @PostConstruct
    fun init() {
      // @Transactional proxy of UserService injected
      assert(userService.javaClass != UserService::class.java)
    }

    @Get("/")
    fun index(session: HttpSession): ModelAndView {
        val user = session.getAttribute(USER_SESSION_KEY)
        return if (user == null)
            ModelAndView("redirect:/register") else ModelAndView("/index.ftl", mapOf("user" to user))
    }

    @Get("/register")
    fun register(): ModelAndView {
        return ModelAndView("/register.ftl")
    }

    @Post("/register")
    fun register(
        @RequestParam email: String, @RequestParam name: String, @RequestParam password: String
    ): ModelAndView {
        return if (userService.register(email, name, password) != null)
            ModelAndView("redirect:/login")
        else
            ModelAndView("/register.ftl", mapOf("error" to "$email already registered"))
    }

    @Get("/login")
    fun login(): ModelAndView {
        return ModelAndView("/login.ftl")
    }

    @Post("/login")
    fun login(@RequestParam email: String, @RequestParam password: String, session: HttpSession): ModelAndView {
        val user = userService.login(email, password)
            ?: return ModelAndView("/login.ftl", mapOf("error" to "email or password is incorrect"))
        session.setAttribute(USER_SESSION_KEY, user)
        return ModelAndView("redirect:/")
    }

    @Get("/logoff")
    fun logoff(session: HttpSession): String {
        session.removeAttribute(USER_SESSION_KEY)
        return "redirect:/login"
    }
}

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    var id: Long,
    @Column(nullable = false, unique = true)
    var email: String,
    @Column(nullable = false)
    var name: String,
    @Column(name = "pwd_salt", nullable = false)
    val pwdSalt: String,
    @Column(name = "pwd_hash", nullable = false)
    val pwdHash: String,
)

@Component
@Transactional
class UserService(@Autowired val naiveOrm: NaiveOrm) {
    companion object {
        const val CREATE_USERS = "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "email TEXT NOT NULL UNIQUE, name TEXT NOT NULL, pwd_salt TEXT NOT NULL, pwd_hash TEXT NOT NULL);"
    }

    @PostConstruct
    fun init() {
        naiveOrm.jdbcTemplate.update(CREATE_USERS)
        register("test@test.com", "test", "test")
    }

    fun getUserByEmail(email: String): User? {
        return naiveOrm.selectFrom<User>().where("email = ?", email).first()
    }

    fun register(email: String, name: String, password: String): User? {
        val pwdSalt = SecureRandomUtil.genRandomString(32)
        val pwdHash = HashUtil.hmacSha256(password, pwdSalt)
        val user = User(-1, email, name, pwdSalt, pwdHash)
        return try {
            naiveOrm.insert(user)
            user
        } catch (e: Exception) {
            null
        }
    }

    fun login(email: String, password: String): User? {
        val user = getUserByEmail(email) ?: return null
        val pwdHash = HashUtil.hmacSha256(password, user.pwdSalt)
        return if (pwdHash == user.pwdHash) user else null
    }
}

```
</details>

## Ref:

- https://github.com/michaelliao/summer-framework
- https://github.com/zzzzbw/doodle
- https://github.com/fuzhengwei/small-spring





