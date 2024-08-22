package org.example.autumn.db.orm

import jakarta.persistence.*
import org.example.autumn.annotation.Around
import org.example.autumn.annotation.Autowired
import org.example.autumn.annotation.Component
import org.example.autumn.annotation.Transactional
import org.example.autumn.aop.Invocation
import org.example.autumn.aop.InvocationChain
import org.example.autumn.context.AnnotationApplicationContext
import org.example.autumn.db.JdbcTemplate
import org.example.autumn.exception.DataAccessException
import org.example.autumn.utils.ConfigProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame

class OrmTxTest {
    companion object {
        const val CREATE_USERS =
            "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT NOT NULL UNIQUE, name TEXT NOT NULL, password TEXT NOT NULL);"
    }

    private val config = ConfigProperties.load()

    @BeforeEach
    fun setUp() {
        Files.deleteIfExists(Path("test_jdbc.db"))
        AnnotationApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            ctx.getBean<JdbcTemplate>("jdbcTemplate").update(CREATE_USERS)
        }
    }

    @Test
    fun testOrmWithTx() {
        AnnotationApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val userService = ctx.getBean<UserService>("userService")
            // proxied:
            assertNotSame(UserService::class.java, userService.javaClass)

            // with Tx
            val user_0 = userService.insertUser("test_0", "test_0", "test_0")
            assertNotEquals(-1, user_0.id)
            val user_1 = User(-1, "test_1", "test_1", "test_1")
            val user_2 = User(-1, "test_2", "test_2", "test_2")
            assertThrows<DataAccessException> { userService.insertUsers(listOf(user_1, user_2, user_0)) }
            assertEquals(1, userService.getAllUser().size)

            // without Tx
            assertThrows<DataAccessException> { userService.insertUsersNoTx(listOf(user_1, user_2, user_0)) }
            assertEquals(3, userService.getAllUser().size)
        }
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
    @Column(nullable = false)
    val password: String,
)

@Component
class AroundLogInvocation : Invocation {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun before(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?) {
        logger.info("[Before] ${method.declaringClass.toString().removePrefix("class ")}.${method.name}")
    }

    override fun after(
        caller: Any, returnValue: Any?, method: Method, chain: InvocationChain, args: Array<Any?>?,
    ): Any? {
        logger.info("[After] ${method.declaringClass.toString().removePrefix("class ")}.${method.name}")
        return returnValue
    }

    override fun error(caller: Any, method: Method, chain: InvocationChain, e: Throwable, args: Array<Any?>?) {
        logger.info("[Error] {}", e.toString())
        throw e
    }

    override fun finally(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?) {
        logger.info("[Finally] ${method.declaringClass.toString().removePrefix("class ")}.${method.name}")
    }
}

@Component
@Around("aroundLogInvocation")
@Transactional
class UserService(@Autowired val naiveOrm: NaiveOrm) {
    fun getAllUser(): List<User> {
        return naiveOrm.selectFrom<User>().query()
    }

    @Transactional
    fun insertUser(email: String, name: String, password: String): User {
        val user = User(-1, email, name, password)
        naiveOrm.insert(user)
        return user
    }

    @Transactional
    fun insertUsers(users: List<User>) {
        users.forEach { user -> naiveOrm.insert(user) }
    }

    fun insertUsersNoTx(users: List<User>) {
        users.forEach { user -> naiveOrm.insert(user) }
    }
}
