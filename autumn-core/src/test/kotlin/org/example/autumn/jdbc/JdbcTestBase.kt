package org.example.autumn.jdbc

import org.example.autumn.resolver.Config
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Files
import kotlin.io.path.Path

open class JdbcTestBase {
    companion object {
        const val CREATE_USER =
            "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(255) NOT NULL, age INTEGER)"
        const val CREATE_ADDRESS =
            "CREATE TABLE addresses (id INTEGER PRIMARY KEY AUTOINCREMENT, userId INTEGER NOT NULL, address VARCHAR(255) NOT NULL, zip INTEGER)"

        const val INSERT_USER = "INSERT INTO users (name, age) VALUES (?, ?)"
        const val INSERT_ADDRESS = "INSERT INTO addresses (userId, address, zip) VALUES (?, ?, ?)"

        const val UPDATE_USER = "UPDATE users SET name = ?, age = ? WHERE id = ?"
        const val UPDATE_ADDRESS = "UPDATE addresses SET address = ?, zip = ? WHERE id = ?"

        const val DELETE_USER = "DELETE FROM users WHERE id = ?"
        const val DELETE_ADDRESS_BY_USERID = "DELETE FROM addresses WHERE userId = ?"

        const val SELECT_USER = "SELECT * FROM users WHERE id = ?"
        const val SELECT_USER_NAME = "SELECT name FROM users WHERE id = ?"
        const val SELECT_USER_AGE = "SELECT age FROM users WHERE id = ?"
        const val SELECT_ADDRESS_BY_USERID = "SELECT * FROM addresses WHERE userId = ?"
    }

    val propertyResolver = Config(
        mapOf(
            "autumn.datasource.url" to "jdbc:sqlite:test_jdbc.db",
            "autumn.datasource.username" to "sa",
            "autumn.datasource.password" to "",
            "autumn.datasource.driver-class-name" to "org.sqlite.JDBC"
        ).toProperties()
    )

    @BeforeEach
    fun beforeEach() {
        Files.deleteIfExists(Path("test_jdbc.db"))
    }
}