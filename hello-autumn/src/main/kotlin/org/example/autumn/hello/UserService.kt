package org.example.autumn.hello

import jakarta.persistence.*
import org.example.autumn.annotation.Autowired
import org.example.autumn.annotation.Component
import org.example.autumn.annotation.PostConstruct
import org.example.autumn.jdbc.orm.DbTemplate
import org.example.autumn.utils.HashUtil
import org.example.autumn.utils.SecureRandomUtil

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
class UserService(@Autowired val dbTemplate: DbTemplate) {
    companion object {
        const val CREATE_USERS = "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "email TEXT NOT NULL UNIQUE, name TEXT NOT NULL, pwd_salt TEXT NOT NULL, pwd_hash TEXT NOT NULL);"
    }

    @PostConstruct
    fun init() {
        dbTemplate.jdbcTemplate.update(CREATE_USERS)
        register("test@test.com", "test", "test")
    }

    fun getUserByEmail(email: String): User? {
        return dbTemplate.selectFrom<User>().where("email = ?", email).first()
    }

    fun register(email: String, name: String, password: String): User? {
        val pwdSalt = SecureRandomUtil.genRandomString(32)
        val pwdHash = HashUtil.hmacSha256(password, pwdSalt)
        val user = User(-1, email, name, pwdSalt, pwdHash)
        return try {
            dbTemplate.insert(user)
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