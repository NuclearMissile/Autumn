package org.example.autumn.hello

import org.example.autumn.annotation.*
import org.example.autumn.db.orm.NaiveOrm
import org.example.autumn.utils.HashUtils
import org.example.autumn.utils.SecureRandomUtils
import org.slf4j.LoggerFactory

//@Entity
//@Table(name = "users")
//data class User(
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    @Column(nullable = false, updatable = false)
//    val id: Long,
//    @Column(nullable = false, unique = true)
//    val email: String,
//    @Column(nullable = false)
//    val name: String,
//    @Column(name = "pwd_salt", nullable = false)
//    val pwdSalt: String,
//    @Column(name = "pwd_hash", nullable = false)
//    val pwdHash: String,
//) {
//    override fun toString(): String {
//        return "User(id=$id, email='$email', name='$name')"
//    }
//}

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