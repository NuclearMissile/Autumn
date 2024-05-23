package org.example.autumn.db

import org.example.autumn.resolver.Config
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Files
import kotlin.io.path.Path

data class Address(
    var id: Int = -1,
    var userId: Int = 0,
    var address: String? = null,
    var zipcode: Int = 0,
)

data class User(
    var id: Int = -1, var name: String? = null, var age: Int? = null,
    var booleanTest: Boolean = false, var shortTest: Short = 0, var isTest: Boolean = true,
)

open class JdbcTestBase {
    companion object {
        const val CREATE_USER_TABLE =
            "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(255) NOT NULL, age INTEGER, " +
                "booleanTest BOOLEAN DEFAULT false, shortTest INTEGER NOT NULL DEFAULT 0, isTest BOOLEAN DEFAULT true)"
        const val CREATE_ADDRESS_TABLE =
            "CREATE TABLE addresses (id INTEGER PRIMARY KEY AUTOINCREMENT, userId INTEGER NOT NULL, address VARCHAR(255) NOT NULL, zipcode INTEGER)"

        const val INSERT_USER = "INSERT INTO users (name, age) VALUES (?, ?)"
        const val INSERT_ADDRESS = "INSERT INTO addresses (userId, address, zipcode) VALUES (?, ?, ?)"

        const val UPDATE_USER = "UPDATE users SET name = ?, age = ? WHERE id = ?"
        const val UPDATE_ADDRESS = "UPDATE addresses SET address = ?, zipcode = ? WHERE id = ?"

        const val DELETE_USER = "DELETE FROM users WHERE id = ?"
        const val DELETE_ADDRESS_BY_USERID = "DELETE FROM addresses WHERE userId = ?"

        const val SELECT_USER = "SELECT * FROM users WHERE id = ?"
        const val SELECT_ALL_USER = "SELECT * FROM users"
        const val SELECT_USER_NAME = "SELECT name FROM users WHERE id = ?"
        const val SELECT_USER_AGE = "SELECT age FROM users WHERE id = ?"
        const val SELECT_ADDRESS_BY_USERID = "SELECT * FROM addresses WHERE userId = ?"
    }

    val config = Config(
        mapOf(
            "autumn.datasource.url" to "jdbc:sqlite:test_jdbc.db",
            "autumn.datasource.username" to "",
            "autumn.datasource.password" to "",
            "autumn.datasource.driver-class-name" to "org.sqlite.JDBC"
        ).toProperties()
    )

    @BeforeEach
    fun beforeEach() {
        Files.deleteIfExists(Path("test_jdbc.db"))
    }
}