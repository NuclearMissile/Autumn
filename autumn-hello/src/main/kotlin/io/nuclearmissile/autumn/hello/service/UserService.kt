package io.nuclearmissile.autumn.hello.service

import io.nuclearmissile.autumn.annotation.*
import io.nuclearmissile.autumn.aop.Invocation
import io.nuclearmissile.autumn.aop.InvocationChain
import io.nuclearmissile.autumn.db.orm.NaiveOrm
import io.nuclearmissile.autumn.hello.model.User
import io.nuclearmissile.autumn.utils.HashUtils
import io.nuclearmissile.autumn.utils.SecureRandomUtils
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

@Component
class BeforeInvocation : Invocation {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun before(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?) {
        logger.info("[Before] ${method.declaringClass.toString().removePrefix("class ")}.${method.name}")
    }
}

@Component
class AfterInvocation : Invocation {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun after(
        caller: Any, returnValue: Any?, method: Method, chain: InvocationChain, args: Array<Any?>?,
    ): Any? {
        logger.info("[After] ${method.declaringClass.toString().removePrefix("class ")}.${method.name}")
        return returnValue
    }
}

interface IUserService {
    fun getUserByEmail(email: String): User?
    fun register(email: String, name: String, password: String): User?
    fun changePassword(user: User, newPassword: String): Boolean
    fun validate(email: String, password: String): User?
}

@Around("beforeInvocation", "afterInvocation")
@Component
@Transactional
class UserService @Autowired constructor(private val naiveOrm: NaiveOrm) : IUserService {
    companion object {
        const val CREATE_USERS = "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "email TEXT NOT NULL UNIQUE, name TEXT NOT NULL, pwd_salt TEXT NOT NULL, pwd_hash TEXT NOT NULL);"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        naiveOrm.jdbcTemplate.update(CREATE_USERS)
        val pwdSalt = SecureRandomUtils.genRandomString(32)
        val pwdHash = HashUtils.hmacSha256("test", pwdSalt)
        val user = User(-1, "test@test.com", "test", pwdSalt, pwdHash)
        try {
            naiveOrm.insert(user)
        } catch (_: Exception) {

        }
    }

    override fun getUserByEmail(email: String): User? {
        return naiveOrm.selectFrom<User>().where("email = ?", email).first()
    }

    @Transactional
    override fun register(email: String, name: String, password: String): User? {
        val pwdSalt = SecureRandomUtils.genRandomString(32)
        val pwdHash = HashUtils.hmacSha256(password, pwdSalt)
        val user = User(-1, email, name, pwdSalt, pwdHash)
        return try {
            naiveOrm.insert(user)
            user
        } catch (e: Exception) {
            logger.warn(e.message, e)
            null
        }
    }

    @Transactional
    override fun changePassword(user: User, newPassword: String): Boolean {
        user.pwdSalt = SecureRandomUtils.genRandomString(32)
        user.pwdHash = HashUtils.hmacSha256(newPassword, user.pwdSalt)
        return try {
            naiveOrm.update(user)
            true
        } catch (e: Exception) {
            logger.warn(e.message, e)
            false
        }
    }

    override fun validate(email: String, password: String): User? {
        val user = getUserByEmail(email) ?: return null
        val pwdHash = HashUtils.hmacSha256(password, user.pwdSalt)
        return if (pwdHash == user.pwdHash) user else null
    }
}