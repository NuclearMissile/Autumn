package org.example.autumn.hello.service

import org.example.autumn.annotation.Around
import org.example.autumn.annotation.Autowired
import org.example.autumn.annotation.Component
import org.example.autumn.annotation.PostConstruct
import org.example.autumn.annotation.Transactional
import org.example.autumn.db.orm.NaiveOrm
import org.example.autumn.hello.model.User
import org.example.autumn.utils.HashUtils
import org.example.autumn.utils.SecureRandomUtils
import org.slf4j.LoggerFactory

@Around("beforeLogInvocation", "afterLogInvocation")
@Component
@Transactional
class UserService @Autowired constructor(private val naiveOrm: NaiveOrm) {
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

    fun getUserByEmail(email: String): User? {
        return naiveOrm.selectFrom<User>().where("email = ?", email).first()
    }

    @Transactional
    fun register(email: String, name: String, password: String): User? {
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
    fun changePassword(user: User, newPassword: String): Boolean {
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

    fun validate(email: String, password: String): User? {
        val user = getUserByEmail(email) ?: return null
        val pwdHash = HashUtils.hmacSha256(password, user.pwdSalt)
        return if (pwdHash == user.pwdHash) user else null
    }
}