<img src="https://raw.githubusercontent.com/NuclearMissile/Autumn/master/autumn.png" width="300"> 

# Autumn

_Yet another toy web application framework imitating Spring with homemade http server in Kotlin._

![](screenshot.png)

## Features

- [x] DI + AOP + EventBus + MVC web framework
- [x] Homemade Jakarta Servlet 6.0 http server
- [x] JdbcTemplate and naive ORM, support @Transactional annotation
- [x] Standard .war packaging
- [x] Demo webapp

## Demo

hello-autumn (user login demo), use Autumn just like Spring

![](login-demo.png)

### Let's have a try

```shell
# build
ls                     # current path: xxx/autumn
cd ./autumn-build/     # cd to xxx/autumn/autumn-build/
mvn clean              # clean previous build
mvn install            # build and install builds to maven local cache
cd ../hello-autumn/    # cd to xxx/autumn/hello-autumn/ 
mvn war:war            # build .war package for hello-autumn project

# start web application with homemade http server
ls                     # current path: xxx/autumn
cp ./hello-autumn/target/hello-autumn-1.0.0.war ./autumn-core/target/hello-autumn-1.0.0.war
cd ./autumn-core/target/
# execute hello-autumn-1.0.0.war with homemade http server in autumn-core
java -jar autumn-core-1.0.0.jar -w hello-autumn-1.0.0.war

# or start web application with Tomcat and docker
ls                     # current path: xxx/autumn
cd ./hello-autumn/
docker build -t hello-autumn . 
docker run -p 8080:8080 -t hello-autumn

# then access localhost:8080 to play with the demo

```

<details>

<summary>Code</summary>

```kotlin
// Main.kt
@Controller
class IndexController(
    @Autowired private val userService: UserService,
    @Autowired private val eventBus: EventBus,
) {
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
        @RequestParam email: String, @RequestParam name: String, @RequestParam password: String
    ): ModelAndView {
        return if (userService.register(email, name, password) != null)
            ModelAndView("redirect:/login")
        else
            ModelAndView("/register.ftl", mapOf("error" to "$email already registered"))
    }

    @Get("/login")
    fun login(session: HttpSession): ModelAndView {
        val user = session.getAttribute(USER_SESSION_KEY)
        return if (user == null)
            ModelAndView("/login.ftl") else ModelAndView("redirect:/")
    }

    @Post("/login")
    fun login(@RequestParam email: String, @RequestParam password: String, session: HttpSession): ModelAndView {
        val user = userService.login(email, password)
            ?: return ModelAndView("/login.ftl", mapOf("error" to "email or password is incorrect"))
        session.setAttribute(USER_SESSION_KEY, user)
        eventBus.post(LoginEvent(user))
        return ModelAndView("redirect:/")
    }

    @Get("/logoff")
    fun logoff(session: HttpSession): String {
        session.removeAttribute(USER_SESSION_KEY)
        return "redirect:/login"
    }
}

// UserService.kt
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

## Ref

- https://github.com/michaelliao/summer-framework
- https://github.com/zzzzbw/doodle
- https://github.com/fuzhengwei/small-spring





